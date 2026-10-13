package com.scanpro.cam.pdf.ads.manager

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpAdCache
import com.scanpro.cam.pdf.ads.SpAdConfig
import com.scanpro.cam.pdf.ads.SpAdInfo
import com.scanpro.cam.pdf.ads.SpAdMobInit
import com.scanpro.cam.pdf.ads.SpAdIncomeReport
import com.scanpro.cam.pdf.ads.SpAdSession
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.ads.SpCachedAd
import com.scanpro.cam.pdf.ads.SpNatConfig
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.analytics.SpTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 原生广告。**照抄 ApolloPDF `ad/manager/NativeAdManager.java`**，白牌加载换成 GMA `AdLoader`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/manager/NativeAdManager.java`
 *
 * 关键行为（原样保留）：
 * - 加载也走「位置 → 单元列表」，失败换下一个单元
 * - [checkLoadAndShowAd]：有缓存直接显示，没有则边加载边轮询（200ms）等就绪
 * - 关闭键：`switch_open != 1` 直接隐藏；`switch_open == 1` 时再按
 *   `jump_percent` 掷骰——点数 < jumpPercent 就 **替用户点一次 CTA**（误触），否则才隐藏
 * - 展示中 `adLayoutView` 先隐藏，避免露出上一个广告
 */
class SpNativeAdManager internal constructor(
    private val activity: Activity,
    /** 页面提供的容器，Apollo 的 `adLayoutView`。 */
    private val adLayoutView: ViewGroup? = null,
) {

    private val appContext: Context = activity.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main)

    /** 等价 Apollo `AdConfigKt.getAdInfoList`：读 `RemoteConfig.INSTANCE.getAcAdConfig()`。 */
    private val config: SpAdConfig get() = SpAdsHub.getOrNull()?.config() ?: SpAdConfig()

    /** 等价 Apollo `RemoteConfig.INSTANCE.getNatConfig()`。缺省与 `DefaultConfig.ac_nat_config` 相同。 */
    private val natConfig: SpNatConfig get() = SpAdsHub.getOrNull()?.nat() ?: SpNatConfig()
    private val blocking: SpBlockingLogic get() = SpBlockingLogic.get(appContext)

    private val loadIndexOfAdPositionMap = HashMap<String, Int>()
    private var isLoading = false
    private var currentNativeAd: NativeAd? = null
    private var pollJob: Job? = null

    /**
     * 首页/结果页调用：**尽量现在就出广告**。有缓存立即显示；没有则加载并轮询等待。
     * Apollo `checkLoadAndShowAd(position, style)`。
     */
    fun checkLoadAndShowAd(position: String, style: SpNativeAdStyle) {
        if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) return
        if (!canShow(position)) return
        SpTracker.log(appContext, SpTracker.SHARK_AD_CHANCE)

        val cached = SpAdCache.getNativeAd(position, config, blocking)
        if (cached != null) {
            showAd(position, cached, style)
            return
        }
        isLoading = true
        if (tryLoadAd(position, 0)) {
            startCheckShowAd(position, style)
        }
    }

    /** Apollo `startCheckShowAd`：每 200ms 看一眼 loading，落了就取缓存显示。 */
    private fun startCheckShowAd(position: String, style: SpNativeAdStyle) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isLoading) {
                delay(200L)
            }
            if (!blocking.canShowAdByShowCountLimit()) return@launch
            val cached = SpAdCache.getNativeAd(position, config, blocking) ?: return@launch
            showAd(position, cached, style)
        }
    }

    /** Apollo `tryCacheAd(position)`：只预热，不显示。 */
    fun tryCacheAd(position: String) {
        if (!canShow(position)) return
        if (SpAdCache.isHaveEnableNativeAd(position, config)) return
        tryLoadAd(position, 0)
    }

    private fun canShow(position: String): Boolean =
        !blocking.isShowPartialAdsOnly(position) &&
            !blocking.isVPNBlock() &&
            blocking.canShowAdByShowCountLimit() &&
            blocking.canShowAllAd()

    /** Apollo `tryLoadAd(position, index)`。 */
    private fun tryLoadAd(position: String, index: Int): Boolean {
        val list = config.adInfoList(position) ?: return false
        if (index > list.size - 1) {
            isLoading = false
            return false
        }
        val adInfo = list[index]
        loadIndexOfAdPositionMap[position] = index
        loadAd(position, adInfo)
        return true
    }

    /** Apollo `loadAD(position, adInfo)`：AdLoader 拉原生，成功入缓存，失败换下一个单元。 */
    private fun loadAd(position: String, adInfo: SpAdInfo) {
        if (adInfo.adUnitId.isEmpty()) {
            tryLoadAd(position, (loadIndexOfAdPositionMap[position] ?: 0) + 1)
            return
        }
        val sessionId = SpAdSession.newId()
        SpTracker.log(
            appContext,
            SpTracker.SHARK_AD_PLACEMENT_REQUEST,
            Bundle().apply { putString("position", position); putString("unit", adInfo.adUnitId) },
        )
        AdLoader.Builder(appContext, adInfo.adUnitId)
            .forNativeAd { nativeAd ->
                isLoading = false
                SpTracker.log(
                    appContext,
                    SpTracker.SHARK_AD_SOURCE_REQUEST,
                    Bundle().apply { putString("position", position); putString("unit", adInfo.adUnitId) },
                )
                SpAdCache.putNativeAd(SpCachedAd(nativeAd, System.currentTimeMillis(), sessionId))
                Log.i(TAG, "native loaded position=$position unit=${adInfo.adUnitId}")
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    SpTracker.log(
                        appContext,
                        SpTracker.SHARK_AD_SOURCE_ERROR,
                        Bundle().apply {
                            putString("position", position)
                            putString("unit", adInfo.adUnitId)
                            putString("message", error.message)
                        },
                    )
                    val current = loadIndexOfAdPositionMap[position] ?: 0
                    tryLoadAd(position, current + 1)
                }

                override fun onAdImpression() {
                    SpTracker.log(appContext, SpTracker.SHARK_AD_IMPRESSION)
                }

                override fun onAdClicked() {
                    SpAdSession.markAdClicked()
                    SpTracker.log(appContext, SpTracker.SHARK_AD_CLICK)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build(),
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    /** Apollo `showAd(position, bean, style)`：绑定数据 + 关闭键误触 + 记数。 */
    private fun showAd(position: String, cached: SpCachedAd<NativeAd>, style: SpNativeAdStyle) {
        adLayoutView?.visibility = View.GONE
        if (!canShow(position)) return
        currentNativeAd = cached.ad
        cached.ad.setOnPaidEventListener { value ->
            SpAdIncomeReport.sendAdIncome(appContext, value, cached.ad.responseInfo)
        }
        bindAndShow(cached.ad, style)
    }

    private fun bindAndShow(nativeAd: NativeAd, style: SpNativeAdStyle) {
        val host = adLayoutView ?: return
        host.removeAllViews()
        val view = when (style) {
            SpNativeAdStyle.big -> bindBig(host, nativeAd)
            SpNativeAdStyle.small -> bindSmall(host, nativeAd)
        }
        host.visibility = View.VISIBLE
        host.addView(view)
        blocking.updateAdShowCount()
    }

    private fun bindBig(host: ViewGroup, nativeAd: NativeAd): View {
        val view = activity.layoutInflater.inflate(R.layout.sp_custom_native_ad_big_layout, host, false)
        val adView = view.findViewById<NativeAdView>(R.id.nativeAdView)
        val headline = view.findViewById<TextView>(R.id.adHeadline)
        val body = view.findViewById<TextView>(R.id.adBody)
        val cta = view.findViewById<TextView>(R.id.adCallToAction)
        val icon = view.findViewById<ImageView>(R.id.adAppIcon)
        val advertiser = view.findViewById<TextView>(R.id.adAdvertiser)
        val media = view.findViewById<MediaView>(R.id.media)
        val close = view.findViewById<ImageView>(R.id.imageClose)

        adView.mediaView = media
        media.mediaContent = nativeAd.mediaContent
        adView.headlineView = headline
        headline.text = nativeAd.headline
        adView.bodyView = body
        body.text = nativeAd.body
        adView.callToActionView = cta
        cta.text = nativeAd.callToAction
        adView.iconView = icon
        nativeAd.icon?.let { icon.setImageDrawable(it.drawable); icon.visibility = View.VISIBLE }
            ?: run { icon.visibility = View.GONE }
        adView.advertiserView = advertiser
        advertiser.text = nativeAd.advertiser

        advertiser.visibility = assetVisibility(nativeAd.advertiser)
        body.visibility = assetVisibility(nativeAd.body)
        cta.visibility = assetVisibility(nativeAd.callToAction)
        headline.visibility = assetVisibility(nativeAd.headline)
        icon.visibility = assetVisibility(nativeAd.icon)

        applyCloseButton(adView, cta, close)
        adView.setNativeAd(nativeAd)
        return view
    }

    private fun bindSmall(host: ViewGroup, nativeAd: NativeAd): View {
        val view = activity.layoutInflater.inflate(R.layout.sp_custom_native_ad_small_layout, host, false)
        val adView = view.findViewById<NativeAdView>(R.id.nativeAdView)
        val headline = view.findViewById<TextView>(R.id.adHeadline)
        val body = view.findViewById<TextView>(R.id.adBody)
        val cta = view.findViewById<TextView>(R.id.adCallToAction)
        val icon = view.findViewById<ImageView>(R.id.adAppIcon)
        val advertiser = view.findViewById<TextView>(R.id.adAdvertiser)
        val close = view.findViewById<ImageView>(R.id.imageClose)

        adView.headlineView = headline
        headline.text = nativeAd.headline
        adView.bodyView = body
        body.text = nativeAd.body
        adView.callToActionView = cta
        cta.text = nativeAd.callToAction
        adView.iconView = icon
        nativeAd.icon?.let { icon.setImageDrawable(it.drawable); icon.visibility = View.VISIBLE }
            ?: run { icon.visibility = View.GONE }
        adView.advertiserView = advertiser
        advertiser.text = nativeAd.advertiser

        advertiser.visibility = assetVisibility(nativeAd.advertiser)
        body.visibility = assetVisibility(nativeAd.body)
        cta.visibility = assetVisibility(nativeAd.callToAction)
        headline.visibility = assetVisibility(nativeAd.headline)
        icon.visibility = assetVisibility(nativeAd.icon)

        applyCloseButton(adView, cta, close)
        adView.setNativeAd(nativeAd)
        return view
    }

    /**
     * Apollo 的关闭键判据（`displayBigNativeAd` / `displayNativeAd`）：
     * `switch_open != 1` → 关闭键隐藏；否则 `switch_open == 1` 时按 `jump_percent` 掷骰。
     */
    private fun applyCloseButton(
        adView: NativeAdView,
        cta: TextView,
        close: ImageView,
    ) {
        val nat = natConfig
        if (nat.switchOpen != 1) {
            close.visibility = View.GONE
            return
        }
        if (!blocking.isShowNativeAdCloseButton()) {
            close.visibility = View.GONE
            return
        }
        close.visibility = View.VISIBLE
        close.setOnClickListener {
            val roll = Random.nextInt(100)
            if (roll < nat.jumpPercent) {
                // 误触：替用户点一次广告 CTA
                adView.callToActionView?.performClick() ?: cta.performClick()
            } else {
                adLayoutView?.visibility = View.GONE
            }
        }
    }

    private fun assetVisibility(asset: Any?): Int = if (asset == null) View.INVISIBLE else View.VISIBLE

    /** Apollo `release()`。 */
    fun release() {
        pollJob?.cancel()
        adLayoutView?.removeAllViews()
        currentNativeAd?.destroy()
        currentNativeAd = null
    }

    companion object {
        private const val TAG = "SpNativeAd"
    }
}
