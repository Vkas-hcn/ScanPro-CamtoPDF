package com.scanpro.cam.pdf.ads.manager

import android.app.Activity
import android.content.Context
import android.util.Log
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
import com.scanpro.cam.pdf.analytics.SpTracker

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
        if (!SpAdMobInit.getOrNull()?.isMobileAdsInitialize().let { it == true }) return
        if (blocking.isShowPartialAdsOnly(position)) return
        if (blocking.isVPNBlock()) return
        if (!blocking.canShowAdByShowCountLimit()) return
        if (!blocking.canShowAllAd()) return

        val list = config.adInfoList(position) ?: return
        if (index > list.size - 1) return
        val adInfo = list[index]
        loadIndexOfAdPositionMap[position] = index

        if (adInfo.isOpen) {
            if (SpAdCache.isHaveOpenAd(config)) return
            if (loadingAdStateMap[adInfo.adUnitId] == true) return
            loadOpenAd(position, adInfo, scene)
            return
        }
        if (adInfo.isInterstitial) {
            if (SpAdCache.isHaveEnableInterstitialAd(position, config)) return
            if (loadingAdStateMap[adInfo.adUnitId] == true) return
            loadInterstitialAd(position, adInfo, scene)
            return
        }
        Log.e(TAG, "checkAndLoadAd: bad ad type ${adInfo.adType} at $position")
    }

    private fun loadOpenAd(position: String, adInfo: SpAdInfo, scene: String) {
        val sessionId = SpAdSession.newId()
        sessionIdTryCountMap[sessionId] = 0
        loadingAdStateMap[adInfo.adUnitId] = true
        reportStartLoading(scene, adInfo.adUnitId, sessionId)
        requestOpenAd(position, adInfo, scene, sessionId)
    }

    private fun loadInterstitialAd(position: String, adInfo: SpAdInfo, scene: String) {
        val sessionId = SpAdSession.newId()
        sessionIdTryCountMap[sessionId] = 0
        loadingAdStateMap[adInfo.adUnitId] = true
        reportStartLoading(scene, adInfo.adUnitId, sessionId)
        requestInterstitialAd(position, adInfo, scene, sessionId)
    }

    /** 同一 [sessionId] 的一次开屏请求。重试走这里，避免重新发 session、把计数清零。 */
    private fun requestOpenAd(position: String, adInfo: SpAdInfo, scene: String, sessionId: String) {
        AppOpenAd.load(
            appContext,
            adInfo.adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loadingAdStateMap[adInfo.adUnitId] = false
                    sessionIdTryCountMap.remove(sessionId)
                    SpAdCache.putOpenAd(SpCachedAd(ad, System.currentTimeMillis(), sessionId))
                    reportLoaded(scene, adInfo.adUnitId, sessionId)
                    Log.i(TAG, "open ad loaded position=$position unit=${adInfo.adUnitId}")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
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
        InterstitialAd.load(
            appContext,
            adInfo.adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingAdStateMap[adInfo.adUnitId] = false
                    sessionIdTryCountMap.remove(sessionId)
                    SpAdCache.putInterstitialAd(SpCachedAd(ad, System.currentTimeMillis(), sessionId))
                    reportLoaded(scene, adInfo.adUnitId, sessionId)
                    Log.i(TAG, "interstitial loaded position=$position unit=${adInfo.adUnitId}")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
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
        Log.d(TAG, "preload failed position=$position unit=${adInfo.adUnitId}: $message")
        SpTracker.log(
            appContext,
            SpTracker.SHARK_AD_SOURCE_ERROR,
            android.os.Bundle().apply {
                putString("position", position)
                putString("unit", adInfo.adUnitId)
                putString("message", message)
            },
        )
        val tried = (sessionIdTryCountMap[sessionId] ?: 0) + 1
        if (tried < tryCountLimit) {
            sessionIdTryCountMap[sessionId] = tried
            Log.d(TAG, "retry $tried/$tryCountLimit position=$position unit=${adInfo.adUnitId}")
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
        if (blocking.isShowPartialAdsOnly(position) ||
            !blocking.canShowAdByShowCountLimit() ||
            blocking.isVPNBlock() ||
            !blocking.canShowAllAd()
        ) {
            onDone()
            return
        }
        SpTracker.log(appContext, SpTracker.SHARK_AD_CHANCE)
        val cached = SpAdCache.getInterstitialAd(position, config, blocking)
        if (cached == null) {
            onDone()
            return
        }
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
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
        if (isAdTryShowing) return
        if (host == null) {
            Log.e(TAG, "showAd: no host activity")
            onResult(false, false)
            return
        }
        isAdTryShowing = true

        when (ad) {
            is InterstitialAd -> {
                ad.fullScreenContentCallback = fullScreenCallbacks(
                    scene = scene,
                    sessionId = sessionId,
                    reloadPosition = reloadPosition,
                    onResult = onResult,
                )
                ad.setOnPaidEventListener { value ->
                    SpAdIncomeReport.sendAdIncome(appContext, value, ad.responseInfo)
                }
                SpFullScreenMask.show(host) {
                    if (host.isFinishing || host.isDestroyed) {
                        isAdTryShowing = false
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
                    SpAdIncomeReport.sendAdIncome(appContext, value, ad.responseInfo)
                }
                SpFullScreenMask.show(host) {
                    if (host.isFinishing || host.isDestroyed) {
                        isAdTryShowing = false
                        onResult(false, false)
                        return@show
                    }
                    ad.show(host)
                }
            }

            else -> {
                Log.e(TAG, "showAd: unsupported type ${ad.javaClass.simpleName}")
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
            SpTracker.log(appContext, SpTracker.SHARK_AD_CLICK)
        }

        override fun onAdImpression() {
            SpTracker.log(appContext, SpTracker.SHARK_AD_IMPRESSION)
        }

        override fun onAdShowedFullScreenContent() {
            isAdTryShowing = false
            blocking.updateAdShowCount()
            onResult(true, false)
            // Apollo: 展示成功立刻补下一次预加载
            checkAndLoadAd(reloadPosition)
        }

        override fun onAdDismissedFullScreenContent() {
            SpTracker.log(appContext, SpTracker.SHARK_AD_CLOSE)
            onResult(false, true)
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            isAdTryShowing = false
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

    private fun reportStartLoading(scene: String, unitId: String, sessionId: String) {
        SpTracker.log(
            appContext,
            SpTracker.SHARK_AD_PLACEMENT_REQUEST,
            android.os.Bundle().apply {
                putString("scene", scene)
                putString("unit", unitId)
                putString("session", sessionId)
            },
        )
    }

    private fun reportLoaded(scene: String, unitId: String, sessionId: String) {
        SpTracker.log(
            appContext,
            SpTracker.SHARK_AD_SOURCE_REQUEST,
            android.os.Bundle().apply {
                putString("scene", scene)
                putString("unit", unitId)
                putString("session", sessionId)
            },
        )
    }

    companion object {
        private const val TAG = "SpFullScreenAd"
    }
}
