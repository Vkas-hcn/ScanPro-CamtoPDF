package com.zppko.pdfplus.nor.ads.mob

import android.app.Activity
import com.scanpro.cam.pdf.ads.SpAdSession
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.tradplus.ads.open.nativead.TPNative
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad
import com.zppko.pdfplus.nor.ads.trad.TradAdNative
import com.zppko.pdfplus.nor.ads.trad.TradLoadCallback
import com.zppko.pdfplus.nor.ads.trad.TradShowCallback

/**
 * 原生。骨架照 SmartPdfScan_luow NatAdsLoad（childLoad + getAdView + childShow 空实现），
 * SDK 用 TradPlus TPNative；渲染由 B 现有 SpNativeAdManager 负责。
 * 展示计数：AdCountNati.nati_addShowCount() 由 SpNativeAdManager 在展示前完成，这里不重复。
 */
class NatAdsLoad(
    setting: AdsSetting
) : BaseAdsLoad(setting) {

    private var lastPoin = ""

    private val trad = TradAdNative()

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

        // A 原样：原生在 load 时就挂 show 回调（展示由页面触发）。
        trad.setShowCallback(object : TradShowCallback() {
            override fun onAdShowed() {
                // 展示起点由 SpNativeAdManager.trackNativeImpression 记录。
            }

            override fun onAdClicked() {
                SpAdSession.markAdClicked()
                trackLife?.let { AdTrackingHelper.click(it) }
            }

            override fun onAdDismissed() {
            }

            override fun onAdFailedToShow() {
            }
        })
    }

    fun getAdView(point: String): TPNative? {
        lastPoin = point
        return trad.getAD()
    }

    override fun childShow(act: Activity, event: String, whenOnVisible: Boolean, finish: () -> Unit) {
        // A 原样：原生由页面拿对象自己挂 UI（B 渲染层负责），此处空实现。
    }

    override fun zcanUsable(): Boolean {
        return trad.getAD()?.isReady == true
    }

    override fun zAdsDestory() {
        trad.getAD()?.onDestroy()
    }
}
