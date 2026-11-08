package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import com.scanpro.cam.pdf.ads.manager.SpBannerAdManager
import com.scanpro.cam.pdf.ads.manager.SpFullScreenAdManager
import com.scanpro.cam.pdf.ads.manager.SpNativeAdManager
import com.scanpro.cam.pdf.analytics.SpFacebookInit
import com.scanpro.cam.pdf.analytics.SpSceneCode
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.config.SpConfigBootstrapper
import com.scanpro.cam.pdf.config.SpConfigLoadResult
import com.scanpro.cam.pdf.config.SpConfigRemoteSource
import com.scanpro.cam.pdf.config.SpConfigRepository
import com.scanpro.cam.pdf.config.SpConfigStore
import com.scanpro.cam.pdf.notice.SpAcPopConfig
import com.scanpro.cam.pdf.notice.SpMediaConfig
import com.scanpro.cam.pdf.notice.SpNewConfig
import com.scanpro.cam.pdf.notice.SpPopRefresh
import com.scanpro.cam.pdf.notice.SpWinPopConfig
import com.scanpro.cam.pdf.util.AppLog
import com.zppko.pdfplus.nor.ads.ImpressionUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * 广告总入口。
 * 配置链（api/dispatch → SpConfigStore）与页面时序（开屏 launch_min/wait/catchup、热启动、
 * UMP、预加载触发点）保持 B 原样；广告「加载-缓存-展示」内核切换为 PDF260713_p01 的
 * ImpressionUtils 池（OpenAdsLoad/IntAdsLoad/NatAdsLoad + BaseAdsLoad TTL/降级链）。
 */
