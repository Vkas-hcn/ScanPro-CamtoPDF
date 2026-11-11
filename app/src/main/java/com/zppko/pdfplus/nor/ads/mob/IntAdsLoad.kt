package com.zppko.pdfplus.nor.ads.mob

import android.app.Activity
import com.scanpro.cam.pdf.ads.SpAdSession
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad
import com.zppko.pdfplus.nor.ads.trad.TradAdInter
import com.zppko.pdfplus.nor.ads.trad.TradLoadCallback
import com.zppko.pdfplus.nor.ads.trad.TradShowCallback

/**
 * 插屏。骨架照 SmartPdfScan_luow IntAdsLoad（TradPlus TPInterstitial），
 * 埋点对齐 B：trackLife + AdTrackingHelper。展示计数由 SpFullScreenAdManager 在展示前
 * 调 AdCountInter.inter_addShowCount() 完成，这里不再重复计数。
 */
class IntAdsLoad(
    setting: AdsSetting
) : BaseAdsLoad(setting) {

    private val trad = TradAdInter()

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
        val nowAd = trad.getAD() ?: return zDismiss(act, whenOnVisible, finish)
        trad.setShowCallback(object : TradShowCallback() {
            override fun onAdShowed() {
                // 展示起点已由 BaseAdsLoad.zShoweee 记录；计数由 manager 完成。
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
        nowAd.showAd(act, "sceneId")
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
