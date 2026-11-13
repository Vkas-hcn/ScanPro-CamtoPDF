package com.zppko.pdfplus.nor.ads.trad

import android.content.Context
import com.scanpro.cam.pdf.CamPdfApp
import com.buncead.common.util.Util
import com.tradplus.ads.base.bean.TPAdError
import com.tradplus.ads.base.bean.TPAdInfo
import com.tradplus.ads.open.interstitial.InterstitialAdListener
import com.tradplus.ads.open.interstitial.TPInterstitial
import com.zppko.pdfplus.nor.tool.LifecycleUtil

class TradAdInter {
    val TAG = "TradAdInter"

    fun getAD(): TPInterstitial? {
        return mTPInterstitial
    }

    private var mTPInterstitial: TPInterstitial? = null
    var mTPAdInfo: TPAdInfo? = null

    fun initSplashAd(placementId: String?, call: TradLoadCallback): TPInterstitial {
        val context: Context = CamPdfApp.app.applicationContext
        mLoadCallback = call

        val interstitialAd: TPInterstitial = TPInterstitial(context, placementId)
        mTPInterstitial = interstitialAd


        //进入广告场景，广告场景ID后台创建
        // 广告场景是用来统计进入广告场景的次数和进入场景后展示广告的次数，所以请在准确的位置调用
        interstitialAd.entryAdScenario("")

//        // 流量分组的时候用到，可以自定义一些app相关的属性，在TradPlus后台根据这些属性来对用户做分组
//        // 设置流量分组有两个维度，一个是全局的，一个是单个广告位的，单个广告位的相同属性会覆盖全局的
//        val customMap = HashMap<String?, String?>()
//        customMap.put("user_gender", "male") //男性
//        customMap.put("user_level", "10") //游戏等级10
//
//        //        SegmentUtils.initCustomMap(customMap);//设置APP维度的规则，对全部placement有效
//        SegmentUtils.initPlacementCustomMap(TestAdUnitId.ENTRY_AD_INTERSTITIAL, customMap) //仅对该广告位有效，会覆盖APP维度设置的规则


        // 监听广告的不同状态
        interstitialAd.setAdListener(object : InterstitialAdListener {
            override fun onAdLoaded(tpAdInfo: TPAdInfo?) {
                Util.eee { "$TAG onAdLoaded: inter, ecpm=${tpAdInfo?.ecpm}, ${tpAdInfo?.adSourceName}" }

                mTPAdInfo = tpAdInfo
                mLoadCallback?.onAdLoaded()
            }
            override fun onAdFailed(tpAdError: TPAdError?) {
                Util.eee { "$TAG onAdLoadFailed: inter, ${tpAdError?.errorCode}->${tpAdError?.errorMsg}" }

                mLoadCallback?.onAdFailedToLoad(tpAdError?.errorCode?:0,tpAdError?.errorMsg?:"no err msg")
            }

            override fun onAdImpression(tpAdInfo: TPAdInfo) {
                Util.eee { "$TAG onAdImpression: inter, " + tpAdInfo.adSourceName }

                mShowCallback?.onAdShowed()
            }
            override fun onAdClicked(tpAdInfo: TPAdInfo) {
                Util.eee { "$TAG onAdClicked: inter, " + tpAdInfo.adSourceName }

                LifecycleUtil.notLaunch()
                mShowCallback?.onAdClicked()
            }
            override fun onAdClosed(tpAdInfo: TPAdInfo) {
                Util.eee { "$TAG onAdClosed: inter, " + tpAdInfo.adSourceName }

                mShowCallback?.onAdDismissed()
            }

            override fun onAdVideoStart(tpAdInfo: TPAdInfo?) {
                // V8.1.0.1 播放开始
            }
            override fun onAdVideoEnd(tpAdInfo: TPAdInfo?) {
                // V8.1.0.1 播放结束
            }
            override fun onAdVideoError(tpAdInfo: TPAdInfo, err: TPAdError?) {
                Util.eee { "$TAG onAdShowFailed: inter, ${tpAdInfo?.adSourceName}, ${err?.errorCode}->${err?.errorMsg}" }

                mShowCallback?.onAdFailedToShow()
            }
        })


        // 华为下载类按钮
        val mLocalExtras = HashMap<String?, Any?>()
        mLocalExtras.put("huawei_autoinstall", 1)
        interstitialAd.setCustomParams(mLocalExtras)

        interstitialAd.loadAd()
        return interstitialAd
    }

    var mLoadCallback: TradLoadCallback?=null
    var mShowCallback: TradShowCallback?=null
    fun setShowCallback(callback: TradShowCallback){
        mShowCallback = callback
    }


}