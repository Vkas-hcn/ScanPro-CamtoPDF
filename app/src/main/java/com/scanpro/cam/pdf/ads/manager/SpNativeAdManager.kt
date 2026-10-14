package com.scanpro.cam.pdf.ads.manager

import android.app.Activity
import android.content.Context
import android.os.Bundle
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
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 原生广告。**照抄 ApolloPDF `ad/manager/NativeAdManager.java`**，白牌加载换成 GMA `AdLoader`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/manager/NativeAdManager.java`
 *
 * 关键行为（原样保留）：
 * - 加载也走「位置 → 单元列表」，失败换下一个单元
 * - [checkLoadAndShowAd]：有缓存直接显示，没有则边加载边轮询（200ms）等就绪
 * - 关闭键：`switch_open != 1` 直接隐藏；`switch_open == 1` 时点击关闭键只隐藏广告
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
    private var shownSessionId: String? = null

    /**
     * 首页/结果页调用：**尽量现在就出广告**。有缓存立即显示；没有则加载并轮询等待。
     * Apollo `checkLoadAndShowAd(position, style)`。
     */
    fun checkLoadAndShowAd(position: String, style: SpNativeAdStyle) {
        if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) {
            AppLog.ads.i("跳过广告展示 类型=native 位置=$position 原因=广告SDK未初始化")
            return
        }
        if (!canShow(position, "展示")) return
        SpTracker.log(appContext, SpTracker.SHARK_AD_CHANCE)

        val cached = SpAdCache.getNativeAd(position, config, blocking)
        if (cached != null) {
            AppLog.ads.i("广告缓存已消费 类型=native 位置=$position")
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
            if (!blocking.canShowAdByShowCountLimit()) {
                AppLog.ads.i("跳过广告展示 类型=native 位置=$position 原因=达到当日展示上限")
                SpTrackEvents.adBlocked(AdTrackingHelper.AdType.NATIVE, position, "daily_cap")
                return@launch
            }
            val cached = SpAdCache.getNativeAd(position, config, blocking) ?: run {
                AppLog.ads.i("跳过广告展示 类型=native 位置=$position 原因=广告尚未就绪")
                return@launch
            }
            showAd(position, cached, style)
        }
    }

    /** Apollo `tryCacheAd(position)`：只预热，不显示。 */
    fun tryCacheAd(position: String) {
        if (!canShow(position, "加载")) return
        if (SpAdCache.isHaveEnableNativeAd(position, config)) {
            AppLog.ads.d("复用已有广告缓存 类型=native 位置=$position")
            return
        }
        tryLoadAd(position, 0, "preload")
    }

    private fun canShow(position: String, action: String): Boolean {
        if (blocking.isShowPartialAdsOnly(position)) {
            AppLog.ads.i("跳过广告$action 类型=native 位置=$position 原因=只展示部分广告")
            return false
        }
        if (blocking.isVPNBlock()) {
            AppLog.ads.i("跳过广告$action 类型=native 位置=$position 原因=VPN拦截")
            return false
        }
        if (!blocking.canShowAdByShowCountLimit()) {
            AppLog.ads.i("跳过广告$action 类型=native 位置=$position 原因=达到当日展示上限")
            if (action == "展示") SpTrackEvents.adBlocked(AdTrackingHelper.AdType.NATIVE, position, "daily_cap")
            return false
        }
        if (!blocking.canShowAllAd()) {
            AppLog.ads.i("跳过广告$action 类型=native 位置=$position 原因=总闸拦截")
            if (action == "展示") SpTrackEvents.adBlocked(AdTrackingHelper.AdType.NATIVE, position, "disabled")
            return false
        }
        return true
    }

    /** Apollo `tryLoadAd(position, index)`。 */
    private fun tryLoadAd(position: String, index: Int, scene: String = ""): Boolean {
        val list = config.adInfoList(position) ?: run {
            AppLog.ads.i("跳过广告加载 类型=native 位置=$position 原因=没有广告位配置")
            return false
        }
        if (index > list.size - 1) {
            isLoading = false
            AppLog.ads.w("广告加载失败 类型=native 位置=$position 原因=广告单元已用完")
            return false
        }
        val adInfo = list[index]
        loadIndexOfAdPositionMap[position] = index
        loadAd(position, adInfo, scene)
        return true
    }

    /** Apollo `loadAD(position, adInfo)`：AdLoader 拉原生，成功入缓存，失败换下一个单元。 */
    private fun loadAd(position: String, adInfo: SpAdInfo, scene: String = "") {
        if (adInfo.adUnitId.isEmpty()) {
            AppLog.ads.w("跳过广告加载 类型=native 位置=$position 原因=广告单元为空")
            tryLoadAd(position, (loadIndexOfAdPositionMap[position] ?: 0) + 1, scene)
            return
        }
        val sessionId = SpAdSession.newId()

        val life = AdTrackingHelper.begin(AdTrackingHelper.AdType.NATIVE, position, adInfo.adUnitId, scene)
        AppLog.ads.i("开始加载广告 类型=native 位置=$position 单元=${adInfo.adUnitId}")
        AdLoader.Builder(appContext, adInfo.adUnitId)
            .forNativeAd { nativeAd ->
                isLoading = false
                AdTrackingHelper.filled(life, nativeAd.responseInfo)
                AdTrackingHelper.remember(sessionId, life)
                SpAdCache.putNativeAd(SpCachedAd(nativeAd, System.currentTimeMillis(), sessionId))
                AppLog.ads.i("广告加载成功 类型=native 位置=$position 单元=${adInfo.adUnitId}")
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    AppLog.ads.w("广告加载失败 类型=native 位置=$position 单元=${adInfo.adUnitId} 错误码=${error.code} 错误信息=${error.message}")
                    AdTrackingHelper.failed(life, error.code, error.message)
                    val current = loadIndexOfAdPositionMap[position] ?: 0
                    tryLoadAd(position, current + 1, scene)
                }

                override fun onAdImpression() {
                    AppLog.ads.i("广告已曝光 类型=native 位置=$position")
                    AdTrackingHelper.impression(life)
                }

                override fun onAdClicked() {
                    SpAdSession.markAdClicked()
                    AppLog.ads.i("广告已点击 类型=native 位置=$position")
                    AdTrackingHelper.click(life)
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

    /** Apollo `showAd(position, bean, style)`：绑定数据 + 关闭键 + 记数。 */
    private fun showAd(position: String, cached: SpCachedAd<NativeAd>, style: SpNativeAdStyle) {
        adLayoutView?.visibility = View.GONE
        if (!canShow(position, "展示")) return
        AppLog.ads.i("开始展示广告 类型=native 位置=$position")
        shownSessionId = cached.sessionId
        currentNativeAd = cached.ad
        cached.ad.setOnPaidEventListener { value ->
            SpAdIncomeReport.sendAdIncome(
                appContext,
                value,
                cached.ad.responseInfo,
                AdTrackingHelper.AdType.NATIVE,
                position,
                AdTrackingHelper.recall(cached.sessionId),
            )
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

        applyCloseButton(close)
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

        applyCloseButton(close)
        adView.setNativeAd(nativeAd)
        return view
    }

    /**
     * 关闭键判据（`displayBigNativeAd` / `displayNativeAd`）：
     * `switch_open != 1` → 关闭键隐藏；否则关闭键可见，点击只隐藏广告并记录关闭。
     */
    private fun applyCloseButton(close: ImageView) {
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
            shownSessionId?.let { id ->
                AdTrackingHelper.recall(id)?.let {
                    AppLog.ads.i("广告已关闭 类型=native 位置=${it.posId}")
                    AdTrackingHelper.close(it)
                }
                AdTrackingHelper.forget(id)
            }
            shownSessionId = null
            adLayoutView?.visibility = View.GONE
        }
    }

    private fun assetVisibility(asset: Any?): Int = if (asset == null) View.INVISIBLE else View.VISIBLE

    /** Apollo `release()`。 */
    fun release() {
        pollJob?.cancel()
        shownSessionId?.let { id ->
            AdTrackingHelper.recall(id)?.let { life ->
                if (life.showStartMs > 0L) {
                    AppLog.ads.i("广告已关闭 类型=native 位置=${life.posId}")
                    AdTrackingHelper.close(life)
                }
            }
            AdTrackingHelper.forget(id)
        }
        shownSessionId = null
        adLayoutView?.removeAllViews()
        currentNativeAd?.destroy()
        currentNativeAd = null
    }

}
