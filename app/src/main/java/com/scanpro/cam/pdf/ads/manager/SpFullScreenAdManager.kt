package com.scanpro.cam.pdf.ads.manager

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.scanpro.cam.pdf.ads.SpAdCache
import com.scanpro.cam.pdf.ads.SpAdConfig
import com.scanpro.cam.pdf.ads.SpAdInfo
import com.scanpro.cam.pdf.ads.SpAdMobInit
import com.scanpro.cam.pdf.ads.SpAdSession
import com.scanpro.cam.pdf.ads.SpAdIncomeReport
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.ads.SpCachedAd
import com.scanpro.cam.pdf.ads.SpFullScreenMask
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.util.AppLog

/**
 * 全屏广告（开屏 + 插屏）的加载、缓存与展示。**照抄 ApolloPDF
 * `ad/manager/FullScreenAdManager.java` 的编排**，把 Apollo 的白牌预加载 SDK
 * （`ads_mobile_sdk` 的 `dh.a` / `b41.a`）换成 GMA 原生 `AppOpenAd` / `InterstitialAd`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/manager/FullScreenAdManager.java`
 *
 * 保留的行为：
 * - 每个位置一条按优先级排的单元列表，加载失败**换下一个单元**（不是换位置）
 * - 同一个 sessionId 最多重试 [tryCountLimit] 次
 * - `loadingAdStateMap` 按 adUnitId 串行化，避免并发重复加载
 * - 展示前先走 [SpFullScreenMask]（500ms 假加载）
 * - 展示成功 → `updateAdShowCount()` 并立刻为下一次做预加载
 * - 展示失败 → 记 `Shark_ad_impression_error`，再补一次预加载
 *
 * 与 Apollo 的差异：Apollo 用 Activity 作用域构造；这里同样按 Activity 构造，由
 * `SpActivity` 持有。
 */
