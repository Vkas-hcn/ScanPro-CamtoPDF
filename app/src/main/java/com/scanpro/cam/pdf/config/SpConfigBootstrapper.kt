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
            dispatch(SpConfigLoadResult.Unavailable("配置接口未配置"))
            return
        }
        var refreshRemoteAfterCache = false
        configLoadMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = repository.loadCache()
            val lastRequestedAt = lastConfigRequestAt(repository.lastSuccessfulFetchAtMillis())
            val needRemote = shouldRequestConfig(lastRequestedAt, now)
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
                    "跳过远端刷新 原因=距上次请求不足刷新间隔 触发=$trigger " +
                        "距上次=${sinceText(now, lastRequestedAt)} " +
                        "还需等待=${durationText(CONFIG_REFRESH_INTERVAL_MILLIS - (now - lastRequestedAt))}",
                )
                dispatch(SpConfigLoadResult.Unavailable("距上次配置请求不足刷新间隔"))
                return@withLock
            }
            fetchRemote(wire, cached = null, trigger = trigger)
        }
        if (!refreshRemoteAfterCache) return
        configLoadMutex.withLock {
            val lastRequestedAt = lastConfigRequestAt(repository.lastSuccessfulFetchAtMillis())
            if (!shouldRequestConfig(lastRequestedAt, System.currentTimeMillis())) {
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
            if (cached == null) {
                dispatch(SpConfigLoadResult.Unavailable("BI Distinct ID 不可用"))
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
                    dispatch(remoteResult)
                }
            }
        }
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
            val lastRequestedAt = lastConfigRequestAt(repository.lastSuccessfulFetchAtMillis())
            val waitMillis = if (lastRequestedAt <= 0L) {
                CONFIG_REFRESH_INTERVAL_MILLIS
            } else {
                (CONFIG_REFRESH_INTERVAL_MILLIS - (System.currentTimeMillis() - lastRequestedAt))
                    .coerceAtLeast(0L)
            }
            if (waitMillis > 0L) delay(waitMillis)
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

    private fun lastConfigRequestAt(lastFetchedAtMillis: Long): Long =
        maxOf(lastFetchedAtMillis, repository.lastAttemptAtMillis())

    companion object {
        const val CONFIG_REFRESH_INTERVAL_MILLIS = 3 * 60 * 60 * 1_000L
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
