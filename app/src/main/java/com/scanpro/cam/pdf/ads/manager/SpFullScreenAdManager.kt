package com.scanpro.cam.pdf.ads.manager

import android.app.Activity
import android.content.Context
import com.mymy.mymy.ads.count.AdCountInter
import com.mymy.mymy.ads.count.AdCountOpen
import com.scanpro.cam.pdf.ads.SpAdMobInit
import com.scanpro.cam.pdf.ads.SpAdShowGate
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.ads.SpFullScreenMask
import com.scanpro.cam.pdf.ads.SpUmpGate
import com.scanpro.cam.pdf.util.AppLog
import com.zppko.pdfplus.nor.ads.ImpressionUtils

/**
 * 全屏广告（开屏 + 插屏）的加载/展示接线层。
 * 加载内核 = PDF260713_p01 的 ImpressionUtils 池（每位置一个 Loader，TTL + 失败降级链），
 * 展示时序 = B 原行为（500ms 遮罩、SpAdShowGate 互斥、60 秒节流在 SpPageAdTriggers）。
 * 埋点对齐 PDF26：不再发 Shark_* 事件；收入走 OnPaidEventListener -> Price.qqq。
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

    private fun adCountCanShow(position: String): Boolean = when (adTypeOf(position)) {
        "op" -> AdCountOpen.openCanShow()
        "int" -> AdCountInter.interCanShow()
        else -> true
    }

    private fun countShow(position: String) {
        when (adTypeOf(position)) {
            "op" -> AdCountOpen.open_addShowCount()
            "int" -> AdCountInter.inter_addShowCount()
        }
    }

    /**
     * 预加载。B 原时序的触发点不变（进页/开屏/退后台/配置就绪），内部改为池 load。
     */
    fun checkAndLoadAd(position: String, scene: String = position, index: Int = 0) {
        if (!SpUmpGate.canRequestAds(appContext)) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=用户同意尚未就绪")
            return
        }
        if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) {
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
        if (!blocking.canShowAllAd()) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=总闸拦截")
            return
        }
        if (!adCountCanShow(position)) {
            AppLog.ads.i("跳过广告加载 位置=$position 原因=达到当日展示上限")
            return
        }
        ImpressionUtils.load(position)
    }

    /** 该位置是否已有可用（未过期）的广告。 */
    fun hasReadyAd(position: String): Boolean = ImpressionUtils.loadSuccess(position)

    /**
     * 展示：gating -> 互斥 -> 就绪检查 -> 500ms 遮罩 -> 池 show。
     * onResult(showed, dismissed)：展示失败(false,false)；展示成功且已关闭(true,true)。
     */
    fun showFullScreenAd(position: String, scene: String, onResult: (showed: Boolean, dismissed: Boolean) -> Unit) {
        val host = activity
        if (host == null || host.isFinishing || host.isDestroyed) {
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=页面已销毁")
            onResult(false, false)
            return
        }
        if (!SpUmpGate.canRequestAds(appContext) || blocking.isShowPartialAdsOnly(position) ||
            blocking.isVPNBlock() || !blocking.canShowAllAd()
        ) {
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=总闸拦截")
            onResult(false, false)
            return
        }
        if (!SpAdShowGate.tryEnter()) {
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=已有广告正在展示")
            onResult(false, false)
            return
        }
        if (!ImpressionUtils.loadSuccess(position)) {
            SpAdShowGate.exit()
            AppLog.ads.i("跳过广告展示 位置=$scene 原因=广告尚未就绪")
            onResult(false, false)
            return
        }
        countShow(position)
        AppLog.ads.i("开始展示广告 位置=$scene")
        SpFullScreenMask.show(host) {
            if (host.isFinishing || host.isDestroyed) {
                SpAdShowGate.exit()
                onResult(false, false)
                return@show
            }
            if (!ImpressionUtils.loadSuccess(position)) {
                SpAdShowGate.exit()
                onResult(false, false)
                return@show
            }
            // whenOnVisible=false：关闭/失败即刻回调（B 原语义，PDF26 池内 zDismiss 分支）
            ImpressionUtils.show(host, position, scene, whenOnVisible = false) {
                SpAdShowGate.exit()
                onResult(true, true)
            }
        }
    }

    /** B 原 `showInterstitialAdNoWait`：没缓存就立刻回调，不等待。 */
    fun showInterstitialAdNoWait(position: String, onDone: () -> Unit) {
        showFullScreenAd(position, position) { _, _ -> onDone() }
    }

    /** 开屏展示：失败立刻放行，成功等关闭（B 原 `presentLaunchAd` 语义）。 */
    fun showOpenAd(position: String, scene: String, onDone: () -> Unit) {
        showFullScreenAd(position, scene) { showed, dismissed ->
            if (!showed || dismissed) onDone()
        }
    }

    /** Apollo `release()`：接线层无本地状态，保留空实现以兼容调用方。 */
    fun release() {
    }
}