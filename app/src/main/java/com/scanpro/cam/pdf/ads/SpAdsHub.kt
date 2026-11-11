package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
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
import com.scanpro.cam.pdf.config.SpConfigWire
import com.scanpro.cam.pdf.notice.SpAcPopConfig
import com.scanpro.cam.pdf.notice.SpMediaConfig
import com.scanpro.cam.pdf.notice.SpNewConfig
import com.scanpro.cam.pdf.notice.SpPopRefresh
import com.scanpro.cam.pdf.notice.SpWinPopConfig
import com.scanpro.cam.pdf.util.AppLog
import com.zppko.pdfplus.nor.ads.ImpressionUtils
import com.zppko.pdfplus.nor.ads.tool.UserParms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * 广告总入口。
 * 配置链（api/dispatch → SpConfigStore）与页面时序（开屏 launch_wait、热启动、
 * 预加载触发点）保持 B 原样；UMP 时序已改为 PDF260713_p01 的
 * 「通知权限后 → UMP（仅欧盟、无超时、失败继续）→ 加载」，广告「加载-缓存-展示」内核
 * 使用 PDF260713_p01 的 ImpressionUtils 池（OpenAdsLoad/IntAdsLoad/NatAdsLoad +
 * BaseAdsLoad TTL/降级链）。
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

    /** 本次 payload 是否显式下发了 `pop_refresh`；缺块时不启用重发，避免默认值误开。 */
    @Volatile
    private var popRefreshConfigured = false

    /** 启动页发起、但 SDK/UMP 未就绪被推迟的开屏预加载；条件满足后补一次。 */
    @Volatile
    private var pendingLaunchPreload = false

    /**
     * UMP 流程是否已结束。
     *
     * 对齐 PDF26：非欧盟无需 UMP，直接视为完成；欧盟在 `ImpressionUtils.init` 的表单/失败回调
     * 之后才置 true。广告加载/展示只等这个「流程完成」信号，不再检查 `canRequestAds()`，
     * 因此 UMP 失败后仍会继续加载（PDF26 的 fail-open 语义）。
     */
    @Volatile
    private var consentFlowDone: Boolean = !UserParms.isOm()

    /** 无页面宿主的预加载接线层（开屏位）。 */
    private val preloadFacade by lazy { SpFullScreenAdManager(app, null) }

    /** 开屏比价候选位：开屏 + 新加入的插屏位（照 SmartPdfScan_luow 的开屏比价）。 */
    private val launchBidPlaces = listOf(SpAdPosition.AC_LAUNCH, SpAdPosition.AC_LAUNCH_INT)

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
     * PDF26 `SplashActivity.initAds()` 的 `ImpressionUtils.init(this) { ... }` 等价物。
     *
     * 调用时机必须与 PDF26 一致：
     * 1. 通知权限回调之后；
     * 2. 任何开屏广告加载之前；
     * 3. 仅欧盟执行 UMP，非欧盟直接完成；
     * 4. 无超时，表单/请求回调返回后才继续（失败也继续）。
     */
    suspend fun requestStartupConsent(activity: Activity) {
        if (consentFlowDone) return
        suspendCancellableCoroutine<Unit> { continuation ->
            ImpressionUtils.init(activity) {
                consentFlowDone = true
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    /** 广告链路使用：UMP 流程（欧盟）是否已经结束；结束后不再做 `canRequestAds` 拦截。 */
    fun isConsentFlowDone(): Boolean = consentFlowDone

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

    /** 是否显式下发过 `pop_refresh`；未下发时不启用通知重发循环。 */
    fun hasPopRefreshConfig(): Boolean = popRefreshConfigured

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
        popRefreshConfigured = payload.has(SpConfigWire.KEY_POP_REFRESH)
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
        popRefreshConfigured = false
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
     * PDF26 的触发点是 UMP 之后；这里保留 SDK 就绪等待，但 UMP 未完成前不再发请求。
     */
    fun preloadLaunchAd(reason: String = "auto") {
        scope.launch(Dispatchers.Main) {
            if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) {
                pendingLaunchPreload = true
                AppLog.ads.w("加载拦截 位置=${SpAdPosition.AC_LAUNCH} 结果=延后 原因=广告SDK未就绪 触发=$reason")
                return@launch
            }
            if (!consentFlowDone) {
                pendingLaunchPreload = true
                AppLog.ads.w("加载拦截 位置=${SpAdPosition.AC_LAUNCH} 结果=延后 原因=UMP流程未完成 触发=$reason")
                return@launch
            }
            pendingLaunchPreload = false
            AppLog.ads.i("加载 位置=${SpAdPosition.AC_LAUNCH} 结果=开始 触发=$reason")
            runCatching {
                preloadFacade.checkAndLoadAd(SpAdPosition.AC_LAUNCH, scene = SCENE_PRELOAD)
            }.onFailure { AppLog.ads.w("加载 位置=${SpAdPosition.AC_LAUNCH} 结果=异常 触发=$reason", it) }
        }
    }

    /**
     * 开屏比价预加载：并发加载两个候选位（`ac_launch` + `ac_launch_int`）。
     * SDK/UMP 未就绪时延后，由 [onAdSdkReady] 补发。
     */
    fun preloadLaunchBidAds(reason: String = "auto") {
        scope.launch(Dispatchers.Main) {
            if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) {
                pendingLaunchPreload = true
                AppLog.ads.w("加载拦截 位置=${launchBidPlaces.joinToString()} 结果=延后 原因=广告SDK未就绪 触发=$reason")
                return@launch
            }
            if (!consentFlowDone) {
                pendingLaunchPreload = true
                AppLog.ads.w("加载拦截 位置=${launchBidPlaces.joinToString()} 结果=延后 原因=UMP流程未完成 触发=$reason")
                return@launch
            }
            pendingLaunchPreload = false
            AppLog.ads.i("加载 位置=${launchBidPlaces.joinToString()} 结果=开始 触发=$reason")
            runCatching {
                launchBidPlaces.forEach { preloadFacade.checkAndLoadAd(it, scene = SCENE_PRELOAD) }
            }.onFailure { AppLog.ads.w("加载 位置=${launchBidPlaces.joinToString()} 结果=异常 触发=$reason", it) }
        }
    }

    /**
     * SDK 初始化完成后由 [SpAdMobInit] 回调。
     * 只补启动页已经发起、但因 SDK 未就绪被推迟的那一次；后台进程没有启动页，
     * `pendingLaunchPreload` 保持 false，这里不会发出请求。
     */
    internal fun onAdSdkReady() {
        AppLog.ads.i("广告SDK就绪 待补拉预加载=$pendingLaunchPreload")
        if (pendingLaunchPreload && SpAdSession.isForeground) {
            preloadLaunchBidAds("sdk-ready")
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
            if (SystemClock.elapsedRealtime() >= deadline) {
                AppLog.ads.w("加载 位置=${SpAdPosition.AC_LAUNCH} 结果=等待就绪超时 超时=${timeoutMs}ms")
                return false
            }
            delay(OPEN_AD_POLL_MS)
        }
    }

    /**
     * 展示开屏（比价）广告：就绪立即走池展示（无遮罩）；关闭/失败回调 [onDone]。
     * `place` 为比价胜出的广告位（开屏 `ac_launch` 或插屏 `ac_launch_int`）。
     */
    fun presentLaunchAd(
        activity: Activity,
        place: String = SpAdPosition.AC_LAUNCH,
        scene: String = SpSceneCode.AC_LAUNCH_OPEN,
        onDone: () -> Unit,
    ) {
        val once = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish = { if (once.compareAndSet(false, true)) onDone() else Unit }
        SpFullScreenAdManager(activity, activity).showFullScreenAd(
            position = place,
            scene = scene,
            whenOnVisible = place == SpAdPosition.AC_LAUNCH,
        ) { showed, dismissed ->
            if (!showed || dismissed) finish()
        }
    }
    // endregion

    // region 按 Activity 取 manager（Apollo `BaseActivity.getFullScreenAd()`）

    fun fullScreen(activity: Activity): SpFullScreenAdManager = SpFullScreenAdManager(activity, activity)

    fun native(activity: Activity): SpNativeAdManager = SpNativeAdManager(activity)

    fun native(activity: Activity, container: ViewGroup): SpNativeAdManager =
        SpNativeAdManager(activity, container)
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

        /** 等待开屏广告就绪时的轮询间隔（PDF26 为 500ms）。 */
        private const val OPEN_AD_POLL_MS = 500L

        @Volatile
        private var instance: SpAdsHub? = null

        fun get(app: Application, scope: CoroutineScope): SpAdsHub =
            instance ?: synchronized(this) {
                instance ?: SpAdsHub(app, scope).also { instance = it }
            }

        fun getOrNull(): SpAdsHub? = instance
    }
}