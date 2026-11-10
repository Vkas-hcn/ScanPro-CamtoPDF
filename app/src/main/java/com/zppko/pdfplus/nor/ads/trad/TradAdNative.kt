package com.zppko.pdfplus.nor.ads.trad

import android.content.Context
import android.util.Log
import com.scanpro.cam.pdf.CamPdfApp
import com.buncead.common.util.Util
import com.tradplus.ads.base.bean.TPAdError
import com.tradplus.ads.base.bean.TPAdInfo
import com.tradplus.ads.base.bean.TPBaseAd
import com.tradplus.ads.open.nativead.NativeAdListener
import com.tradplus.ads.open.nativead.TPNative
import com.scanpro.cam.pdf.ads.SpAdSession

class TradAdNative {

    val TAG = "TradAdNative"

    fun getAD(): TPNative? {
        return mTPNative
    }

    private var mTPNative: TPNative? = null
    var mTPAdInfo: TPAdInfo? = null

    fun initSplashAd(placementId: String?, call: TradLoadCallback): TPNative {
        val context: Context = CamPdfApp.get().applicationContext
        mLoadCallback = call

        val nativeAd: TPNative = TPNative(context, placementId)
        mTPNative = nativeAd

        // 设置监听
        nativeAd.setAdListener(object : NativeAdListener() {
            override fun onAdLoaded(tpAdInfo: TPAdInfo, tpBaseAd: TPBaseAd?) {
                Util.eee { "$TAG onAdLoaded: native, ecpm=${tpAdInfo?.ecpm}, ${tpAdInfo?.adSourceName}" }

                mTPAdInfo= tpAdInfo
                mLoadCallback?.onAdLoaded()
            }
            override fun onAdLoadFailed(tpAdError: TPAdError) {
                Util.eee { "$TAG onAdLoadFailed: native, ${tpAdError?.errorCode}->${tpAdError?.errorMsg}" }

                mLoadCallback?.onAdFailedToLoad(tpAdError?.errorCode?:0,tpAdError?.errorMsg?:"no err msg")
            }

            override fun onAdImpression(tpAdInfo: TPAdInfo) {
                Util.eee { "$TAG onAdImpression: native, " + tpAdInfo.adSourceName }

                mShowCallback?.onAdShowed()
            }
            override fun onAdClicked(tpAdInfo: TPAdInfo) {
                Util.eee { "$TAG onAdClicked: native, " + tpAdInfo.adSourceName }

                SpAdSession.markAdClicked()
                mShowCallback?.onAdClicked()
            }
            override fun onAdClosed(tpAdInfo: TPAdInfo) {
                Util.eee { "$TAG onAdClosed: native, " + tpAdInfo.adSourceName }

                mShowCallback?.onAdDismissed()
            }
            override fun onAdShowFailed(err: TPAdError?, tpAdInfo: TPAdInfo) {
                Util.eee { "$TAG onAdShowFailed: native, ${tpAdInfo?.adSourceName}, ${err?.errorCode}->${err?.errorMsg}" }

                mShowCallback?.onAdFailedToShow()
            }

        })

        nativeAd.setCustomParams(setLocalCustomParams());

        nativeAd.loadAd()
        return nativeAd
    }

    var mLoadCallback: TradLoadCallback?=null
    var mShowCallback: TradShowCallback?=null
    fun setShowCallback(callback: TradShowCallback){
        mShowCallback = callback
    }


    private fun setLocalCustomParams(): MutableMap<String?, Any?> {
        val mLocalExtras = HashMap<String?, Any?>()
        // 华为dislike关闭 右下角显示
        mLocalExtras.put("huawei_close_position", 2)
        // 华为模板渲染  1 大图（默认） 2 小图 3 三小图
        // 自渲染类型开发者需要高级自定义的方式获取多图tpNativeAdView.getPicObject()，获取返回的图片自行添加到布局中
        mLocalExtras.put("huawei_native_template_type", 1)
        // 华为下载类广告下载控件点击直接安装
        // 自动下载 1 ，默认关闭 0
        // 模板渲染类型展示后直接使用，自渲染类型开发者需要高级自定义的方式实现获取tpNativeAdView.getAppDownloadButton(),判断不为空的时候替换cta按钮
        mLocalExtras.put("huawei_autoinstall", 0)
        return mLocalExtras
    }

}
