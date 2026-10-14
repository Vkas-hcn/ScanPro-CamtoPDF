package com.scanpro.cam.pdf.ads

import android.content.Context
import com.flux.tracksdk.utils.UsdFromAdMicros
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.scanpro.cam.pdf.BuildConfig
import com.scanpro.cam.pdf.analytics.AdTrackLife
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.scanpro.cam.pdf.util.AppLog
import java.util.Locale.getDefault

/**
 * 广告展示收入上报。照抄 ApolloPDF `ad/AdIncomeReport.java` 的三路上报，
 * **但只保留我们有 SDK 的一路**：
 *
 * | 通道 | Apollo | 本工程 |
 * |---|---|---|
 * | Adjust `AdjustAdRevenue("admob_sdk")` | 有 | 缺依赖，见文末 |
 * | Firebase `ad_impression_revenue`（value + currency） | 有 | ✅ 已实现 |
 * | Facebook `AppEventsLogger` | 有 | 缺依赖，见文末 |
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/AdIncomeReport.java`
 *
 * 值口径与 Apollo 一致：`d = adValue.valueMicros / 1_000_000.0`（原币种金额，不是 eCPM）。
 * 货币用 `AdValue.currencyCode`；Apollo 的 Firebase 那路硬编码 `"USD"`，这里保留原语义。
 */
object SpAdIncomeReport {

    /**
     * @param posId 广告位，17 个 [SpAdPosition] 之一。开屏用 `ac_launch`，不用进入场景码。
     */
    fun sendAdIncome(
        context: Context,
        adValue: AdValue,
        responseInfo: ResponseInfo?,
        adType: String,
        posId: String,
        life: AdTrackLife?,
    ) {
        try {
            // 规范 §4 E10：一次展示被平台多次回调收入时只报一次
            if (life != null && life.revenueReported) return
            val valueM = if (BuildConfig.DEBUG) {
                6000L
            } else {
                adValue.valueMicros
            }
            val usd: Double = UsdFromAdMicros.toUsd(
                valueM,
                adValue.currencyCode.uppercase(getDefault()),
                null
            )
            val source = responseInfo?.adapterResponses?.firstOrNull()?.adSourceName
            val adSource = life?.adSource ?: AdTrackingHelper.adSourceOf(source)
            val adId = life?.adId.orEmpty()
            AppLog.tracking.d("广告收入 类型=$adType 位置=$posId 来源=$source adId=$adId")
            AdTrackingHelper.trackAdRevenueAdjust(usd)
            AdTrackingHelper.trackAdImpressionRevenueMicros(context, usd, adType, posId, adId, adSource)
            life?.revenueReported = true
        } catch (t: Throwable) {
            AppLog.tracking.e("上报收入失败 类型=$adType 位置=$posId", t)
        }
    }
}
