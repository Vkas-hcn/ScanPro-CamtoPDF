package com.zppko.pdfplus.nor.ads.mob

import android.app.Activity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad


/**
 * 原生。骨架照 SmartPdfScan NatAdsLoad（childLoad + getAdView + childShow 空实现），
 * SDK 用 GMA AdLoader；收入照 PDF26 模式在填充成功后挂 OnPaidEventListener。
 * 渲染由 B 现有版式层（big/small + 关闭键）负责，经 ImpressionUtils.fireAnative 取用。
 */
class NatAdsLoad(
    setting: AdsSetting
) : BaseAdsLoad(setting) {

    private var nativeAd: NativeAd? = null

    override fun childLoad() {
        if (setting.zId.isEmpty()) {
            zmyAdsLoadFFFF("")
            return
        }
        AdLoader.Builder(Zppkoo.ii, setting.zId)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
                ad.setOnPaidEventListener { value ->
                    val adSourceName = ad.responseInfo?.adapterResponses?.firstOrNull()?.adSourceName
                    sendValueToServer(value, adSourceName)
                }
                zmyAdsLoadSSSS()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    zmyAdsLoadFFFF(error.message)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build(),
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    fun getAdView(point: String): NativeAd? {
        return nativeAd
    }

    override fun childShow(act: Activity, event: String, whenOnVisible: Boolean, finish: () -> Unit) {
        // A 原样：原生由页面拿对象自己挂 UI（B 渲染层负责），此处空实现。
    }

    override fun zcanUsable(): Boolean {
        return nativeAd != null
    }

    override fun zAdsDestory() {
        nativeAd?.destroy()
        nativeAd = null
    }

}