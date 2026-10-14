package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val booted = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var remotePayloadReady = false

    /** 开屏 UMP 结束（或超时）后完成。预加载最多等 [SpUmpGate.DEFAULT_TIMEOUT_MS]。 */
    private val umpGate = CompletableDeferred<Unit>()

    fun initialize() {
        if (!booted.compareAndSet(false, true)) return
        SpAdSession.attachLifecycle(app)
        scope.launch(Dispatchers.Main.immediate) {
            SpWebWarmup.awaitWarmupReady(app)
            SpAdMobInit.get(app).init(scope)
            bootstrapper.start()
        }
    }

    /** 冷启动页调用。2.5 秒兜底在 [SpUmpGate] 内部。 */
    suspend fun requestStartupConsent(activity: Activity) {
        if (umpGate.isCompleted) return
        runCatching { SpUmpGate(app).requestUmpConsent(activity) }
        if (!umpGate.isCompleted) umpGate.complete(Unit)
    }

    val configApplied: StateFlow<Boolean> get() = bootstrapper.configApplied

    // region 配置

    fun config(): SpAdConfig = adConfig
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
        adConfig = next
        natConfig = configStore.natConfig(payload)
        newConfig = configStore.newConfig(payload)
        acPopConfig = configStore.acPopConfig(payload)
        popRefresh = configStore.popRefresh(payload)
        mediaConfig = configStore.mediaConfig(payload)
        winPopConfig = configStore.winPopConfig(payload)
        applyBlocking(payload)
        applyDispatchMeta(payload)
        Log.i(
            TAG,
            "config applied positions=${next.byPosition.keys} nat=${natConfig.switchOpen}",
        )
        SpAdCache.clearAll()
        preloadLaunchAd()
    }

    private fun applyUnavailable(reason: String) {
        adConfig = SpAdConfig()
        SpAdCache.clearAll()
        Log.w(TAG, "config unavailable ads off: $reason")
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

    /** 用 `ac_launch` 预算开屏广告。Apollo 在 Application 初始化后就会预热。 */
    fun preloadLaunchAd() {
        scope.launch(Dispatchers.Main) {
            withTimeoutOrNull(SpUmpGate.DEFAULT_TIMEOUT_MS) { umpGate.await() }
            runCatching { preloadManager.checkAndLoadAd(SpAdPosition.AC_LAUNCH) }
                .onFailure { Log.w(TAG, "preloadLaunchAd failed", it) }
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

        @Volatile
        private var instance: SpAdsHub? = null

        fun get(app: Application, scope: CoroutineScope): SpAdsHub =
            instance ?: synchronized(this) {
                instance ?: SpAdsHub(app, scope).also { instance = it }
            }

        fun getOrNull(): SpAdsHub? = instance
    }
}
