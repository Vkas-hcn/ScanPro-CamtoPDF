package com.zppko.pdfplus.nor.ads.trad

import android.content.Context
import com.buncead.common.util.Util
import com.scanpro.cam.pdf.CamPdfApp
import com.tradplus.ads.base.bean.TPAdError
import com.tradplus.ads.base.bean.TPAdInfo
import com.tradplus.ads.base.bean.TPBaseAd
import com.tradplus.ads.open.splash.SplashAdListener
import com.tradplus.ads.open.splash.TPSplash
import com.scanpro.cam.pdf.ads.SpAdSession


class TradAdOpen {
    val TAG = "TradAdOpen"

    fun getAD(): TPSplash? {
        return mTPSplash
    }

    private var mTPSplash: TPSplash? = null
    var mTPAdInfo: TPAdInfo? = null

    fun initSplashAd(placementId: String?, call: TradLoadCallback): TPSplash {
        val context: Context = CamPdfApp.get().applicationContext
        mLoadCallback = call

        val splashAd: TPSplash = TPSplash(context, placementId)
        mTPSplash = splashAd

        val localExtras: MutableMap<String, Any> = mutableMapOf()
        localExtras.put("time_out", 10000)
        splashAd.setCustomParams(localExtras)

        // 设置监听
        splashAd.setAdListener(object : SplashAdListener() {
            override fun onAdLoaded(tpAdInfo: TPAdInfo?, tpBaseAd: TPBaseAd?) {
                Util.eee { "$TAG onAdLoaded: open, ecpm=${tpAdInfo?.ecpm}, ${tpAdInfo?.adSourceName}" }
                // 加载成功后展示广告
                //======================================================================================================
                // 这里一定要注意，需要判断一下是否已经进入app内部，如果加载时间过长，已经进入到app内部，这次load结果就不展示了
//                Toast.makeText(this@SplashActivity, "广告加载成功", Toast.LENGTH_SHORT).show()

                mTPAdInfo = tpAdInfo
                mLoadCallback?.onAdLoaded()
            }
            override fun onAdLoadFailed(tpAdError: TPAdError?) {
                Util.eee { "$TAG onAdLoadFailed: open, ${tpAdError?.errorCode}->${tpAdError?.errorMsg}" }
                // 广告加载失败
                //======================================================================================================
                // 这里一定要注意，需要判断一下是否已经进入app内部，如果加载时间过长，已经进入到app内部，这次load结果就不展示了
//                Toast.makeText(this@SplashActivity, "广告加载失败", Toast.LENGTH_SHORT).show()
//                this@SplashActivity.finish()

                mLoadCallback?.onAdFailedToLoad(tpAdError?.errorCode?:0,tpAdError?.errorMsg?:"no err msg")
            }

            override fun onAdImpression(tpAdInfo: TPAdInfo?) {
                Util.eee { "$TAG onAdImpression: open, " + tpAdInfo?.adSourceName }

                mShowCallback?.onAdShowed()
            }
            override fun onAdClicked(tpAdInfo: TPAdInfo?) {
                Util.eee { "$TAG onAdClicked: open, " + tpAdInfo?.adSourceName }
//                this@SplashActivity.finish()

                SpAdSession.markAdClicked()
                mShowCallback?.onAdClicked()
            }
            override fun onAdClosed(tpAdInfo: TPAdInfo?) {
                Util.eee { "$TAG onAdClosed: open, " + tpAdInfo?.adSourceName }
                // 广告关闭后，要把开屏页面关闭，如果是跟内容在同一个activity，这里把开屏的容器remove掉
//                this@SplashActivity.finish()

                mShowCallback?.onAdDismissed()
            }

            override fun onAdShowFailed(tpAdInfo: TPAdInfo?, err: TPAdError?) {
                Util.eee { "$TAG onAdShowFailed: open, ${tpAdInfo?.adSourceName}, ${err?.errorCode}->${err?.errorMsg}" }

                mShowCallback?.onAdFailedToShow()
            }


        })

        splashAd.loadAd(null)
        return splashAd
    }

    var mLoadCallback: TradLoadCallback?=null
    var mShowCallback: TradShowCallback?=null
    fun setShowCallback(callback: TradShowCallback){
        mShowCallback = callback
    }


}
