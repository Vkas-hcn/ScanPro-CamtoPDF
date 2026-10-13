package com.scanpro.cam.pdf.config

import android.app.Application
import android.os.Build
import android.util.Log
import com.scanpro.cam.pdf.BuildConfig
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
 * 不接 Adjust / Facebook / 买量归因。
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
            runCatching { loadAndApplyConfig() }
                .onFailure { Log.w(TAG, "startup config load failed", it) }
        }
        scope.launch { reportCloakIfNeeded() }
        scope.launch { monitorRemoteConfig() }
    }

    fun refresh() {
        scope.launch {
            runCatching { loadAndApplyConfig() }
                .onFailure { Log.w(TAG, "config refresh failed", it) }
        }
    }

    suspend fun loadAndApplyConfig() {
        val wire = SpConfigWire.load(app)
        if (wire == null) {
            dispatch(SpConfigLoadResult.Unavailable("配置接口未配置"))
            return
        }
        var refreshRemoteAfterCache = false
        configLoadMutex.withLock {
            val startedAt = System.currentTimeMillis()
            val cached = repository.loadCache()
            val lastRequestedAt = lastConfigRequestAt(repository.lastSuccessfulFetchAtMillis())
            val needRemote = shouldRequestConfig(lastRequestedAt, startedAt)
            if (cached != null) {
                Log.i(TAG, "use cache needRemote=$needRemote")
                dispatch(cached)
                refreshRemoteAfterCache = needRemote
                return@withLock
            }
            if (!needRemote) {
                dispatch(SpConfigLoadResult.Unavailable("距上次配置请求不足刷新间隔"))
                return@withLock
            }
            fetchRemote(wire, cached = null)
        }
        if (!refreshRemoteAfterCache) return
        configLoadMutex.withLock {
            val lastRequestedAt = lastConfigRequestAt(repository.lastSuccessfulFetchAtMillis())
            if (!shouldRequestConfig(lastRequestedAt, System.currentTimeMillis())) return@withLock
            fetchRemote(wire, cached = repository.loadCache())
        }
    }

    private suspend fun fetchRemote(
        wire: SpDispatchWire,
        cached: SpConfigLoadResult.Available?,
    ) {
        val request = buildDispatchRequest()
        if (request.distinctId.isBlank()) {
            Log.w(TAG, "skip remote: distinctId blank")
            if (cached == null) {
                dispatch(SpConfigLoadResult.Unavailable("BI Distinct ID 不可用"))
            }
            return
        }
        val remoteResult = try {
            repository.load(request, wire)
        } finally {
            repository.markAttempt()
        }
        when (remoteResult) {
            is SpConfigLoadResult.Available -> {
                if (cached == null || remoteResult.origin == SpConfigOrigin.REMOTE) {
                    dispatch(remoteResult)
                } else {
                    Log.i(TAG, "remote unavailable, keep cache")
                }
            }
            is SpConfigLoadResult.Unavailable -> {
                if (cached != null) {
                    Log.w(TAG, "remote failed, keep cache: ${remoteResult.reason}")
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
                .onFailure { Log.w(TAG, "dispatch failed", it) }
            _configApplied.value = true
        }
    }

    private suspend fun monitorRemoteConfig() {
        configApplied.first { it }
        while (true) {
            val lastRequestedAt = lastConfigRequestAt(repository.lastSuccessfulFetchAtMillis())
            val waitMillis = if (lastRequestedAt <= 0L) {
                CONFIG_REFRESH_INTERVAL_MILLIS
            } else {
                (CONFIG_REFRESH_INTERVAL_MILLIS - (System.currentTimeMillis() - lastRequestedAt))
                    .coerceAtLeast(0L)
            }
            if (waitMillis > 0L) delay(waitMillis)
            runCatching { loadAndApplyConfig() }
                .onFailure { Log.w(TAG, "background refresh failed", it) }
        }
    }

    private suspend fun reportCloakIfNeeded() {
        try {
            cloakMutex.withLock { reportCloakLocked() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(TAG, "cloak start failed", error)
        }
    }

    private suspend fun reportCloakLocked() {
        if (repository.cloakReported()) return
        val wire = SpConfigWire.load(app) ?: return
        val request = buildDispatchRequest()
        repeat(CLOAK_ATTEMPTS) { index ->
            val attempt = withTimeoutOrNull(CLOAK_TIMEOUT_MS) {
                remote.cloak(request, wire)
            }
            if (attempt == null) {
                Log.w(TAG, "cloak timeout attempt=${index + 1}")
                return@repeat
            }
            if (attempt.success) {
                repository.saveCloakSuccess(attempt.resultJson)
                Log.i(TAG, "cloak reported")
                return
            }
        }
        Log.w(TAG, "cloak failed after $CLOAK_ATTEMPTS attempts")
    }

    private fun lastConfigRequestAt(lastFetchedAtMillis: Long): Long =
        maxOf(lastFetchedAtMillis, repository.lastAttemptAtMillis())

    companion object {
        private const val TAG = "SpConfig"
        const val CONFIG_REFRESH_INTERVAL_MILLIS = 3 * 60 * 60 * 1_000L
        private const val MAX_IDENTIFIER_LENGTH = 256
        private const val CLOAK_ATTEMPTS = 10
        private const val CLOAK_TIMEOUT_MS = 10_000L
    }
}

internal fun shouldRequestConfig(
    lastRequestedAtMillis: Long,
    nowMillis: Long,
): Boolean = lastRequestedAtMillis <= 0L ||
    nowMillis - lastRequestedAtMillis >= SpConfigBootstrapper.CONFIG_REFRESH_INTERVAL_MILLIS
