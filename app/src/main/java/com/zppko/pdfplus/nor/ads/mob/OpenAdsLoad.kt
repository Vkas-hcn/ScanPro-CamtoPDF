package com.zppko.pdfplus.nor.ads.mob

import android.widget.FrameLayout
import com.buncead.common.ui.BaseActivity
import com.buncead.common.util.Util
import com.mymy.mymy.ads.count.AdCountInter
import com.mymy.mymy.ads.count.AdCountOpen
import com.mymy.mymy.ads.poi.TrackAd
import com.mymy.mymy.ads.poi.TrackBean
import com.tradplus.ads.open.splash.TPSplash
import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad
import com.zppko.pdfplus.nor.ads.trad.TradAdOpen
import com.zppko.pdfplus.nor.ads.trad.TradLoadCallback
import com.zppko.pdfplus.nor.ads.trad.TradShowCallback


class OpenAdsLoad(
    setting: AdsSetting
) : BaseAdsLoad(setting) {
    val TAG = "OpenAdsLoad"
    val trBean: TrackBean = TrackBean.open(setting.zPlace, CodeKeyAndEvent.ddd_FIRM_TRADPLUS,setting.zId)

    val trad = TradAdOpen()
    override fun childLoad() {
        TrackAd.loadStart(trBean)

        val splashAd: TPSplash? = trad.getAD()
        if (splashAd==null){
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
            splashAd.loadAd(null)
        }

    }

    override fun childShow(act: BaseActivity, event: String, container: FrameLayout, whenOnVisible:Boolean, finish: () -> Unit) {
//        openAd?.setOnPaidEventListener {
//            val adSourceName = openAd?.responseInfo?.loadedAdapterResponseInfo?.adSourceName
////            sendValueToServer(it, adSourceName)
//            OpenRequest().sendValueToServer(this,it,adSourceName)
//        }

        val splashAd: TPSplash? = trad.getAD()
        if (splashAd == null) {
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
                        splashAd.loadAd(null)
                    }
                }
                override fun onAdFailedToShow() { // topon open没有show失败的call，下面代码没有意义
                    zDismiss(act,whenOnVisible, finish)

                }
                override fun onAdShowed() {
                    AdCountOpen.open_addShowCount()
                    TrackAd.showSuccess(trBean)

                    if (zcanUsable()){
                        Util.eee { "$TAG ad show, ad isRead=true, no load" }
                    }else{
                        Util.eee { "$TAG ad show, ad isRead=false, start load=${setting.zId}" }
                        splashAd.loadAd(null)
                    }
                }
                override fun onAdClicked() {
                    AdCountOpen.open_addClickCount()
                    TrackAd.showClick(trBean)

                }
            })

            splashAd.showAd(act,container,"splash_eve")
        }
    }

    override fun zcanUsable(): Boolean {
        val splashAd: TPSplash? = trad.getAD()
        return  splashAd?.isReady ?: false
    }

    override fun zAdsDestory() {

    }

    override fun getEcpm(): String {
        return trad.mTPAdInfo?.ecpm ?: "0.0"
    }
}