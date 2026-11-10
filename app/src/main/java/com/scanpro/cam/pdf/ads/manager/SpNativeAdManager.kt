package com.scanpro.cam.pdf.ads.manager

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.mymy.mymy.ads.count.AdCountNati
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpAdMobInit
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.ads.SpNatConfig
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.util.AppLog
import com.zppko.pdfplus.nor.ads.ImpressionUtils
import com.zppko.pdfplus.nor.ads.mob.NatAdsLoad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 原生广告接线层。
 * 加载 = PDF260713_p01 池（ImpressionUtils.load + fireAnative），
 * 展示时机/版式/关闭键 = B 原行为（200ms 轮询等待、big/small 版式、switch_open 关闭键）。
 * 埋点对齐 PDF26：无 Shark_* / ad_* 事件；收入由 NatAdsLoad 的 OnPaidEvent 走 Price.qqq。
 */
class SpNativeAdManager internal constructor(
    private val activity: Activity,
    /** 页面提供的容器，Apollo 的 `adLayoutView`。 */
    private val adLayoutView: ViewGroup? = null,
) {

    private val appContext: Context = activity.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main)

    /** 等价 Apollo `RemoteConfig.INSTANCE.getNatConfig()`。缺省与 `DefaultConfig.ac_nat_config` 相同。 */
    private val natConfig: SpNatConfig get() = SpAdsHub.getOrNull()?.nat() ?: SpNatConfig()
    private val blocking: SpBlockingLogic get() = SpBlockingLogic.get(appContext)

    private var currentNativeAd: NativeAd? = null
    /** 当前展示的原生加载器，用于关闭键上报 ad_close。 */
    private var currentLoader: NatAdsLoad? = null
    private var pollJob: Job? = null
    private var isReleased = false
    private var displayTarget: Pair<String, SpNativeAdStyle>? = null
    private var renderPending = false
    private val lifecycleOwner = activity as? LifecycleOwner
    private var observedTree: ViewTreeObserver? = null
    private val visibilityListener = ViewTreeObserver.OnPreDrawListener {
        renderIfVisible()
        true
    }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            observeVisibility()
        }

        override fun onViewDetachedFromWindow(view: View) {
            stopObservingVisibility()
        }
    }
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            renderIfVisible()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            release()
        }
    }

    init {
        lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
        adLayoutView?.addOnAttachStateChangeListener(attachListener)
        if (adLayoutView?.isAttachedToWindow == true) observeVisibility()
    }

    private fun observeVisibility() {
        stopObservingVisibility()
        observedTree = adLayoutView?.viewTreeObserver?.takeIf { it.isAlive }?.also {
            it.addOnPreDrawListener(visibilityListener)
        }
    }

    private fun stopObservingVisibility() {
        observedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(visibilityListener)
        observedTree = null
    }

    /** A late fill stays bound to this page and is consumed only while its container is visible. */
    private fun renderIfVisible() {
        if (isReleased || !renderPending || activity.isFinishing || activity.isDestroyed) return
        val (position, style) = displayTarget ?: return
        val host = adLayoutView ?: return
        if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == false) return
        if (!host.isAttachedToWindow || !host.isShown || host.windowVisibility != View.VISIBLE) return
        if (SpAdsHub.getOrNull()?.isConsentFlowDone() != true) return
        val loader = ImpressionUtils.fireAnative(position) ?: return
        val nativeAd = loader.getAdView(position) ?: return
        renderPending = false
        pollJob?.cancel()
        showAd(nativeAd, style, loader)
    }

    private companion object {
        private const val POLL_MS = 200L
    }

    /**
     * 首页/设置页调用：**尽量现在就出广告**。有就绪立即显示；没有则加载并轮询等待（200ms）。
     * Apollo `checkLoadAndShowAd(position, style)`。
     */
    fun checkLoadAndShowAd(position: String, style: SpNativeAdStyle) {
        if (adLayoutView == null) {
            // 没有容器就没有展示位，先发请求只会造成「有填充、无展示」。
            AppLog.ads.i("跳过广告加载 类型=native 位置=$position 原因=没有广告容器")
            return
        }
        if (isReleased) return
        displayTarget = position to style
        renderPending = true
        if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) {
            AppLog.ads.i("跳过广告展示 类型=native 位置=$position 原因=广告SDK未初始化")
            return
        }
        if (!canShow(position, "展示")) return
        ImpressionUtils.load(position)
        renderIfVisible()
        if (!renderPending) return
        if (ImpressionUtils.loadSuccess(position)) {
            renderIfVisible()
            return
        }
        startCheckShowAd(position)
    }

    /**
     * 每 200ms 看一次池是否就绪。池（PDF26）没有加载完成回调，晚到填充只能靠轮询/页面
     * 可见性事件触发渲染；因此不再像旧实现那样 3 秒截断，持续轮询直到渲染完成或页面销毁。
     */
    private fun startCheckShowAd(position: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (!isReleased && renderPending) {
                delay(POLL_MS)
                if (ImpressionUtils.loadSuccess(position)) {
                    renderIfVisible()
                    return@launch
                }
            }
        }
    }

    /** Apollo `tryCacheAd(position)`：只预热，不显示。 */
    fun tryCacheAd(position: String) {
        if (isReleased || !canShow(position, "加载")) return
        ImpressionUtils.load(position)
    }

    private fun canShow(position: String, action: String): Boolean {
        if (isReleased || activity.isFinishing || activity.isDestroyed) return false
        if (SpAdsHub.getOrNull()?.isConsentFlowDone() != true) return false
        if (blocking.isShowPartialAdsOnly(position)) {
            AppLog.ads.i("跳过广告$action 类型=native 位置=$position 原因=只展示部分广告")
            return false
        }
        if (blocking.isVPNBlock()) {
            AppLog.ads.i("跳过广告$action 类型=native 位置=$position 原因=VPN拦截")
            return false
        }
        if (!blocking.canShowAllAd()) {
            AppLog.ads.i("跳过广告$action 类型=native 位置=$position 原因=总闸拦截")
            return false
        }
        if (!AdCountNati.natiCanShow()) {
            AppLog.ads.i("跳过广告$action 类型=native 位置=$position 原因=达到当日展示上限")
            return false
        }
        return true
    }

    /** 绑定数据 + 关闭键 + 记数（B 原渲染层）。 */
    private fun showAd(nativeAd: NativeAd, style: SpNativeAdStyle, loader: NatAdsLoad? = null) {
        currentNativeAd?.destroy()
        adLayoutView?.visibility = View.GONE
        AppLog.ads.i("开始展示广告 类型=native 位置=${displayTarget?.first}")
        currentNativeAd = nativeAd
        currentLoader = loader
        loader?.trackNativeImpression()
        AdCountNati.nati_addShowCount()
        bindAndShow(nativeAd, style)
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
     * `switch_open != 1` → 关闭键隐藏；否则关闭键可见，点击只隐藏广告。
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
            AppLog.ads.i("广告已关闭 类型=native")
            currentLoader?.trackNativeClose()
            currentLoader = null
            adLayoutView?.visibility = View.GONE
        }
    }

    private fun assetVisibility(asset: Any?): Int = if (asset == null) View.INVISIBLE else View.VISIBLE

    /** Apollo `release()`。 */
    fun release() {
        if (isReleased) return
        isReleased = true
        renderPending = false
        displayTarget = null
        pollJob?.cancel()
        scope.cancel()
        stopObservingVisibility()
        adLayoutView?.removeOnAttachStateChangeListener(attachListener)
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        adLayoutView?.removeAllViews()
        currentNativeAd?.destroy()
        currentNativeAd = null
        currentLoader = null
    }

}