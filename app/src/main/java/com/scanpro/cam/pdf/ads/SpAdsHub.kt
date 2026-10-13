package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.app.Application
import android.util.Log
import com.scanpro.cam.pdf.ads.manager.SpBannerAdManager
import com.scanpro.cam.pdf.ads.manager.SpFullScreenAdManager
import com.scanpro.cam.pdf.ads.manager.SpNativeAdManager
import com.scanpro.cam.pdf.analytics.SpSceneCode
import com.scanpro.cam.pdf.config.SpConfigIdentity
import com.scanpro.cam.pdf.config.SpConfigStore
import com.scanpro.cam.pdf.notice.SpAcPopConfig
import com.scanpro.cam.pdf.notice.SpMediaConfig
import com.scanpro.cam.pdf.notice.SpNewConfig
import com.scanpro.cam.pdf.notice.SpPopRefresh
import com.scanpro.cam.pdf.notice.SpWinPopConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 广告总入口。合并 ApolloPDF 里散在 `ApoApplication` / `RemoteConfig` / `BaseActivity`
 * 的接线：初始化 SDK、装配配置、按 Activity 造 manager。
 *
 * 出处：
 * - `app/ApoApplication.java`（`AdMobInit.init` / `TriggerManager.start*` / 生命周期）
 * - `remote/RemoteConfig.java`（`getAcAdConfig` / `getNatConfig` / `getNewConfig` …）
 * - `ui/base/BaseActivity.java`（`FullScreenAdManager(this)`）
 *
 * **远端配置不走 Apollo 的实现**（Firebase Remote Config），改走我们自己的中台配置下发
 * （`config/SpConfigStore`），按 Black 的要求。
 */
class SpAdsHub private constructor(
    private val app: Application,
    private val scope: CoroutineScope,
) {

    private val configStore = SpConfigStore(app)
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

    fun initialize() {
        if (!booted.compareAndSet(false, true)) return
        SpAdSession.attachLifecycle(app)
        SpAdMobInit.get(app).init(scope)
        refreshConfig()
    }

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
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = configStore.refreshIfStale(SpConfigIdentity.build(app))
                applyPayload(payload)
            }.onFailure { Log.w(TAG, "config refresh failed safely", it) }
        }
    }

    private fun applyPayload(payload: org.json.JSONObject?) {
        if (payload == null) return
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
        Log.i(
            TAG,
            "config applied positions=${next.byPosition.keys} nat=${natConfig.switchOpen}/${natConfig.jumpPercent}",
        )
        // 位置集合变了，旧缓存的 TTL 口径可能不同，清一次
        SpAdCache.clearAll()
        preloadLaunchAd()
    }

    /** 用 `ac_launch` 预算开屏广告。Apollo 在 Application 初始化后就会预热。 */
    fun preloadLaunchAd() {
        scope.launch(Dispatchers.Main) {
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

        @Volatile
        private var instance: SpAdsHub? = null

        fun get(app: Application, scope: CoroutineScope): SpAdsHub =
            instance ?: synchronized(this) {
                instance ?: SpAdsHub(app, scope).also { instance = it }
            }

        fun getOrNull(): SpAdsHub? = instance
    }
}
