package com.zppko.pdfplus.nor.ads.mob

// 迁移适配：com.buncead.common.pdfpro.my.BaseActivity → android.app.Activity
import android.app.Activity
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback


class OpenAdsLoad(
    setting: AdsSetting
) : BaseAdsLoad(setting) {
    //开屏广告
    private var openAd: AppOpenAd? = null

    override fun childLoad() {
        OpenRequest().start(setting.zId,object : AppOpenAdLoadCallback() {
            override fun onAdLoaded(p0: AppOpenAd) {
                openAd = p0
                zmyAdsLoadSSSS()
            }

            override fun onAdFailedToLoad(p0: LoadAdError) {
                zmyAdsLoadFFFF(p0.message)
            }
        })

//        AppOpenAd.load(
//            Zenlytic.ii,
//            setting.zId,
//            AdRequest.Builder().build(),
//            object : AppOpenAdLoadCallback() {
//                override fun onAdLoaded(p0: AppOpenAd) {
//                    openAd = p0
//                    zmyAdsLoadSSSS()
//                }
//
//                override fun onAdFailedToLoad(p0: LoadAdError) {
//                    zmyAdsLoadFFFF(p0.message)
//                }
//            }
//        )
    }

    override fun childShow(act: Activity, event: String, whenOnVisible:Boolean, finish: () -> Unit) {
        openAd?.setOnPaidEventListener {
            val adSourceName = openAd?.responseInfo?.loadedAdapterResponseInfo?.adSourceName
//            sendValueToServer(it, adSourceName)
            OpenRequest().sendValueToServer(this,it,adSourceName)
        }
        openAd?.let {
            it.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    zDismiss(act,whenOnVisible, finish)
                    openAd = null
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    zDismiss(act,whenOnVisible, finish)
                    openAd = null
                }
                override fun onAdImpression() {
                }
                override fun onAdClicked() {
                }
            }
            it.show(act)
        } ?: zDismiss(act,whenOnVisible, finish)
    }

    override fun zcanUsable(): Boolean {
        return openAd != null
    }

    override fun zAdsDestory() {

    }

}
