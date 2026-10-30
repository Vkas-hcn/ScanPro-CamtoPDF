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
import com.scanpro.cam.pdf.ads.SpAdShowGate
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
 * - 进程级 `loadingPositions` 按 position 串行化，避免预加载/展示 manager 跨实例重复加载
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

    /** sessionId → 已重试次数。 */
    private val sessionIdTryCountMap = LinkedHashMap<String, Int>()

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
        // 进程级互斥：preloadManager 和页面 manager 是不同实例，必须在这里统一挡住重复请求。
        if (position in loadingPositions) {
            AppLog.ads.d("跳过广告加载 位置=$position 原因=已有请求正在执行")
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
            loadOpenAd(position, adInfo, scene)
            return
        }
        if (adInfo.isInterstitial) {
            if (SpAdCache.isHaveEnableInterstitialAd(position, config)) {
                AppLog.ads.d("复用已有广告缓存 类型=interstitial 位置=$position")
                return
            }
            loadInterstitialAd(position, adInfo, scene)
            return
        }
        AppLog.ads.e("跳过广告加载 位置=$position 原因=广告类型无效 ${adInfo.adType}")
    }

    private fun loadOpenAd(position: String, adInfo: SpAdInfo, scene: String) {
        // add 本身是原子的：即使两个线程同时越过上面的 contains 检查，也只有一个能真正发起请求。
        if (!loadingPositions.add(position)) {
            AppLog.ads.d("跳过广告加载 类型=open 位置=$position 原因=已有请求正在执行")
            return
        }
        val sessionId = SpAdSession.newId()
        sessionIdTryCountMap[sessionId] = 0
        AppLog.ads.i("开始加载广告 类型=open 位置=$position 单元=${adInfo.adUnitId}")
        requestOpenAd(position, adInfo, scene, sessionId)
    }

    private fun loadInterstitialAd(position: String, adInfo: SpAdInfo, scene: String) {
        if (!loadingPositions.add(position)) {
            AppLog.ads.d("跳过广告加载 类型=interstitial 位置=$position 原因=已有请求正在执行")
            return
        }
        val sessionId = SpAdSession.newId()
        sessionIdTryCountMap[sessionId] = 0
        AppLog.ads.i("开始加载广告 类型=interstitial 位置=$position 单元=${adInfo.adUnitId}")
        requestInterstitialAd(position, adInfo, scene, sessionId)
    }

    /** 同一 [sessionId] 的一次开屏请求。重试走这里，避免重新发 session、把计数清零。 */
    private fun requestOpenAd(position: String, adInfo: SpAdInfo, scene: String, sessionId: String) {
        val life = AdTrackingHelper.begin(AdTrackingHelper.AdType.OPEN, position, adInfo.adUnitId, scene)
        AppOpenAd.load(
            appContext,
            adInfo.adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loadingPositions.remove(position)
                    sessionIdTryCountMap.remove(sessionId)
                    SpAdCache.putOpenAd(
                        SpCachedAd(ad, System.currentTimeMillis(), sessionId, position, adInfo.adTimeLimit),
                    )
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
        val life = AdTrackingHelper.begin(AdTrackingHelper.AdType.INTERSTITIAL, position, adInfo.adUnitId, scene)
        InterstitialAd.load(
            appContext,
            adInfo.adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingPositions.remove(position)
                    sessionIdTryCountMap.remove(sessionId)
                    SpAdCache.putInterstitialAd(
                        SpCachedAd(ad, System.currentTimeMillis(), sessionId, position, adInfo.adTimeLimit),
                    )
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
     * 预加载失败后的处理。
     *
     * 开屏**不原地重试**：开屏只有几秒窗口，同一个单元连试 3 次等于把窗口耗尽；直接换列表里的
     * 下一个单元，成功率和时延都更好。插屏保留原来的 3 次重试。
     */
    private fun onPreloadFailed(
        position: String,
        adInfo: SpAdInfo,
        scene: String,
        sessionId: String,
        message: String,
    ) {
        AppLog.ads.d("广告加载失败 位置=$position 单元=${adInfo.adUnitId} 错误信息=$message")

        val tried = (sessionIdTryCountMap[sessionId] ?: 0) + 1
        if (!adInfo.isOpen && tried < tryCountLimit) {
            sessionIdTryCountMap[sessionId] = tried
            AppLog.ads.i("广告加载重试 位置=$position 单元=${adInfo.adUnitId} 次数=$tried/$tryCountLimit")
            requestInterstitialAd(position, adInfo, scene, sessionId)
            return
        }
        loadingPositions.remove(position)
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
            // 广告已经从缓存出队：放回去，并且必须回调 onDone，否则调用方流程会卡住。
            putBack(cached.ad, cached.sessionId, position)
            checkAndLoadAd(position)
            onDone()
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
     * 所有提前返回都必须回调 [onResult]，否则调用方的流程（开屏 `finish`、首页跳转）
     * 会永久卡住；展示不出去时广告要放回缓存，而不是就地丢弃。
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
        val adType = if (ad is AppOpenAd) AdTrackingHelper.AdType.OPEN else AdTrackingHelper.AdType.INTERSTITIAL
        if (!SpAdShowGate.tryEnter()) {
            SpTrackEvents.adBlocked(adType, reloadPosition, "another_fullscreen")
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=已有广告正在展示")
            putBack(ad, sessionId, reloadPosition)
            onResult(false, false)
            return
        }
        if (host == null || host.isFinishing || host.isDestroyed) {
            SpAdShowGate.exit()
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=没有可用宿主页面")
            putBack(ad, sessionId, reloadPosition)
            onResult(false, false)
            return
        }
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
                        AdTrackingHelper.recall(sessionId),
                    )
                }
                SpFullScreenMask.show(host) {
                    if (host.isFinishing || host.isDestroyed) {
                        SpAdShowGate.exit()
                        AppLog.ads.i("跳过广告展示 类型=interstitial 位置=$scene 原因=页面已销毁")
                        putBack(ad, sessionId, reloadPosition)
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
                        AdTrackingHelper.recall(sessionId),
                    )
                }
                SpFullScreenMask.show(host) {
                    if (host.isFinishing || host.isDestroyed) {
                        SpAdShowGate.exit()
                        AppLog.ads.i("跳过广告展示 类型=open 位置=$scene 原因=页面已销毁")
                        putBack(ad, sessionId, reloadPosition)
                        onResult(false, false)
                        return@show
                    }
                    ad.show(host)
                }
            }

            else -> {
                SpAdShowGate.exit()
                AppLog.ads.e("跳过广告展示 位置=$scene 原因=不支持的广告类型 ${ad.javaClass.simpleName}")
                onResult(false, false)
            }
        }
    }

    /** 展示没发出去时把广告放回缓存，避免「填充成功但被丢弃」。 */
    private fun putBack(ad: Any, sessionId: String, position: String) {
        val ttl = config.ttlSecondsOf(position) ?: 0
        val now = System.currentTimeMillis()
        runCatching {
            when (ad) {
                is AppOpenAd -> SpAdCache.putOpenAd(SpCachedAd(ad, now, sessionId, position, ttl))
                is InterstitialAd -> SpAdCache.putInterstitialAd(SpCachedAd(ad, now, sessionId, position, ttl))
            }
        }.onFailure { AppLog.ads.w("广告回缓存失败 位置=$position", it) }
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
            blocking.updateAdShowCount()
            onResult(true, false)
            // Apollo: 展示成功立刻补下一次预加载（闸门保持到关闭/失败再释放）
            checkAndLoadAd(reloadPosition)
        }

        override fun onAdDismissedFullScreenContent() {
            AppLog.ads.i("广告已关闭 位置=$scene")
            AdTrackingHelper.recall(sessionId)?.let { AdTrackingHelper.close(it) }
            AdTrackingHelper.forget(sessionId)
            SpAdShowGate.exit()
            onResult(false, true)
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            SpAdShowGate.exit()
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

    /** Apollo `release()`。只清本地重试计数，不强行释放正在展示的广告。 */
    fun release() {
        sessionIdTryCountMap.clear()
    }

    private companion object {
        /**
         * 进程级“正在加载”的广告位集合。
         *
         * `SpAdsHub` 的预加载 manager 和每个 Activity 的展示 manager 是不同实例，
         * 原实例字段挡不住跨实例的重复请求；这里按 position 串行化，保证同一广告位
         * 同一时间最多只有一个 load 在飞。
         */
        val loadingPositions: MutableSet<String> = java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
        )
    }
}
