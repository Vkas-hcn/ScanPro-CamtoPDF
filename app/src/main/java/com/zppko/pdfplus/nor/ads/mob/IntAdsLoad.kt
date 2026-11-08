package com.zppko.pdfplus.nor.ads.mob

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad


/**
 * 插屏。骨架照 SmartPdfScan IntAdsLoad，SDK 调用照 PDF26 OpenAdsLoad 的 AdMob 模式：
 * 加载成功/失败只走 zmyAdsLoadSSSS/FFFF（含降级链），展示走 FullScreenContentCallback，
 * 收入走 OnPaidEventListener -> sendValueToServer（对齐 PDF26：无 TrackAd/AdCount 调用点）。
 */
class IntAdsLoad(
    setting: AdsSetting
) : BaseAdsLoad(setting) {

    private var interAd: InterstitialAd? = null

    override fun childLoad() {
        if (setting.zId.isEmpty()) {
            // PDF26 OpenRequest 对空 ID 静默跳过；此处显式失败以复位加载标志并触发降级链。
            zmyAdsLoadFFFF("")
            return
        }
        InterstitialAd.load(
            Zppkoo.ii,
            setting.zId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interAd = ad
                    zmyAdsLoadSSSS()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    zmyAdsLoadFFFF(error.message)
                }
            },
        )
    }

    override fun childShow(act: Activity, event: String, whenOnVisible: Boolean, finish: () -> Unit) {
        interAd?.setOnPaidEventListener {
            val adSourceName = interAd?.responseInfo?.adapterResponses?.firstOrNull()?.adSourceName
            OpenRequest().sendValueToServer(this, it, adSourceName)
        }
        interAd?.let {
            it.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    zDismiss(act, whenOnVisible, finish)
                    interAd = null
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    zDismiss(act, whenOnVisible, finish)
                    interAd = null
                }
            }
            it.show(act)
        } ?: zDismiss(act, whenOnVisible, finish)
    }

    override fun zcanUsable(): Boolean {
        return interAd != null
    }

    override fun zAdsDestory() {

    }

}