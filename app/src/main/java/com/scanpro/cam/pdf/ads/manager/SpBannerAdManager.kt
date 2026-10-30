package com.scanpro.cam.pdf.ads.manager

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.scanpro.cam.pdf.ads.SpAdConfig
import com.scanpro.cam.pdf.ads.SpAdIncomeReport
import com.scanpro.cam.pdf.ads.SpAdMobInit
import com.scanpro.cam.pdf.ads.SpAdSession
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.analytics.AdTrackLife
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Banner。照抄 ApolloPDF `ad/manager/BannerAdManager.java`：
 *
 * - Banner 不缓存，每次进页面现拉，直接挂到页面给的容器里
 * - 尺寸用「屏宽 dp × 70」，取 Apollo 的写法
 * - 加载前同样过门控，并记 `Shark_ad_chance`
 * - [release] 必须由页面在销毁时调用，否则 AdView 泄漏
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/manager/BannerAdManager.java`
 */
class SpBannerAdManager(
    private val activity: Activity,
    private val viewGroup: ViewGroup,
) {

    private val appContext: Context = activity.applicationContext

    /** 等价 Apollo `AdConfigKt.getAdInfoList`：读 `RemoteConfig.INSTANCE.getAcAdConfig()`。 */
    private val config: SpAdConfig get() = SpAdsHub.getOrNull()?.config() ?: SpAdConfig()
    private val blocking: SpBlockingLogic get() = SpBlockingLogic.get(appContext)

    private var adView: AdView? = null
    private var isReleased = false
    private var bannerLife: AdTrackLife? = null
    private var retriedAfterConfig = false
    private val scope = CoroutineScope(Dispatchers.Main)

    /** Apollo `loadBannerAd(position)`。 */
    fun loadBannerAd(position: String) {
        if (isReleased) {
            AppLog.ads.i("跳过广告加载 类型=banner 位置=$position 原因=管理器已释放")
            return
        }
        if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) {
            AppLog.ads.i("跳过广告加载 类型=banner 位置=$position 原因=广告SDK未初始化")
            return
        }
        if (blocking.isShowPartialAdsOnly(position)) {
            AppLog.ads.i("跳过广告加载 类型=banner 位置=$position 原因=只展示部分广告")
            return
        }
        if (blocking.isVPNBlock()) {
            AppLog.ads.i("跳过广告加载 类型=banner 位置=$position 原因=VPN拦截")
            return
        }
        if (!blocking.canShowAdByShowCountLimit()) {
            AppLog.ads.i("跳过广告加载 类型=banner 位置=$position 原因=达到当日展示上限")
            SpTrackEvents.adBlocked(AdTrackingHelper.AdType.BANNER, position, "daily_cap")
            return
        }
        if (!blocking.canShowAllAd()) {
            AppLog.ads.i("跳过广告加载 类型=banner 位置=$position 原因=总闸拦截")
            SpTrackEvents.adBlocked(AdTrackingHelper.AdType.BANNER, position, "disabled")
            return
        }

        SpTracker.log(appContext, SpTracker.SHARK_AD_CHANCE)
        val list = config.adInfoList(position)
        if (list.isNullOrEmpty()) {
            // 配置晚于页面创建是常态：这里补一次等配置就绪的重新加载，否则这个 Activity
            // 整个生命周期都不会再请求 banner。
            AppLog.ads.i("跳过广告加载 类型=banner 位置=$position 原因=没有广告位配置")
            retryWhenConfigApplied(position)
            return
        }
        val adInfo = list.firstOrNull() ?: run {
            AppLog.ads.i("跳过广告加载 类型=banner 位置=$position 原因=广告单元为空")
            return
        }

        val metrics = activity.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        val banner = AdView(activity).apply {
            adUnitId = adInfo.adUnitId
            setAdSize(AdSize(widthDp, 70))
        }
        adView = banner


        closeShown(bannerLife)
        val life = AdTrackingHelper.begin(AdTrackingHelper.AdType.BANNER, position, adInfo.adUnitId)
        bannerLife = life
        banner.setOnPaidEventListener { value ->
            SpAdIncomeReport.sendAdIncome(
                appContext,
                value,
                banner.responseInfo,
                AdTrackingHelper.AdType.BANNER,
                position,
                life,
            )
        }
        banner.adListener = object : AdListener() {
            override fun onAdLoaded() {
                AppLog.ads.i("广告加载成功 类型=banner 位置=$position 单元=${adInfo.adUnitId}")
                AdTrackingHelper.filled(life, banner.responseInfo)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                AppLog.ads.w("广告加载失败 类型=banner 位置=$position 单元=${adInfo.adUnitId} 错误码=${error.code} 错误信息=${error.message}")

                AdTrackingHelper.failed(life, error.code, error.message)
            }

            override fun onAdImpression() {
                blocking.updateAdShowCount()
                AppLog.ads.i("广告已曝光 类型=banner 位置=$position")
                AdTrackingHelper.impression(life)
            }

            override fun onAdClicked() {
                SpAdSession.markAdClicked()
                AppLog.ads.i("广告已点击 类型=banner 位置=$position")
                AdTrackingHelper.click(life)
            }
        }

        viewGroup.removeAllViews()
        viewGroup.addView(banner)
        AppLog.ads.i("开始加载广告 类型=banner 位置=$position 单元=${adInfo.adUnitId}")
        banner.loadAd(AdRequest.Builder().build())
    }

    /** Apollo `release()`。 */
    fun release() {
        isReleased = true
        closeShown(bannerLife)
        bannerLife = null
        adView?.let { banner ->
            banner.destroy()
        }
        adView = null
        viewGroup.removeAllViews()
    }

    /** 配置还没到就先记着，等 `configApplied` 变 true 后补拉一次（只补一次，避免循环）。 */
    private fun retryWhenConfigApplied(position: String) {
        if (retriedAfterConfig) return
        retriedAfterConfig = true
        val flow = SpAdsHub.getOrNull()?.configApplied ?: return
        scope.launch {
            flow.first { it }
            if (isReleased || activity.isFinishing || activity.isDestroyed) return@launch
            AppLog.ads.i("banner 配置就绪后补拉 位置=$position")
            loadBannerAd(position)
        }
    }

    private fun closeShown(life: AdTrackLife?) {
        if (life != null && life.showStartMs > 0L) {
            AppLog.ads.i("广告已关闭 类型=banner 位置=${life.posId}")
            AdTrackingHelper.close(life)
        }
    }
}