class SpFullScreenAdManager(
    context: Context,
    /** 只做预加载时传 null；要 `show()` 必须非空。 */
    private val activity: Activity?,
) {

    private val appContext: Context = context.applicationContext

    /** 等价 Apollo `AdConfigKt.getAdInfoList`：读 `RemoteConfig.INSTANCE.getAcAdConfig()`。 */
    private val config: SpAdConfig
        get() = SpAdsHub.getOrNull()?.config() ?: SpAdConfig()

    private val blocking: SpBlockingLogic
        get() = SpBlockingLogic.get(appContext)

    @Volatile
    private var isAdTryShowing = false

    /** sessionId → 已重试次数。 */
    private val sessionIdTryCountMap = LinkedHashMap<String, Int>()

    /** adUnitId → 是否正在加载。 */
    private val loadingAdStateMap = LinkedHashMap<String, Boolean>()

    /** position → 当前加载到列表第几个。 */
    private val loadIndexOfAdPositionMap = LinkedHashMap<String, Int>()

    private val tryCountLimit = 3

    // region 预加载

    /**
     * Apollo `checkAndLoadAd(position, scene, index)`。
     *
     * @param position `ac_*` 位置
     * @param scene 埋点场景名，默认同 [position]
     * @param index 从列表第几个单元开始（失败后 +1）
     */
    fun checkAndLoadAd(position: String, scene: String = position, index: Int = 0) {
        if (!SpAdMobInit.getOrNull()?.isMobileAdsInitialize().let { it == true }) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=广告SDK未初始化")
            return
        }
        if (blocking.isShowPartialAdsOnly(position)) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=只展示部分广告")
            return
        }
        if (blocking.isVPNBlock()) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=VPN拦截")
            return
        }
        if (!blocking.canShowAdByShowCountLimit()) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=达到当日展示上限")
            return
        }
        if (!blocking.canShowAllAd()) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=总闸拦截")
            return
        }

        val list = config.adInfoList(position) ?: run {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=没有广告位配置")
            return
        }
        if (index > list.size - 1) {
            AppLog.ads.w("广告加载失败 位置=$position 原因=广告单元已用完")
            return
        }
        val adInfo = list[index]
        loadIndexOfAdPositionMap[position] = index

        if (adInfo.isOpen) {
            if (SpAdCache.isHaveOpenAd(config)) {
                AppLog.ads.d("复用已有广告缓存 类型=open 位置=$position")
                return
            }
            if (loadingAdStateMap[adInfo.adUnitId] == true) {
                AppLog.ads.d("跳过广告加载 类型=open 位置=$position 原因=已有请求正在执行")
                return
            }
            loadOpenAd(position, adInfo, scene)
            return
        }
        if (adInfo.isInterstitial) {
            if (SpAdCache.isHaveEnableInterstitialAd(position, config)) {
                AppLog.ads.d("复用已有广告缓存 类型=interstitial 位置=$position")
                return
            }
            if (loadingAdStateMap[adInfo.adUnitId] == true) {
                AppLog.ads.d("跳过广告加载 类型=interstitial 位置=$position 原因=已有请求正在执行")
                return
            }
            loadInterstitialAd(position, adInfo, scene)
            return
        }
        AppLog.ads.e("跳过广告加载 位置=$position 原因=广告类型无效 ${adInfo.adType}")
    }

    private fun loadOpenAd(position: String, adInfo: SpAdInfo, scene: String) {
        val sessionId = SpAdSession.newId()
        sessionIdTryCountMap[sessionId] = 0
        loadingAdStateMap[adInfo.adUnitId] = true
        AppLog.ads.i("开始加载广告 类型=open 位置=$position 单元=${adInfo.adUnitId}")
        requestOpenAd(position, adInfo, scene, sessionId)
    }

    private fun loadInterstitialAd(position: String, adInfo: SpAdInfo, scene: String) {
        val sessionId = SpAdSession.newId()
        sessionIdTryCountMap[sessionId] = 0
        loadingAdStateMap[adInfo.adUnitId] = true
        AppLog.ads.i("开始加载广告 类型=interstitial 位置=$position 单元=${adInfo.adUnitId}")
        requestInterstitialAd(position, adInfo, scene, sessionId)
    }

    /** 同一 [sessionId] 的一次开屏请求。重试走这里，避免重新发 session、把计数清零。 */
    private fun requestOpenAd(position: String, adInfo: SpAdInfo, scene: String, sessionId: String) {
        val life = AdTrackingHelper.begin(AdTrackingHelper.AdType.OPEN, position)
        AppOpenAd.load(
            appContext,
            adInfo.adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loadingAdStateMap[adInfo.adUnitId] = false
                    sessionIdTryCountMap.remove(sessionId)
                    SpAdCache.putOpenAd(SpCachedAd(ad, System.currentTimeMillis(), sessionId))
                    AdTrackingHelper.filled(life, ad.responseInfo)
                    AdTrackingHelper.remember(sessionId, life)
                    AppLog.ads.i("广告加载成功 类型=open 位置=$position 单元=${adInfo.adUnitId}")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AppLog.ads.w("广告加载失败 类型=open 位置=$position 单元=${adInfo.adUnitId} 错误码=${error.code} 错误信息=${error.message}")
                    AdTrackingHelper.failed(life, error.code, error.message)
                    onPreloadFailed(
                        position = position,
                        adInfo = adInfo,
                        scene = scene,
                        sessionId = sessionId,
                        message = error.message,
                    )
                }
            },
        )
    }

    /** 同一 [sessionId] 的一次插屏请求。 */
    private fun requestInterstitialAd(
        position: String,
        adInfo: SpAdInfo,
        scene: String,
        sessionId: String,
    ) {
        val life = AdTrackingHelper.begin(AdTrackingHelper.AdType.INTERSTITIAL, position)
        InterstitialAd.load(
            appContext,
            adInfo.adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingAdStateMap[adInfo.adUnitId] = false
                    sessionIdTryCountMap.remove(sessionId)
                    SpAdCache.putInterstitialAd(SpCachedAd(ad, System.currentTimeMillis(), sessionId))
                    AdTrackingHelper.filled(life, ad.responseInfo)
                    AdTrackingHelper.remember(sessionId, life)
                    AppLog.ads.i("广告加载成功 类型=interstitial 位置=$position 单元=${adInfo.adUnitId}")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    AppLog.ads.w("广告加载失败 类型=interstitial 位置=$position 单元=${adInfo.adUnitId} 错误码=${error.code} 错误信息=${error.message}")
                    AdTrackingHelper.failed(life, error.code, error.message)
                    onPreloadFailed(
                        position = position,
                        adInfo = adInfo,
                        scene = scene,
                        sessionId = sessionId,
                        message = error.message,
                    )
                }
            },
        )
    }

    /**
     * Apollo `onAdFailedToPreload`：同一 session 失败次数小于 [tryCountLimit] 时再请求一次，
     * 满了才换列表里的下一个单元。白牌 SDK 会自己再次回调；GMA 的 `onAdFailedToLoad` 只来一次，
     * 所以这里必须重新 `load`。
     */
    private fun onPreloadFailed(
        position: String,
        adInfo: SpAdInfo,
        scene: String,
        sessionId: String,
        message: String,
    ) {
        AppLog.ads.d("广告加载重试 位置=$position 单元=${adInfo.adUnitId} 错误信息=$message")

        val tried = (sessionIdTryCountMap[sessionId] ?: 0) + 1
        if (tried < tryCountLimit) {
            sessionIdTryCountMap[sessionId] = tried
            AppLog.ads.i("广告加载重试 位置=$position 单元=${adInfo.adUnitId} 次数=$tried/$tryCountLimit")
            if (adInfo.isOpen) requestOpenAd(position, adInfo, scene, sessionId)
            else requestInterstitialAd(position, adInfo, scene, sessionId)
            return
        }
        loadingAdStateMap[adInfo.adUnitId] = false
        sessionIdTryCountMap.remove(sessionId)
        val current = loadIndexOfAdPositionMap[position] ?: 0
        checkAndLoadAd(position, scene, current + 1)
    }
    // endregion

    // region 取用

    /**
     * Apollo `getFullScreenAd(position)`：按列表顺序，第一个命中缓存就返回。
     * 开屏走 `ac_launch` 的缓存，插屏走该位置的缓存。
     */
    fun getFullScreenAd(position: String): SpCachedAd<*>? {
        val list = config.adInfoList(position) ?: return null
        for (adInfo in list) {
            val cached: SpCachedAd<*>? = when {
                adInfo.isOpen -> SpAdCache.getOpenAd(config, blocking)
                adInfo.isInterstitial -> SpAdCache.getInterstitialAd(position, config, blocking)
                else -> null
            }
            if (cached != null) return cached
        }
        return null
    }

    /**
     * Apollo `showInterstitialAdNoWait(position, onDone)`：没缓存就立刻回调
     * （调用方据此继续自己的流程，不等待）。
     */
    fun showInterstitialAdNoWait(position: String, onDone: () -> Unit) {
        if (blocking.isShowPartialAdsOnly(position)) {
            AppLog.ads.i("跳过广告展示 类型=interstitial 位置=$position 原因=只展示部分广告")
            onDone()
            return
        }
        if (!blocking.canShowAdByShowCountLimit()) {
            AppLog.ads.i("跳过广告展示 类型=interstitial 位置=$position 原因=达到当日展示上限")
            SpTrackEvents.adBlocked(AdTrackingHelper.AdType.INTERSTITIAL, position, "daily_cap")
            onDone()
            return
        }
        if (blocking.isVPNBlock()) {
            AppLog.ads.i("跳过广告展示 类型=interstitial 位置=$position 原因=VPN拦截")
            onDone()
            return
        }
        if (!blocking.canShowAllAd()) {
            AppLog.ads.i("跳过广告展示 类型=interstitial 位置=$position 原因=总闸拦截")
            SpTrackEvents.adBlocked(AdTrackingHelper.AdType.INTERSTITIAL, position, "disabled")
            onDone()
            return
        }
        SpTracker.log(appContext, SpTracker.SHARK_AD_CHANCE)
        val cached = SpAdCache.getInterstitialAd(position, config, blocking)
        if (cached == null) {
            AppLog.ads.i("跳过广告展示 类型=interstitial 位置=$position 原因=广告尚未就绪")
            onDone()
            return
        }
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            AppLog.ads.i("跳过广告展示 类型=interstitial 位置=$position 原因=页面已销毁")
            // 缓存条目已经在 getInterstitialAd 里出队了；补一次预加载
            checkAndLoadAd(position)
            return
        }
        showAd(
            ad = cached.ad,
            sessionId = cached.sessionId,
            scene = position,
            reloadPosition = position,
            // Apollo：没展示出来才回调 onDone；展示出来由调用方自己的流程接管
            onResult = { showed, _ -> if (!showed) onDone() },
        )
    }
    // endregion

    // region 展示

    /**
     * Apollo `showAd(...)`：先盖 500ms 遮罩，遮罩关闭时真正 `show()`。
     *
     * @param onResult `(showed, dismissed)`，与 Apollo 的 `ds0` 回调同义。
     */
    fun showAd(
        ad: Any,
        sessionId: String,
        scene: String,
        reloadPosition: String,
        onResult: (showed: Boolean, dismissed: Boolean) -> Unit,
    ) {
        val host = activity
        if (isAdTryShowing) {
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=已有广告正在展示")
            return
        }
        if (host == null) {
            AppLog.ads.e("跳过广告展示 位置=$scene 原因=没有宿主页面")
            onResult(false, false)
            return
        }
        isAdTryShowing = true
        AppLog.ads.i("开始展示广告 位置=$scene")

        when (ad) {
            is InterstitialAd -> {
                ad.fullScreenContentCallback = fullScreenCallbacks(
                    scene = scene,
                    sessionId = sessionId,
                    reloadPosition = reloadPosition,
                    onResult = onResult,
                )
                ad.setOnPaidEventListener { value ->
                    SpAdIncomeReport.sendAdIncome(
                        appContext,
                        value,
                        ad.responseInfo,
                        AdTrackingHelper.AdType.INTERSTITIAL,
                        reloadPosition,
                    )
                }
                SpFullScreenMask.show(host) {
                    if (host.isFinishing || host.isDestroyed) {
                        isAdTryShowing = false
                        AppLog.ads.i("跳过广告展示 类型=interstitial 位置=$scene 原因=页面已销毁")
                        onResult(false, false)
                        return@show
                    }
                    ad.show(host)
                }
            }

            is AppOpenAd -> {
                ad.fullScreenContentCallback = fullScreenCallbacks(
                    scene = scene,
                    sessionId = sessionId,
                    reloadPosition = reloadPosition,
                    onResult = onResult,
                )
                ad.setOnPaidEventListener { value ->
                    SpAdIncomeReport.sendAdIncome(
                        appContext,
                        value,
                        ad.responseInfo,
                        AdTrackingHelper.AdType.OPEN,
                        reloadPosition,
                    )
                }
                SpFullScreenMask.show(host) {
                    if (host.isFinishing || host.isDestroyed) {
                        isAdTryShowing = false
                        AppLog.ads.i("跳过广告展示 类型=open 位置=$scene 原因=页面已销毁")
                        onResult(false, false)
                        return@show
                    }
                    ad.show(host)
                }
            }

            else -> {
                AppLog.ads.e("跳过广告展示 位置=$scene 原因=不支持的广告类型 ${ad.javaClass.simpleName}")
                isAdTryShowing = false
            }
        }
    }

    private fun fullScreenCallbacks(
        scene: String,
        sessionId: String,
        reloadPosition: String,
        onResult: (Boolean, Boolean) -> Unit,
    ) = object : FullScreenContentCallback() {
        override fun onAdClicked() {
            SpAdSession.markAdClicked()
            AppLog.ads.i("广告已点击 位置=$scene")
            AdTrackingHelper.recall(sessionId)?.let { AdTrackingHelper.click(it) }
        }

        override fun onAdImpression() {
            AppLog.ads.i("广告已曝光 位置=$scene")
            AdTrackingHelper.recall(sessionId)?.let { AdTrackingHelper.impression(it, scene) }
        }

        override fun onAdShowedFullScreenContent() {
            isAdTryShowing = false
            blocking.updateAdShowCount()
            onResult(true, false)
            // Apollo: 展示成功立刻补下一次预加载
            checkAndLoadAd(reloadPosition)
        }

        override fun onAdDismissedFullScreenContent() {
            AppLog.ads.i("广告已关闭 位置=$scene")
            AdTrackingHelper.recall(sessionId)?.let { AdTrackingHelper.close(it) }
            AdTrackingHelper.forget(sessionId)
            onResult(false, true)
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            isAdTryShowing = false
            AppLog.ads.w("广告展示失败 位置=$scene 错误码=${error.code} 错误信息=${error.message}")
            AdTrackingHelper.forget(sessionId)
            SpTracker.log(
                appContext,
                SpTracker.SHARK_AD_IMPRESSION_ERROR,
                android.os.Bundle().apply {
                    putString("code", error.code.toString())
                    putString("message", error.message)
                },
            )
            onResult(false, false)
            // Apollo: 展示失败也补一次预加载
            checkAndLoadAd(reloadPosition)
        }
    }
    // endregion

    /** Apollo `release()`。 */
    fun release() {
        sessionIdTryCountMap.clear()
        isAdTryShowing = false
    }





}
