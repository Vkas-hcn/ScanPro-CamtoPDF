package com.zppko.pdfplus.nor.ads.mob

import android.widget.FrameLayout
import com.buncead.common.ui.BaseActivity
import com.buncead.common.util.Util
import com.facebook.ads.InterstitialAd
import com.mymy.mymy.ads.count.AdCountInter
import com.mymy.mymy.ads.poi.TrackAd
import com.mymy.mymy.ads.poi.TrackBean

import com.tradplus.ads.open.interstitial.TPInterstitial
import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad
import com.zppko.pdfplus.nor.ads.trad.TradAdInter
import com.zppko.pdfplus.nor.ads.trad.TradLoadCallback
import com.zppko.pdfplus.nor.ads.trad.TradShowCallback


class IntAdsLoad(
    setting: AdsSetting
) : BaseAdsLoad(setting) {
    val TAG = "IntAdsLoad"
    val trBean: TrackBean = TrackBean.inter(setting.zPlace, CodeKeyAndEvent.ddd_FIRM_TRADPLUS,setting.zId)

    val trad = TradAdInter()
    override fun childLoad() {
        TrackAd.loadStart(trBean)

        val nowAd: TPInterstitial? = trad.getAD()
        if (nowAd==null){
            Util.eee { "$TAG no ad obj, first init, start load=${setting.zId}" }
            trad.initSplashAd(setting.zId,object : TradLoadCallback() {
                override fun onAdLoaded() {
                    zmyAdsLoadSSSS()

                    trBean.adSource = "${trad.mTPAdInfo?.adSourceName}"
                    TrackAd.loadSuccess(trBean)
                }
                override fun onAdFailedToLoad(code: Int, message: String) {
                    TrackAd.loadFailed(trBean, "${code}", message)

                    zmyAdsLoadFFFF("")
                }
            })
        }else{
            Util.eee { "use ad obj, start load=${setting.zId}" }
            nowAd.loadAd()
        }
    }

    override fun childShow(act: BaseActivity, event: String, container: FrameLayout, whenOnVisible:Boolean, finish: () -> Unit) {
//        interAd?.setOnPaidEventListener {
//            val adSourceName = interAd?.responseInfo?.loadedAdapterResponseInfo?.adSourceName
//            sendValueToServer(it, adSourceName)
//        }

        val nowAd: TPInterstitial? = trad.getAD()
        if (nowAd == null) {
            zDismiss(act,whenOnVisible, finish)
        }else{
            trad.setShowCallback(object: TradShowCallback() {
                override fun onAdDismissed() {
                    TrackAd.showDismiss(trBean)

                    container.removeAllViews()
                    zDismiss(act,whenOnVisible, finish)

                    if (zcanUsable()){
                        Util.eee { "$TAG ad close, ad isRead=true, no load" }
                    }else{
                        Util.eee { "$TAG ad close, ad isRead=false, start load=${setting.zId}" }
                        nowAd.loadAd()
                    }
                }
                override fun onAdFailedToShow() { // topon open没有show失败的call，下面代码没有意义
                    zDismiss(act,whenOnVisible, finish)

                }
                override fun onAdShowed() {
                    AdCountInter.inter_addShowCount()
                    TrackAd.showSuccess(trBean)

                    if (zcanUsable()){
                        Util.eee { "$TAG ad show, ad isRead=true, no load" }
                    }else{
                        Util.eee { "$TAG ad show, ad isRead=false, start load=${setting.zId}" }
                        nowAd.loadAd()
                    }
                }
                override fun onAdClicked() {
                    AdCountInter.inter_addClickCount()
                    TrackAd.showClick(trBean)
                }
            })

            nowAd.showAd(act,"sceneId")
        }
    }

    override fun zcanUsable(): Boolean {
        val nowAd: TPInterstitial? = trad.getAD()
        return  nowAd?.isReady ?: false
    }

    override fun zAdsDestory() {

    }

    override fun getEcpm(): String {
        return trad.mTPAdInfo?.ecpm ?: "0.0"
    }

}