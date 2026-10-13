package com.scanpro.cam.pdf.ads.manager

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.scanpro.cam.pdf.ads.SpAdConfig
import com.scanpro.cam.pdf.ads.SpAdMobInit
import com.scanpro.cam.pdf.ads.SpAdSession
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.analytics.SpTracker

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

    /** Apollo `loadBannerAd(position)`。 */
    fun loadBannerAd(position: String) {
        if (isReleased) return
        if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) return
        if (blocking.isShowPartialAdsOnly(position)) return
        if (blocking.isVPNBlock()) return
        if (!blocking.canShowAdByShowCountLimit()) return
        if (!blocking.canShowAllAd()) return

        SpTracker.log(appContext, SpTracker.SHARK_AD_CHANCE)
        val list = config.adInfoList(position) ?: return
        val adInfo = list.firstOrNull() ?: return

        val sessionId = SpAdSession.newId()
        val metrics = activity.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        val banner = AdView(activity).apply {
            adUnitId = adInfo.adUnitId
            setAdSize(AdSize(widthDp, 70))
        }
        adView = banner

        SpTracker.log(
            appContext,
            SpTracker.SHARK_AD_PLACEMENT_REQUEST,
            Bundle().apply { putString("position", position); putString("unit", adInfo.adUnitId) },
        )
        banner.adListener = object : AdListener() {
            override fun onAdLoaded() {
                SpTracker.log(
                    appContext,
                    SpTracker.SHARK_AD_SOURCE_REQUEST,
                    Bundle().apply { putString("position", position); putString("unit", adInfo.adUnitId) },
                )
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.d(TAG, "banner failed: ${error.message}")
                SpTracker.log(
                    appContext,
                    SpTracker.SHARK_AD_SOURCE_ERROR,
                    Bundle().apply {
                        putString("position", position)
                        putString("unit", adInfo.adUnitId)
                        putString("message", error.message)
                    },
                )
            }

            override fun onAdImpression() {
                blocking.updateAdShowCount()
                SpTracker.log(appContext, SpTracker.SHARK_AD_IMPRESSION)
            }

            override fun onAdClicked() {
                SpAdSession.markAdClicked()
                SpTracker.log(appContext, SpTracker.SHARK_AD_CLICK)
            }
        }

        viewGroup.removeAllViews()
        viewGroup.addView(banner)
        banner.loadAd(AdRequest.Builder().build())
        Log.i(TAG, "loadBannerAd position=$position unit=${adInfo.adUnitId} session=$sessionId")
    }

    /** Apollo `release()`。 */
    fun release() {
        isReleased = true
        adView?.let { banner ->
            banner.destroy()
        }
        adView = null
        viewGroup.removeAllViews()
    }

    private companion object {
        const val TAG = "SpBannerAd"
    }
}
