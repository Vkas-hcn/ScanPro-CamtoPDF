package com.scanpro.cam.pdf.ads

import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.scanpro.cam.pdf.analytics.SpTracker

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

    /** Apollo `sendAdIncome(adValue, responseInfo)`。 */
    fun sendAdIncome(context: Context, adValue: AdValue, responseInfo: ResponseInfo?) {
        val amount = adValue.valueMicros / 1_000_000.0
        val network = mediationNetwork(responseInfo)
        try {
            val params = Bundle().apply {
                putDouble(AdIncomeKeys.VALUE, amount)
                putString(AdIncomeKeys.CURRENCY, "USD")
                putString(AdIncomeKeys.NETWORK, network)
            }
            SpTracker.log(context, SpTracker.AD_IMPRESSION_REVENUE, params)
        } catch (t: Throwable) {
            // 上报失败不影响主流程
        }
    }

    /** 等价 Apollo `responseInfo.c().h()`：拿中介网络名，缺省 "Admob"。 */
    private fun mediationNetwork(responseInfo: ResponseInfo?): String {
        // 不用 getMediationAdapterClassName()：play-services-ads 25 已把它移除。
        val name = responseInfo?.adapterResponses?.firstOrNull()?.adSourceName?.trim().orEmpty()
        return name.ifEmpty { DEFAULT_NETWORK }
    }

    private const val DEFAULT_NETWORK = "Admob"

    private object AdIncomeKeys {
        const val VALUE = "value"
        const val CURRENCY = "currency"
        const val NETWORK = "network"
    }
    // ponytail: Adjust `AdjustAdRevenue` 与 Facebook `AppEventsLogger` 两路没接，
    // 因为工程没有这两个依赖（gradle/libs.versions.toml 只有 firebase-analytics）。
    // 补依赖后：Adjust.trackAdRevenue(AdjustAdRevenue("admob_sdk").apply {
    //     setRevenue(amount, adValue.currencyCode); setAdRevenueNetwork(network) })
    // 与 AppEventsLogger.logPurchase(...)。升级路径就这两行，别的不动。
}
