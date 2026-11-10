package com.scanpro.cam.pdf.ads.manager

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.mymy.mymy.ads.count.AdCountNati
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpAdMobInit
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.ads.SpNatConfig
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.util.AppLog
import com.tradplus.ads.base.adapter.nativead.TPNativeAdView
import com.tradplus.ads.base.common.TPImageLoader
import com.tradplus.ads.open.nativead.TPNative
import com.tradplus.ads.open.nativead.TPNativeAdRender
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
 * 展示时机/关闭键 = B 原行为（200ms 轮询等待、switch_open 关闭键）。
 * SDK 由 AdMob GMA 换成 TradPlus TPNative（模板渲染 R.layout.tp_native_ad_list_item，照 A）。
 * 埋点对齐 B：trackNativeImpression/trackNativeClose + AdCountNati 计数；收入走全局 Impression。
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

    private var currentTpNative: TPNative? = null
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
        val tpNative = loader.getAdView(position) ?: return
        renderPending = false
        pollJob?.cancel()
        showAd(tpNative, style, loader)
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

    /** 绑定 TradPlus 模板渲染 + 关闭键 + 记数（B 原渲染层）。 */
    private fun showAd(tpNative: TPNative, style: SpNativeAdStyle, loader: NatAdsLoad? = null) {
        currentTpNative?.onDestroy()
        adLayoutView?.visibility = View.GONE
        AppLog.ads.i("开始展示广告 类型=native 位置=${displayTarget?.first}")
        currentTpNative = tpNative
        currentLoader = loader
        loader?.trackNativeImpression()
        AdCountNati.nati_addShowCount()
        bindAndShow(tpNative, style)
    }

    /**
     * 自渲染：用 B 的 big/small 版式承载 TradPlus 返回的素材（恢复迁移前紧凑样式）。
     * 原 AdMob 走 NativeAdView 绑定；TradPlus 走 TPNativeAdRender 自渲染 + 素材填充。
     */
    private fun bindAndShow(tpNative: TPNative, style: SpNativeAdStyle) {
        val host = adLayoutView ?: return
        host.removeAllViews()
        val layoutRes = when (style) {
            SpNativeAdStyle.big -> R.layout.sp_custom_native_ad_big_layout
            SpNativeAdStyle.small -> R.layout.sp_custom_native_ad_small_layout
        }
        tpNative.showAd(host, object : TPNativeAdRender() {
            override fun createAdLayoutView(): ViewGroup =
                activity.layoutInflater.inflate(layoutRes, host, false) as ViewGroup

            override fun renderAdView(adView: TPNativeAdView): ViewGroup {
                val v = createAdLayoutView()
                val title = v.findViewById<TextView>(R.id.adHeadline)
                val body = v.findViewById<TextView>(R.id.adBody)
                val cta = v.findViewById<TextView>(R.id.adCallToAction)
                val advertiser = v.findViewById<TextView>(R.id.adAdvertiser)
                val icon = v.findViewById<ImageView>(R.id.adAppIcon)

                title?.text = adView.title.orEmpty()
                body?.text = adView.subTitle.orEmpty()
                cta?.text = adView.callToAction.orEmpty()
                advertiser?.text = adView.advertiserName.orEmpty()

                // 图标：优先直接 Drawable，否则按 URL 异步加载。
                if (icon != null) {
                    val drawable = adView.iconImage
                    if (drawable != null) {
                        icon.setImageDrawable(drawable)
                    } else {
                        val url = adView.iconImageUrl
                        if (!url.isNullOrBlank()) TPImageLoader.getInstance().loadImage(icon, url)
                    }
                }

                // 大版：主图/视频容器。
                if (layoutRes == R.layout.sp_custom_native_ad_big_layout) {
                    val mediaHost = v.findViewById<FrameLayout>(R.id.media)
                    val mediaView = adView.mediaView
                    if (mediaHost != null && mediaView != null) {
                        mediaHost.addView(mediaView)
                        getClickViews().add(mediaView)
                    } else if (mediaHost != null) {
                        val main = adView.mainImage
                        if (main != null) {
                            val iv = ImageView(activity).apply {
                                setImageDrawable(main)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                            mediaHost.addView(
                                iv,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }
                    }
                }

                // 注册可点击区域，TradPlus 据此计曝光/点击。
                title?.let { setTitleView(it, true) }
                body?.let { setSubTitleView(it, true) }
                cta?.let { setCallToActionView(it, true) }
                advertiser?.let { setAdvertiserView(it, true) }
                icon?.let { setIconView(it, true) }

                // 素材缺失时隐藏，避免空白占位。
                advertiser?.visibility = if (adView.advertiserName.isNullOrBlank()) View.GONE else View.VISIBLE
                body?.visibility = if (adView.subTitle.isNullOrBlank()) View.GONE else View.VISIBLE
                cta?.visibility = if (adView.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
                icon?.visibility = if (adView.iconImage == null && adView.iconImageUrl.isNullOrBlank()) View.GONE else View.VISIBLE

                // B 的关闭键（switch_open）。
                v.findViewById<ImageView>(R.id.imageClose)?.let { applyCloseButton(it) }

                return v
            }
        }, "")
        host.visibility = View.VISIBLE
    }

    /**
     * 关闭键判据（`displayBigNativeAd` / `displayNativeAd`）：
     * `switch_open != 1` → 关闭键隐藏；否则关闭键可见，点击只隐藏广告。
     */
    private fun applyCloseButton(close: ImageView) {
        val nat = natConfig
        if (nat.switchOpen != 1 || !blocking.isShowNativeAdCloseButton()) {
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
        currentTpNative?.onDestroy()
        currentTpNative = null
        currentLoader = null
    }

}
