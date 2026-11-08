package com.zppko.pdfplus.nor.ads.tool

import com.bockcock.adjust.FbHelp
import com.bockcock.adjust.KvUtil
import com.bockcock.adjust.Pin
import com.bockcock.adjust.Pt
import com.buncead.common.util.Util
import com.flux.tracksdk.utils.UsdFromAdMicros
import com.google.firebase.analytics.FirebaseAnalytics
// 迁移适配：AppConfig.isBundle() → !BuildConfig.DEBUG
import com.scanpro.cam.pdf.BuildConfig
// 迁移适配：AdJustHelp.sendRevenue → B 的 Adjust 收益通道
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale.getDefault

object Price {


    fun qqq(microsVVV:Long,currencyVVV: String ){

            // TODO: test value code
            var valueMicros = 0L
            var currencyCode = ""
            if (!BuildConfig.DEBUG){
                valueMicros = microsVVV
                currencyCode = currencyVVV
            }else {
                valueMicros = 6000L
                currencyCode = "usd"
            }
            val  micros = valueMicros;
            val currency = currencyCode;

            val usd: Double = UsdFromAdMicros.toUsd(micros, "$currency".uppercase(getDefault()),null)
            Util.iii { "UsdFromAdMicros =${usd}" }
            if (usd <= 0) return
            // 平台特有收益上报
            // firebase 自动上报(admod 和 firebase 同账号才会自动上报) // 1
//        FirebaseAna.firebase_revenue(usd,currency)
            FbHelp.facebook_revenue(usd,"${currency}") // 2
            AdTrackingHelper.trackAdRevenueAdjust(usd) // 3

            // 收益每个平台在报一次
            Pin.event(Pin.ad_impression_revenue, Pt().put(FirebaseAnalytics.Param.VALUE,usd).put(FirebaseAnalytics.Param.CURRENCY,"${currency}"))

            // 后台
            CoroutineScope(Dispatchers.IO).launch {
                val KEY_AD_VALUE_COUNT = "fsdagc3333"
                val lastValue: Double = KvUtil.getDouble(KEY_AD_VALUE_COUNT)
                val newValue:Double = lastValue + usd
                if (newValue>=0.01){
                    Pin.event(Pin.total_ads_revenue_001, Pt().put(FirebaseAnalytics.Param.VALUE,newValue).put(FirebaseAnalytics.Param.CURRENCY,"${currency}"))
                    KvUtil.putDouble(KEY_AD_VALUE_COUNT,0.0)
                }else{
                    KvUtil.putDouble(KEY_AD_VALUE_COUNT,newValue)
                }
            }


    }
}