class SpAdsHub private constructor(
    private val app: Application,
    private val scope: CoroutineScope,
) {

    private val configStore = SpConfigStore()
    private val remoteSource = SpConfigRemoteSource(app)
    private val configRepository = SpConfigRepository(app, remoteSource)
    private val bootstrapper = SpConfigBootstrapper(
        app,
        configRepository,
        remoteSource,
        ::onConfigResult,
    )
    private val blocking = SpBlockingLogic.get(app)

    @Volatile
    private var adConfig = SpAdConfig()
    @Volatile
    private var natConfig = SpNatConfig()
    @Volatile
    private var newConfig = SpNewConfig()
    @Volatile
    private var acPopConfig = SpAcPopConfig()
    @Volatile
    private var popRefresh = SpPopRefresh()
    @Volatile
    private var mediaConfig = SpMediaConfig()
    @Volatile
    private var winPopConfig = SpWinPopConfig()
    @Volatile
    private var adShowConfig = SpAdShowConfig()

    private val booted = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var remotePayloadReady = false

    /** 启动页发起、但 SDK/consent 未就绪被推迟的开屏预加载；条件满足后补一次。 */
    @Volatile
    private var pendingLaunchPreload = false

    /** 开屏 UMP 结束（或超时）后完成；预加载只做短等待，不再按 2.5 秒阻塞。 */
    private val umpGate = CompletableDeferred<Unit>()

    /** 无页面宿主的预加载接线层（开屏位）。 */
    private val preloadFacade by lazy { SpFullScreenAdManager(app, null) }

    fun initialize() {
        if (!booted.compareAndSet(false, true)) return
        SpAdSession.attachLifecycle(app)
        scope.launch(Dispatchers.Main.immediate) {
            SpWebWarmup.awaitWarmupReady(app)
            SpAdMobInit.get(app).init(scope) { onAdSdkReady() }
            bootstrapper.start()
        }
    }

    suspend fun requestStartupConsent(activity: Activity) {
        if (umpGate.isCompleted) return
        try {
            SpUmpGate(app).requestUmpConsent(activity)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(TAG, "requestStartupConsent failed", error)
        } finally {
            if (!umpGate.isCompleted) umpGate.complete(Unit)
        }
    }

    val configApplied: StateFlow<Boolean> get() = bootstrapper.configApplied

    /** 每次配置下发（含不可用）递增；启动页用它在配置晚到、或广告位后补时再预加载。 */
    private val _configGeneration = MutableStateFlow(0)
    val configGeneration: StateFlow<Int> get() = _configGeneration

    // region 配置

    fun config(): SpAdConfig = adConfig
    fun showConfig(): SpAdShowConfig = adShowConfig
    fun nat(): SpNatConfig = natConfig
    fun newConfigOf(): SpNewConfig = newConfig
    fun acPopConfigOf(): SpAcPopConfig = acPopConfig
    fun popRefreshOf(): SpPopRefresh = popRefresh
    fun mediaConfigOf(): SpMediaConfig = mediaConfig
    fun winPopConfigOf(): SpWinPopConfig = winPopConfig

    /** 远端配置是否已到过一次（Apollo `remoteConfig.newConfig != null`）。 */
    fun hasRemotePayload(): Boolean = remotePayloadReady

    /** 冷启动 / 回前台拉一次配置；未到 3 小时直接读缓存，零网络开销。 */
    fun refreshConfig() {
        bootstrapper.refresh()
    }

    private fun onConfigResult(result: SpConfigLoadResult) {
        when (result) {
            is SpConfigLoadResult.Available -> applyPayload(result.payload)
            is SpConfigLoadResult.Unavailable -> applyUnavailable(result.reason)
        }
    }

    private fun applyPayload(payload: JSONObject) {
        remotePayloadReady = true
        adConfig = configStore.adConfig(payload)
        adShowConfig = configStore.adShowConfig(payload)
        natConfig = configStore.natConfig(payload)
        newConfig = configStore.newConfig(payload)
        acPopConfig = configStore.acPopConfig(payload)
        popRefresh = configStore.popRefresh(payload)
        mediaConfig = configStore.mediaConfig(payload)
        winPopConfig = configStore.winPopConfig(payload)
        applyBlocking(payload)
        applyDispatchMeta(payload)
        // Cache and remote share this path. Wake A only after B's config and gates are applied.
        runCatching {
            com.zppko.pdfplus.nor.remote.RemoteConfigHelper.onConfigReady(payload)
        }.onFailure { Log.w(TAG, "background config update failed", it) }
        AppLog.network.i("配置已应用 位置=${adConfig.byPosition.keys}")
        // 池按 PDF26 语义在每次 load() 重读配置，已加载广告保留到各自 TTL 到期。
        _configGeneration.value += 1
    }

    private fun applyUnavailable(reason: String) {
        adConfig = SpAdConfig()
        AppLog.network.w("配置不可用，停止新广告请求；已加载广告保留到过期 原因=$reason")
        _configGeneration.value += 1
    }

    private fun applyDispatchMeta(payload: JSONObject) {
        val meta = configStore.dispatchMeta(payload)
        runCatching { SpFacebookInit.start(app, meta.facebookAppId, meta.clientToken) }
            .onFailure { Log.w(TAG, "facebook init skipped", it) }
        reportAttributionOnce(meta.isAcquiredUser)
    }

    /**
     * 买量 / 自然量一次性标记，对应规范 E23 `activated` / E25 `biorganic`：两者互斥、
     * 每台设备只报一次、且**只出 bi**（不经 Firebase / Adjust / Facebook）。
     */
    private fun reportAttributionOnce(acquired: Boolean) {
        val prefs = app.getSharedPreferences(PREFS_DISPATCH, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ACTIVATED, false) || prefs.getBoolean(KEY_BIORGANIC, false)) return
        if (acquired) {
            SpTrackEvents.activated()
            prefs.edit().putBoolean(KEY_ACTIVATED, true).apply()
        } else {
            SpTrackEvents.biorganic()
            prefs.edit().putBoolean(KEY_BIORGANIC, true).apply()
        }
    }

    private fun applyBlocking(payload: JSONObject) {
        val patch = configStore.blockingConfig(payload)
        patch.acAdsLimit?.let { blocking.acAdsLimit = it }
        patch.globalBlockSwitch?.let { blocking.globalBlockSwitch = it }
        patch.googlePlayBlock?.let { blocking.googlePlayBlock = it }
        patch.vpnConfigSwitch?.let { blocking.vpnConfigSwitch = it }
        patch.virtualBlockSwitch?.let { blocking.virtualBlockSwitch = it }
    }

    /**
     * 用 `ac_launch` 预加载开屏广告。
     * 触发点保持 B 原样（启动页、退后台、SDK/consent 回调）；加载走池。
     */
    fun preloadLaunchAd(reason: String = "auto") {
        scope.launch(Dispatchers.Main) {
            if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) {
                pendingLaunchPreload = true
                Log.i(TAG, "preloadLaunchAd deferred: sdk not ready reason=$reason")
                return@launch
            }
            awaitConsentBriefly()
            if (!isConsentReady()) {
                pendingLaunchPreload = true
                Log.i(TAG, "preloadLaunchAd deferred: consent not ready reason=$reason")
                return@launch
            }
            pendingLaunchPreload = false
            runCatching {
                preloadFacade.checkAndLoadAd(SpAdPosition.AC_LAUNCH, scene = SCENE_PRELOAD)
            }.onFailure { Log.w(TAG, "preloadLaunchAd failed reason=$reason", it) }
        }
    }

    private suspend fun awaitConsentBriefly() {
        if (umpGate.isCompleted) return
        withTimeoutOrNull(CONSENT_WAIT_MS) { umpGate.await() }
    }

    private fun isConsentReady(): Boolean =
        runCatching { SpUmpGate(app).adsEligible() }.getOrDefault(true)

    /**
     * SDK 初始化完成后由 [SpAdMobInit] 回调。
     * 只补启动页已经发起、但因 SDK 未就绪被推迟的那一次；后台进程没有启动页，
     * `pendingLaunchPreload` 保持 false，这里不会发出请求。
     */
    internal fun onAdSdkReady() {
        Log.i(TAG, "ad sdk ready, pendingPreload=$pendingLaunchPreload")
        if (pendingLaunchPreload && SpAdSession.isForeground) {
            preloadLaunchAd("sdk-ready")
        }
    }

    /** 是否有已经就绪、可以立刻展示的开屏广告。 */
    fun hasOpenAd(): Boolean = ImpressionUtils.loadSuccess(SpAdPosition.AC_LAUNCH)

    /**
     * 等开屏广告就绪。池语义：超时内 loadSuccess 为 true 即视为就绪。
     */
    suspend fun awaitOpenAd(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(0L)
        while (true) {
            if (ImpressionUtils.loadSuccess(SpAdPosition.AC_LAUNCH)) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            delay(OPEN_AD_POLL_MS)
        }
    }

    /**
     * 展示开屏广告：就绪则走 500ms 遮罩 + 池展示；关闭/失败回调 [onDone]。
     */
    fun presentLaunchAd(
        activity: Activity,
        scene: String = SpSceneCode.AC_LAUNCH_OPEN,
        onDone: () -> Unit,
    ) {
        val once = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish = { if (once.compareAndSet(false, true)) onDone() else Unit }
        SpFullScreenAdManager(activity, activity).showOpenAd(
            position = SpAdPosition.AC_LAUNCH,
            scene = scene,
            onDone = finish,
        )
    }
    // endregion

    // region 按 Activity 取 manager（Apollo `BaseActivity.getFullScreenAd()`）

    fun fullScreen(activity: Activity): SpFullScreenAdManager = SpFullScreenAdManager(activity, activity)

    fun native(activity: Activity): SpNativeAdManager = SpNativeAdManager(activity)

    fun native(activity: Activity, container: ViewGroup): SpNativeAdManager =
        SpNativeAdManager(activity, container)

    fun banner(activity: Activity, container: ViewGroup): SpBannerAdManager =
        SpBannerAdManager(activity, container)
    // endregion

    /** 逻辑同 `ApoApplication.isForeground()`。 */
    fun isForeground(): Boolean = SpAdSession.isForeground

    companion object {
        private const val TAG = "SpAdsHub"
        private const val PREFS_DISPATCH = "sp_dispatch_flags"
        private const val KEY_ACTIVATED = "activated_reported"
        private const val KEY_BIORGANIC = "biorganic_reported"

        /** E05 `ad_request` 用 `scene` 标预加载（规范 §4E05）。 */
        const val SCENE_PRELOAD = "preload"

        /** 预加载最多等 consent 这么久，超时也继续（非 EEA 场景 UMP 基本秒回）。 */
        private const val CONSENT_WAIT_MS = 800L

        /** 等待开屏广告就绪时的轮询间隔。 */
        private const val OPEN_AD_POLL_MS = 50L

        @Volatile
        private var instance: SpAdsHub? = null

        fun get(app: Application, scope: CoroutineScope): SpAdsHub =
            instance ?: synchronized(this) {
                instance ?: SpAdsHub(app, scope).also { instance = it }
            }

        fun getOrNull(): SpAdsHub? = instance
    }
}