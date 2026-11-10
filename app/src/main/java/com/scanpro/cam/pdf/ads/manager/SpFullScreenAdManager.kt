package com.scanpro.cam.pdf.ads.manager

import android.app.Activity
import android.content.Context
import com.mymy.mymy.ads.count.AdCountInter
import com.scanpro.cam.pdf.ads.SpAdMobInit
import com.scanpro.cam.pdf.ads.SpAdShowGate
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.util.AppLog
import com.zppko.pdfplus.nor.ads.ImpressionUtils

/**
 * 全屏广告（开屏 + 插屏）的加载/展示接线层。
 * 加载内核 = PDF260713_p01 的 ImpressionUtils 池（每位置一个 Loader，TTL + 失败降级链）。
 * 展示对齐 PDF26：就绪后立即 show，不再有 500ms 遮罩。
 * 开屏按 PDF26 去掉频控与展示互斥；插屏仍保留 AdCountInter + SpAdShowGate。
 * 收入走 OnPaidEventListener -> Price.qqq。
 */
class SpFullScreenAdManager(
    context: Context,
    /** 只做预加载时传 null；要展示必须非空。 */
    private val activity: Activity?,
) {

    private val appContext: Context = context.applicationContext
    private val blocking: SpBlockingLogic get() = SpBlockingLogic.get(appContext)

    /** 该位置配置的广告类型（op/int/...），用于分类型频控计数。 */
    private fun adTypeOf(position: String): String =
        SpAdsHub.getOrNull()?.config()?.adInfoList(position)?.firstOrNull()?.adType ?: ""

    /** 规范 §2.3 的 `ad_type`（open/interstitial/native/banner），用于 ad_blocked 上报。 */
    private fun specAdType(position: String): String = when (adTypeOf(position)) {
        "op" -> AdTrackingHelper.AdType.OPEN
        "int" -> AdTrackingHelper.AdType.INTERSTITIAL
        "nat" -> AdTrackingHelper.AdType.NATIVE
        "banner" -> AdTrackingHelper.AdType.BANNER
        else -> adTypeOf(position)
    }

    /** 开屏按 PDF26 不做频控；插屏保留 AdCountInter。 */
    private fun adCountCanShow(position: String): Boolean = when (adTypeOf(position)) {
        "int" -> AdCountInter.interCanShow()
        else -> true
    }

    private fun countShow(position: String) {
        if (adTypeOf(position) == "int") AdCountInter.inter_addShowCount()
    }

    /**
     * 预加载。B 原时序的触发点不变（进页/开屏/退后台/配置就绪），内部改为池 load。
     */
    fun checkAndLoadAd(position: String, scene: String = position, index: Int = 0) {
//        if (SpAdsHub.getOrNull()?.isConsentFlowDone() != true) {
//            AppLog.ads.i("跳过广告加载 位置=$position 原因=UMP流程未完成")
//            return
//        }
        if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=广告SDK未初始化")
            return
        }
        if (blocking.isShowPartialAdsOnly(position)) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=只展示部分广告")
            SpTrackEvents.adBlocked(specAdType(position), position, "disabled")
            return
        }
        if (blocking.isVPNBlock()) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=VPN拦截")
            SpTrackEvents.adBlocked(specAdType(position), position, "disabled")
            return
        }
        if (!blocking.canShowAllAd()) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=总闸拦截")
            SpTrackEvents.adBlocked(specAdType(position), position, "disabled")
            return
        }
        if (!adCountCanShow(position)) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=达到当日展示上限")
            SpTrackEvents.adBlocked(specAdType(position), position, "daily_cap")
            return
        }
        ImpressionUtils.load(position)
    }

    /** 该位置是否已有可用（未过期）的广告。 */
    fun hasReadyAd(position: String): Boolean = ImpressionUtils.loadSuccess(position)

    /**
     * 展示：gating -> (插屏)互斥 -> 就绪检查 -> 池 show。
     * 开屏按 PDF26 不做展示互斥；插屏仍走 SpAdShowGate。
     * onResult(showed, dismissed)：展示失败(false,false)；展示成功且已关闭(true,true)。
     */
    fun showFullScreenAd(
        position: String,
        scene: String,
        whenOnVisible: Boolean = false,
        onResult: (showed: Boolean, dismissed: Boolean) -> Unit,
    ) {
        val host = activity
        if (host == null || host.isFinishing || host.isDestroyed) {
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=页面已销毁")
            onResult(false, false)
            return
        }
        val adType = adTypeOf(position)
        val useShowGate = adType != "op"
        fun releaseShowGate() {
            if (useShowGate) SpAdShowGate.exit()
        }
        if (SpAdsHub.getOrNull()?.isConsentFlowDone() != true ||
            blocking.isShowPartialAdsOnly(position) ||
            blocking.isVPNBlock() || !blocking.canShowAllAd()
        ) {
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=总闸拦截")
            onResult(false, false)
            return
        }
        if (useShowGate && !SpAdShowGate.tryEnter()) {
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=已有广告正在展示")
            SpTrackEvents.adBlocked(specAdType(position), position, "another_fullscreen")
            onResult(false, false)
            return
        }
        if (!ImpressionUtils.loadSuccess(position)) {
            releaseShowGate()
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=广告尚未就绪")
            onResult(false, false)
            return
        }
        if (useShowGate) countShow(position)
        if (host.isFinishing || host.isDestroyed) {
            releaseShowGate()
            onResult(false, false)
            return
        }
        AppLog.ads.i("开始展示广告 位置=$scene")
        // PDF26：就绪即 show，无 500ms 遮罩；开屏 whenOnVisible=true，等 onResume 再回调。
        ImpressionUtils.show(host, position, scene, whenOnVisible = whenOnVisible) {
            releaseShowGate()
            onResult(true, true)
        }
    }

    /** B 原 `showInterstitialAdNoWait`：没缓存就立刻回调，不等待。 */
    fun showInterstitialAdNoWait(position: String, onDone: () -> Unit) {
        showFullScreenAd(position, position) { _, _ -> onDone() }
    }

    /** 开屏展示：失败立刻放行，成功等关闭；PDF26 语义 whenOnVisible=true。 */
    fun showOpenAd(position: String, scene: String, onDone: () -> Unit) {
        showFullScreenAd(position, scene, whenOnVisible = true) { showed, dismissed ->
            if (!showed || dismissed) onDone()
        }
    }

    /** Apollo `release()`：接线层无本地状态，保留空实现以兼容调用方。 */
    fun release() {
    }
}