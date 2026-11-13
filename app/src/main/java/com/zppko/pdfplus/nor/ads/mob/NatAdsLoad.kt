package com.zppko.pdfplus.nor.ads.mob

import android.widget.FrameLayout
import com.buncead.common.ui.BaseActivity
import com.buncead.common.util.Util
import com.google.android.gms.ads.nativead.NativeAd
import com.mymy.mymy.ads.count.AdCountInter
import com.mymy.mymy.ads.count.AdCountNati
import com.mymy.mymy.ads.poi.TrackAd
import com.mymy.mymy.ads.poi.TrackBean
import com.tradplus.ads.open.interstitial.TPInterstitial
import com.tradplus.ads.open.nativead.TPNative
import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad
import com.zppko.pdfplus.nor.ads.trad.TradAdNative
import com.zppko.pdfplus.nor.ads.trad.TradLoadCallback
import com.zppko.pdfplus.nor.ads.trad.TradShowCallback


class NatAdsLoad(
    setting: AdsSetting
) : BaseAdsLoad(setting) {
    val TAG = "NatAdsLoad"
    val trBean: TrackBean = TrackBean.nati(setting.zPlace, CodeKeyAndEvent.ddd_FIRM_TRADPLUS,setting.zId)

    private var lastPoin=""

    val trad = TradAdNative()
    override fun childLoad() {
        TrackAd.loadStart(trBean)

        val nowAd: TPNative? = trad.getAD()
        if (nowAd==null) {
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

            trad.setShowCallback(object: TradShowCallback() {
                override fun onAdDismissed() {

                }
                override fun onAdFailedToShow() {

                }
                override fun onAdShowed() {
                    AdCountNati.nati_addShowCount()
                    TrackAd.showSuccess(trBean)
                }
                override fun onAdClicked() {
                    AdCountNati.nati_addClickCount()
                    TrackAd.showClick(trBean)
                }
            })

        }else{
            Util.eee { "use ad obj, start load=${setting.zId}" }
            nowAd.loadAd()
        }
    }

    fun getAdView(point:String):TPNative?{
        lastPoin=point
        val nowAd: TPNative? = trad.getAD()
        return nowAd
    }

    override fun childShow(act: BaseActivity, event: String, container: FrameLayout, whenOnVisible:Boolean, finish: () -> Unit) {

    }

    override fun zcanUsable(): Boolean {
        val nowAd: TPNative? = trad.getAD()
        return  nowAd?.isReady ?: false
    }

    override fun zAdsDestory() {
//        Util.eee { "zAdsDestory()" }
//        val nowAd: TPNative? = trad.getAD()
//        nowAd?.onDestroy()
    }

}