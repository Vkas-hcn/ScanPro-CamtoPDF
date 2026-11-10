package com.zppko.pdfplus.nor.ads.tool

import com.bockcock.adjust.FbHelp
import com.bockcock.adjust.KvUtil
import com.bockcock.adjust.Pin
import com.bockcock.adjust.Pt
import com.buncead.common.util.Util
import com.flux.tracksdk.model.TrackPolicy
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

    /**
     * 广告收益统一入口，对齐规范 §3 E10 / E11：
     * - E10 `ad_impression_revenue`：AD-SOURCE + value + currency（固定 USD），出口 bi/adjust/firebase/facebook；
     * - E11 `total_ads_revenue_001`：value + currency（固定 USD），出口 bi/adjust（不经 firebase）。
     *
     * @param zType        配置里的广告类型（op/int/nat/banner，也接受 open/interstitial/native/banner）
     * @param posId        广告位
     * @param adId         广告单元 ID（与请求用同一个值）
     * @param adSourceName 原始广告源名，内部归一为 §2.3 的 `ad_source`
     */
    fun qqq(
        microsVVV: Long,
        currencyVVV: String,
        zType: String = "",
        posId: String = "",
        adId: String = "",
        adSourceName: String? = null,
    ) {
        // TODO: test value code
        var valueMicros = 0L
        var currencyCode = ""
        if (!BuildConfig.DEBUG) {
            valueMicros = microsVVV
            currencyCode = currencyVVV
        } else {
            valueMicros = 6000L
            currencyCode = "usd"
        }
        val usd: Double = UsdFromAdMicros.toUsd(valueMicros, currencyCode.uppercase(getDefault()), null)
        Util.iii { "UsdFromAdMicros =${usd}" }
        reportRevenue(usd, "USD", zType, posId, adId, adSourceName)
    }

    /**
     * TradPlus 收益入口：全局曝光监听（GlobalImpressionListener）已算出单次展示收益（USD）。
     * 不再经过 AdMob micros 换算，直接复用 B 现有 E10/E11 下游上报。
     */
    fun priceUsd(
        usd: Double,
        currency: String,
        zType: String = "",
        posId: String = "",
        adId: String = "",
        adSourceName: String? = null,
    ) {
        reportRevenue(usd, currency.uppercase(getDefault()), zType, posId, adId, adSourceName)
    }

    private fun reportRevenue(
        usd: Double,
        currency: String,
        zType: String,
        posId: String,
        adId: String,
        adSourceName: String?,
    ) {
        Util.iii { "reportRevenue usd=$usd currency=$currency" }
        if (usd <= 0) return

        val adType = when (zType) {
            "op" -> AdTrackingHelper.AdType.OPEN
            "int" -> AdTrackingHelper.AdType.INTERSTITIAL
            "nat" -> AdTrackingHelper.AdType.NATIVE
            "banner" -> AdTrackingHelper.AdType.BANNER
            else -> zType
        }
        val adSource = AdTrackingHelper.adSourceOf(adSourceName)

        // 平台特有收益上报
        // firebase 自动上报(admod 和 firebase 同账号才会自动上报) // 1
//        FirebaseAna.firebase_revenue(usd,currency)
        FbHelp.facebook_revenue(usd, "USD") // 2
        AdTrackingHelper.trackAdRevenueAdjust(usd) // 3

        // E10：bi + firebase（经 Pin.event），adjust 走上面的 AdjustAdRevenue
        Pin.event(
            Pin.ad_impression_revenue,
            Pt()
                .put(FirebaseAnalytics.Param.VALUE, usd)
                .put(FirebaseAnalytics.Param.CURRENCY, "USD")
                .put("ad_type", adType)
                .put("pos_id", posId)
                .put("sdk_name", AdTrackingHelper.SDK_TRADPLUS)
                .put("ad_id", adId)
                .put("ad_source", adSource),
        )

        // E11：累计到 0.01 USD 上报一次并清零，出口 bi + adjust（不经 firebase）
        CoroutineScope(Dispatchers.IO).launch {
            val KEY_AD_VALUE_COUNT = "fsdagc3333"
            val lastValue: Double = KvUtil.getDouble(KEY_AD_VALUE_COUNT)
            val newValue: Double = lastValue + usd
            if (newValue >= 0.01) {
                AdTrackingHelper.adjustPoint(Pin.total_ads_revenue_001, newValue, "USD")
                Pin.myTrack(
                    Pin.total_ads_revenue_001,
                    mutableMapOf("value" to newValue, "currency" to "USD"),
                    TrackPolicy.IMMEDIATE,
                )
                KvUtil.putDouble(KEY_AD_VALUE_COUNT, 0.0)
            } else {
                KvUtil.putDouble(KEY_AD_VALUE_COUNT, newValue)
            }
        }
    }
}
