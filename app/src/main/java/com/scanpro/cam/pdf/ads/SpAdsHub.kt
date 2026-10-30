package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
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
 * 广告总入口。合并 ApolloPDF 里散在 `ApoApplication` / `RemoteConfig` / `BaseActivity`
 * 的接线：初始化 SDK、装配配置、按 Activity 造 manager。
 *
 * 出处：
 * - `app/ApoApplication.java`（`AdMobInit.init` / `TriggerManager.start*` / 生命周期）
 * - `remote/RemoteConfig.java`（`getAcAdConfig` / `getNatConfig` / `getNewConfig` …）
 * - `ui/base/BaseActivity.java`（`FullScreenAdManager(this)`）
 *
 * **远端配置不走 Apollo 的 Firebase Remote Config**，改走中台 `api/dispatch`
 * （`config/SpConfigBootstrapper`），按 Black 的要求。
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

    fun initialize() {
        if (!booted.compareAndSet(false, true)) return
        SpAdSession.attachLifecycle(app)
        scope.launch(Dispatchers.Main.immediate) {
            SpWebWarmup.awaitWarmupReady(app)
            SpAdMobInit.get(app).init(scope) { onAdSdkReady() }
            bootstrapper.start()
        }
    }

    /**
     * 冷启动页调用。2.5 秒兜底在 [SpUmpGate] 内部。
     *
     * 只负责 consent；预加载由启动页在返回后自己触发，避免这里被其它调用方复用时
     * 在没有页面的情况下发广告请求。
     */
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
        // Apollo 每个触发器开头都有 `if (remoteConfig.newConfig == null) return`：
        // 远端配置没回来之前不发通知。这里用同一语义的开关表达。
        remotePayloadReady = true
        val next = configStore.adConfig(payload)
        // 配置变化不再清空缓存；`unitsChanged` 只用于日志排查，过期由 clearExpired() 按入库 TTL 判断。
        val unitsChanged = adUnitSignature(next) != adUnitSignature(adConfig)
        adConfig = next
        adShowConfig = configStore.adShowConfig(payload)
        natConfig = configStore.natConfig(payload)
        newConfig = configStore.newConfig(payload)
        acPopConfig = configStore.acPopConfig(payload)
        popRefresh = configStore.popRefresh(payload)
        mediaConfig = configStore.mediaConfig(payload)
        winPopConfig = configStore.winPopConfig(payload)
        applyBlocking(payload)
        applyDispatchMeta(payload)
        AppLog.network.i(
            "配置已应用 位置=${next.byPosition.keys} 原生开关=${natConfig.switchOpen} " +
                "单元变更=$unitsChanged",
        )
        // 配置变化只清理已过期广告，未过期的保留；开屏预加载也不再由配置触发，
        // 避免后台进程（保活/闹钟/开机/FCM）拉起时没有页面却发广告请求。
        SpAdCache.clearExpired()
        _configGeneration.value += 1
    }

    private fun adUnitSignature(config: SpAdConfig): Map<String, List<String>> =
        config.byPosition.mapValues { (_, list) -> list.map { it.adUnitId } }

    private fun applyUnavailable(reason: String) {
        adConfig = SpAdConfig()
        // 配置不可用只停止新的加载；已填充广告保留到各自 TTL 到期。
        SpAdCache.clearExpired()
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
     * E25 的「三方归因状态已是已归因则不报」需等归因回传接入后补充（当前配置仅含 isAcquiredUser）。
     */
    private fun reportAttributionOnce(acquired: Boolean) {
        val prefs = app.getSharedPreferences(PREFS_DISPATCH, Context.MODE_PRIVATE)
        // E23 activated / E25 biorganic 互斥：任一已报过，另一个不再报（配置自然量↔买量来回切换也不重复）
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
     *
     * 只允许启动页、退后台（已有用户会话）及其 SDK/consent 回调触发；
     * 配置下发、Application 启动阶段不自动调用。这样进程被保活服务、闹钟、
     * 开机广播、FCM 拉起时，没有页面就不会发开屏请求。
     */
    fun preloadLaunchAd(reason: String = "auto") {
        scope.launch(Dispatchers.Main) {
            if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) {
                // SDK 还没 ready：记一笔，等 MobileAds 初始化回调里补。
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
                preloadManager.checkAndLoadAd(SpAdPosition.AC_LAUNCH, scene = SCENE_PRELOAD)
            }.onFailure { Log.w(TAG, "preloadLaunchAd failed reason=$reason", it) }
        }
    }

    /** 等 UMP 结束，但最多等 [CONSENT_WAIT_MS]，不把开屏时序再拖回去。 */
    private suspend fun awaitConsentBriefly() {
        if (umpGate.isCompleted) return
        withTimeoutOrNull(CONSENT_WAIT_MS) { umpGate.await() }
    }

    private fun isConsentReady(): Boolean =
        runCatching { SpUmpGate(app).adsEligible() }.getOrDefault(true)

    /**
     * SDK 初始化完成后由 [SpAdMobInit] 回调。
     *
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
    fun hasOpenAd(): Boolean = SpAdCache.peekOpenAd(adConfig, blocking) != null

    /**
     * 等开屏广告就绪。旧实现是固定 3 秒后只看一眼缓存，没到货就永久失去展示机会。
     *
     * @return true 表示 [timeoutMs] 内已经有一条可用的开屏广告
     */
    suspend fun awaitOpenAd(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(0L)
        while (true) {
            if (SpAdCache.peekOpenAd(adConfig, blocking) != null) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            delay(OPEN_AD_POLL_MS)
        }
    }

    /** 只用于预加载，不承载展示。 */
    private val preloadManager by lazy { SpFullScreenAdManager(app, null) }

    /**
     * 展示开屏广告。Apollo `SplashActivity.showAdTimer`：拿 `ac_launch` 的缓存，
     * 有就展示（走 500ms 遮罩），没有直接回调 [onDone]；**关闭/失败都会回调一次** [onDone]。
     *
     * @param scene Apollo 的 `sceneCode`，说明这次开屏是被什么带进来的
     *   （普通/媒体式提醒、常驻通知、角标、卸载挽留）。默认 [SpSceneCode.AC_LAUNCH_OPEN]。
     */
    fun presentLaunchAd(
        activity: Activity,
        scene: String = SpSceneCode.AC_LAUNCH_OPEN,
        onDone: () -> Unit,
    ) {
        val cached = SpAdCache.getOpenAd(adConfig, blocking)
        if (cached == null) {
            onDone()
            return
        }
        val once = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish = { if (once.compareAndSet(false, true)) onDone() else Unit }
        SpFullScreenAdManager(activity, activity).showAd(
            ad = cached.ad,
            sessionId = cached.sessionId,
            scene = scene,
            reloadPosition = SpAdPosition.AC_LAUNCH,
            onResult = { showed, dismissed ->
                // 展示失败 → 立刻放行；展示成功 → 等关闭
                if (!showed || dismissed) finish()
            },
        )
    }
    // endregion

    // region 按 Activity 取 manager（Apollo `BaseActivity.getFullScreenAd()`）

    fun fullScreen(activity: Activity): SpFullScreenAdManager = SpFullScreenAdManager(activity, activity)

    fun native(activity: Activity): SpNativeAdManager = SpNativeAdManager(activity)

    /**
     * 带广告容器的原生管理。Apollo `NativeAdManager(activity, adLayoutView)`：
     * 只有拿到容器才会真的把广告渲染出来（[SpNativeAdManager.checkLoadAndShowAd] 里
     * `adLayoutView == null` 直接返回）。
     */
    fun native(activity: Activity, container: android.view.ViewGroup): SpNativeAdManager =
        SpNativeAdManager(activity, container)

    fun banner(activity: Activity, container: android.view.ViewGroup): SpBannerAdManager =
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
