package com.zppko.pdfplus.nor.ads.mob

import android.app.Activity
import android.view.ViewGroup
import com.scanpro.cam.pdf.ads.SpAdSession
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad
import com.zppko.pdfplus.nor.ads.trad.TradAdOpen
import com.zppko.pdfplus.nor.ads.trad.TradLoadCallback
import com.zppko.pdfplus.nor.ads.trad.TradShowCallback

/**
 * 开屏。骨架照 SmartPdfScan_luow OpenAdsLoad（TradPlus TPSplash），
 * 埋点对齐 B：trackLife + AdTrackingHelper（无 A 的 TrackAd/AdCountOpen）。
 * 展示计数：B 开屏不做频控，仅记录埋点；收益统一走全局 GlobalImpressionListener。
 */
class OpenAdsLoad(
    setting: AdsSetting
) : BaseAdsLoad(setting) {

    private val trad = TradAdOpen()

    override fun childLoad() {
        if (setting.zId.isEmpty()) {
            zmyAdsLoadFFFF("配置里 placementId 为空")
            return
        }
        trad.initSplashAd(setting.zId, object : TradLoadCallback() {
            override fun onAdLoaded() {
                zmyAdsLoadSSSS(trad.mTPAdInfo?.adSourceName)
            }

            override fun onAdFailedToLoad(code: Int, message: String) {
                zmyAdsLoadFFFF("code=$code $message")
            }
        })
    }

    override fun childShow(act: Activity, event: String, whenOnVisible: Boolean, finish: () -> Unit) {
        val splashAd = trad.getAD() ?: return zDismiss(act, whenOnVisible, finish)
        trad.setShowCallback(object : TradShowCallback() {
            override fun onAdShowed() {
                // 展示起点已由 BaseAdsLoad.zShoweee 记录（trackLife），无需重复。
            }

            override fun onAdClicked() {
                SpAdSession.markAdClicked()
                trackLife?.let { AdTrackingHelper.click(it) }
            }

            override fun onAdDismissed() {
                zDismiss(act, whenOnVisible, finish)
            }

            override fun onAdFailedToShow() {
                zDismiss(act, whenOnVisible, finish)
            }
        })
        // TradPlus 开屏需要容器：用 Activity 内容根视图作为全屏容器（B 无专用 adCom）。
        val container = act.findViewById<ViewGroup>(android.R.id.content)
        splashAd.showAd(act, container, "splash_eve")
    }

    override fun zcanUsable(): Boolean {
        return trad.getAD()?.isReady == true
    }

    override fun getEcpm(): String {
        return trad.mTPAdInfo?.ecpm ?: "0.0"
    }

    override fun zAdsDestory() {
    }
}
