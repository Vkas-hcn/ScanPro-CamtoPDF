package com.scanpro.cam.pdf.config

import android.app.Application
import android.os.Build
import android.os.SystemClock
import com.scanpro.cam.pdf.BuildConfig
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 配置调度。对齐 EfileScan `AcquisitionConfigBootstrapper`：
 * 缓存优先、3 小时节流、attempt 时间、后台刷新、cloak。
 * Facebook 在配置到达后按 `facebook_app_id` / `client_token` 初始化。
 *
 * 日志走 [AppLog.network]，回答「这次为什么请求 / 为什么没请求」：
 * 触发来源、缓存是否命中、距上次请求多久、还要等多久、身份字段准备耗时。
 */
class SpConfigBootstrapper(
    private val app: Application,
    private val repository: SpConfigRepository,
    private val remote: SpConfigRemoteSource,
    private val onDispatch: (SpConfigLoadResult) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configLoadMutex = Mutex()
    private val cloakMutex = Mutex()
    private val _configApplied = MutableStateFlow(false)
    val configApplied: StateFlow<Boolean> = _configApplied.asStateFlow()

    private var consecutiveFailures = 0
    @Volatile
    private var retryNotBeforeElapsed = 0L

    fun start() {
        scope.launch {
            runCatching { loadAndApplyConfig(TRIGGER_STARTUP) }
                .onFailure { AppLog.network.w("配置启动加载失败 触发=$TRIGGER_STARTUP", it) }
        }
        scope.launch { reportCloakIfNeeded() }
        scope.launch { monitorRemoteConfig() }
    }

    fun refresh() {
        scope.launch {
            runCatching { loadAndApplyConfig(TRIGGER_REFRESH) }
                .onFailure { AppLog.network.w("配置刷新失败 触发=$TRIGGER_REFRESH", it) }
        }
    }

    suspend fun loadAndApplyConfig(trigger: String = TRIGGER_MANUAL) {
        AppLog.network.i("配置调度开始 触发=$trigger")
        val wire = SpConfigWire.load(app)
        if (wire == null) {
            AppLog.network.w("跳过配置接口 原因=接口未配置 触发=$trigger")
            deferAfterFailure()
            val cached = repository.loadCache()
            if (cached != null) {
                AppLog.network.i("接口未配置，使用本地接口缓存 触发=$trigger")
                dispatch(cached)
            } else {
                dispatchDefault("配置接口未配置")
            }
            return
        }
        var refreshRemoteAfterCache = false
        configLoadMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = repository.loadCache()
            val lastRequestedAt = repository.lastSuccessfulFetchAtMillis()
            val waitMillis = requestWaitMillis(cached != null, now)
            val needRemote = waitMillis == 0L
            if (cached != null) {
                AppLog.network.i(
                    "命中配置缓存 触发=$trigger 需要远端刷新=${needRemote.flagText()} " +
                        "距上次请求=${sinceText(now, lastRequestedAt)}",
                )
                dispatch(cached)
                refreshRemoteAfterCache = needRemote
                return@withLock
            }
            if (!needRemote) {
                AppLog.network.i(
                    "跳过远端刷新 原因=失败退避尚未结束 触发=$trigger " +
                        "距上次成功=${sinceText(now, lastRequestedAt)} " +
                        "还需等待=${durationText(waitMillis)}",
                )
                dispatchDefault("配置请求失败退避中")
                return@withLock
            }
            fetchRemote(wire, cached = null, trigger = trigger)
        }
        if (!refreshRemoteAfterCache) return
        configLoadMutex.withLock {
            if (requestWaitMillis(repository.loadCache() != null, System.currentTimeMillis()) > 0L) {
                AppLog.network.i("跳过后台刷新 原因=缓存生效后不足刷新间隔 触发=$trigger")
                return@withLock
            }
            fetchRemote(wire, cached = repository.loadCache(), trigger = trigger)
        }
    }

    private suspend fun fetchRemote(
        wire: SpDispatchWire,
        cached: SpConfigLoadResult.Available?,
        trigger: String,
    ) {
        try {
            fetchRemoteAttempt(wire, cached, trigger)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            deferAfterFailure()
            AppLog.network.w("配置请求异常，稍后重试 触发=$trigger", error)
            if (cached == null) dispatchDefault("配置请求异常")
        }
    }

    private suspend fun fetchRemoteAttempt(
        wire: SpDispatchWire,
        cached: SpConfigLoadResult.Available?,
        trigger: String,
    ) {
        val identityStartedAt = SystemClock.elapsedRealtime()
        val request = buildDispatchRequest()
        AppLog.network.i(
            "请求身份就绪 耗时=${SystemClock.elapsedRealtime() - identityStartedAt}ms " +
                "含DistinctID=${request.distinctId.isNotBlank().flagText()} " +
                "含GAID=${request.gaid.isNotBlank().flagText()} " +
                "含Referrer=${request.installReferrer.isNotBlank().flagText()} 触发=$trigger",
        )
        if (request.distinctId.isBlank()) {
            AppLog.network.w("跳过配置请求 原因=Distinct ID 为空 触发=$trigger")
            deferAfterFailure()
            if (cached == null) {
                dispatchDefault("BI Distinct ID 不可用")
            }
            return
        }
        val startedAt = SystemClock.elapsedRealtime()
        val remoteResult = try {
            repository.load(request, wire, trigger)
        } finally {
            repository.markAttempt()
        }
        val cost = SystemClock.elapsedRealtime() - startedAt
        if (remoteResult is SpConfigLoadResult.Available && remoteResult.origin == SpConfigOrigin.REMOTE) {
            consecutiveFailures = 0
            retryNotBeforeElapsed = 0L
        } else {
            // Cache fallback is still a failed remote attempt; never apply the 3-hour success throttle.
            deferAfterFailure()
        }
        when (remoteResult) {
            is SpConfigLoadResult.Available -> {
                if (cached == null || remoteResult.origin == SpConfigOrigin.REMOTE) {
                    dispatch(remoteResult)
                } else {
                    AppLog.network.w("远端配置不可用，继续使用缓存 耗时=${cost}ms 触发=$trigger")
                }
            }
            is SpConfigLoadResult.Unavailable -> {
                if (cached != null) {
                    AppLog.network.w(
                        "远端配置不可用，继续使用缓存 原因=${remoteResult.reason} " +
                            "耗时=${cost}ms 触发=$trigger",
                    )
                } else {
                    dispatchDefault(remoteResult.reason)
                }
            }
        }
    }

    /**
     * 接口无数据、且本地没有接口缓存时，才使用 assets/default_config.json。
     * 这里再查一次缓存，确保「有缓存就绝不用默认 JSON」。
     */
    private suspend fun dispatchDefault(reason: String) {
        val cached = repository.loadCache()
        if (cached != null) {
            AppLog.network.i("使用本地接口缓存，跳过默认配置 原因=$reason")
            dispatch(cached)
            return
        }
        val payload = SpDefaultConfig.load(app)
        if (payload == null) {
            dispatch(SpConfigLoadResult.Unavailable(reason))
            return
        }
        AppLog.network.i("使用本地默认配置 原因=$reason 字段数=${payload.length()}")
        dispatch(SpConfigLoadResult.Available(payload, SpConfigOrigin.DEFAULT))
    }

    private suspend fun buildDispatchRequest(): SpConfigRequest = coroutineScope {
        val referrerAsync = async { SpConfigIdentity.resolveInstallReferrer(app) }
        val distinctAsync = async {
            SpConfigIdentity.distinctId(app)
                .filterNot(Char::isISOControl).trim().take(MAX_IDENTIFIER_LENGTH)
        }
        val gaidAsync = async { SpConfigIdentity.gaid(app) }
        val referrer = referrerAsync.await()
        val distinctId = distinctAsync.await()
        val gaid = gaidAsync.await()
        SpConfigRequest(
            packageName = app.packageName,
            versionName = BuildConfig.VERSION_NAME.substringBefore('-'),
            distinctId = distinctId,
            gaid = gaid,
            deviceModel = Build.MODEL.orEmpty().trim().ifBlank { "unknown" },
            installReferrer = referrer,
            language = SpConfigIdentity.currentLanguage(app),
            osVersion = Build.VERSION.RELEASE.orEmpty().trim().ifBlank { "unknown" },
        )
    }

    private suspend fun dispatch(result: SpConfigLoadResult) {
        withContext(Dispatchers.Main.immediate) {
            runCatching { onDispatch(result) }
                .onFailure {
                    AppLog.network.w("配置下发失败 结果=${result.javaClass.simpleName}", it)
                }
            _configApplied.value = true
        }
    }

    private suspend fun monitorRemoteConfig() {
        configApplied.first { it }
        AppLog.network.i("配置定时刷新已启动 间隔=${durationText(CONFIG_REFRESH_INTERVAL_MILLIS)}")
        while (true) {
            val waitMillis = requestWaitMillis(repository.loadCache() != null, System.currentTimeMillis())
                .coerceAtLeast(1_000L)
            delay(waitMillis)
            runCatching { loadAndApplyConfig(TRIGGER_TIMER) }
                .onFailure { AppLog.network.w("配置定时刷新失败 触发=$TRIGGER_TIMER", it) }
        }
    }

    private suspend fun reportCloakIfNeeded() {
        try {
            cloakMutex.withLock { reportCloakLocked() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AppLog.network.w("cloak 启动失败", error)
        }
    }

    private suspend fun reportCloakLocked() {
        if (repository.cloakReported()) {
            AppLog.network.i("跳过 cloak 原因=本安装周期已上报成功")
            return
        }
        val wire = SpConfigWire.load(app) ?: return
        val request = buildDispatchRequest()
        repeat(CLOAK_ATTEMPTS) { index ->
            AppLog.network.i("开始 cloak 次数=${index + 1}/$CLOAK_ATTEMPTS 触发=$TRIGGER_CLOAK")
            val attempt = withTimeoutOrNull(CLOAK_TIMEOUT_MS) {
                remote.cloak(request, wire, TRIGGER_CLOAK)
            }
            if (attempt == null) {
                AppLog.network.w(
                    "cloak 超时 次数=${index + 1}/$CLOAK_ATTEMPTS " +
                        "超时=${durationText(CLOAK_TIMEOUT_MS)}",
                )
                return@repeat
            }
            if (attempt.success) {
                repository.saveCloakSuccess(attempt.resultJson)
                AppLog.network.i("cloak 上报成功 次数=${index + 1}/$CLOAK_ATTEMPTS")
                return
            }
        }
        AppLog.network.w("cloak 上报失败 次数=$CLOAK_ATTEMPTS")
    }

    private fun requestWaitMillis(hasCache: Boolean, nowMillis: Long): Long {
        val successAt = repository.lastSuccessfulFetchAtMillis()
        val successWait = if (hasCache && successAt > 0L) {
            (CONFIG_REFRESH_INTERVAL_MILLIS - (nowMillis - successAt))
                .coerceIn(0L, CONFIG_REFRESH_INTERVAL_MILLIS)
        } else 0L
        val inProcessRetryWait = (retryNotBeforeElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val attemptAt = repository.lastAttemptAtMillis()
        val restartRetryWait = if (attemptAt > successAt) {
            (CONFIG_RETRY_INITIAL_MS - (nowMillis - attemptAt)).coerceIn(0L, CONFIG_RETRY_INITIAL_MS)
        } else 0L
        return maxOf(successWait, inProcessRetryWait, restartRetryWait)
    }

    private fun deferAfterFailure() {
        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(5)
        val backoff = (CONFIG_RETRY_INITIAL_MS * (1L shl (consecutiveFailures - 1)))
            .coerceAtMost(CONFIG_RETRY_MAX_MS)
        retryNotBeforeElapsed = SystemClock.elapsedRealtime() + backoff
        AppLog.network.w("配置未取得远端结果，将重试 退避=${durationText(backoff)}")
    }

    companion object {
        const val CONFIG_REFRESH_INTERVAL_MILLIS = 3 * 60 * 60 * 1_000L
        private const val CONFIG_RETRY_INITIAL_MS = 30_000L
        private const val CONFIG_RETRY_MAX_MS = 5 * 60_000L
        private const val MAX_IDENTIFIER_LENGTH = 256
        private const val CLOAK_ATTEMPTS = 10
        private const val CLOAK_TIMEOUT_MS = 10_000L

        /** 日志里的「触发」，说明这次调度是谁发起的。 */
        private const val TRIGGER_STARTUP = "应用启动"
        private const val TRIGGER_REFRESH = "前台刷新"
        private const val TRIGGER_TIMER = "定时刷新"
        private const val TRIGGER_MANUAL = "手动调用"
        private const val TRIGGER_CLOAK = "cloak上报"
    }
}

internal fun shouldRequestConfig(
    lastRequestedAtMillis: Long,
    nowMillis: Long,
): Boolean = lastRequestedAtMillis <= 0L ||
    nowMillis - lastRequestedAtMillis >= SpConfigBootstrapper.CONFIG_REFRESH_INTERVAL_MILLIS

private fun Boolean.flagText(): String = if (this) "是" else "否"

private fun durationText(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0L)) / 1000
    return when {
        seconds < 60 -> "${seconds}秒"
        seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
        else -> "${seconds / 3600}小时${(seconds % 3600) / 60}分"
    }
}

private fun sinceText(nowMillis: Long, lastMillis: Long): String =
    if (lastMillis <= 0L) "从未请求" else "${durationText(nowMillis - lastMillis)}前"
