package com.mymy.mymy.ads.poi


import com.flux.tracksdk.core.TrackSDK

/**
 * 广告埋点辅助类
 * 用于统一管理广告相关的埋点上报
 */
object AdTrackingHelperMy {




    /**
     * 广告类型
     */
    object AdType {
        const val BANNER = "banner"
        const val NATIVE = "native"
        const val INTERSTITIAL = "interstitial"
        const val OPEN = "open"
        const val REWARDED = "rewarded"
    }

    /**
     * 上报广告请求（AdMob 聚合）
     */
    fun trackAdRequestGMA(adType: String, posId: String, sdkName: String ,adId: String): String {
        TrackSDK.trackAdRequest(adType, posId, sdkName, adId)
        return adId
    }

    /**
     * 上报广告填充成功（显式传入来源，适配 AdMob）
     */
    fun trackAdFilledWithSource(
        adType: String,
        posId: String,
        sdkName: String,
        adId: String,
        adSource: String,
        duration: Long
    ) {
        TrackSDK.trackAdFilled(adType, posId, sdkName, adId, adSource, duration)
    }

    /**
     * 上报广告填充失败
     * @param adType 广告类型
     * @param posId 位置ID
     * @param adId 唯一请求ID
     * @param errCode 错误码
     * @param errMsg 错误描述
     */
    fun trackAdFailed(
        adType: String,
        posId: String,
        sdkName: String,
        adId: String,
        errCode: String,
        errMsg: String
    ) {
        // 将 errCode 转换为 Int，如果转换失败使用 -1
        val errCodeInt = errCode.toIntOrNull() ?: -1
        TrackSDK.trackAdFailed(adType, posId, sdkName, adId, errCodeInt, errMsg)
    }

    /**
     * 上报   广告展示（显式传入来源，适配 AdMob）
     */
    fun trackAdImpressionWithSource(
        adType: String,
        posId: String,
        adId: String,
        sdkName: String,
        adSource: String,
        isVisiblePct: Int = 100,
        scene: String = ""
    ) {
        TrackSDK.trackAdImpression(adType, posId, sdkName, adId, adSource, isVisiblePct.toFloat(), scene)
    }

    /**
     * 上报广告点击（显式传入来源，适配 AdMob）
     */
    fun trackAdClickWithSource(
        adType: String,
        posId: String,
        adId: String,
        sdkName: String,
        adSource: String,
        clickArea: String = ""
    ) {
        TrackSDK.trackAdClick(adType, posId, sdkName, adId, adSource, clickArea)
    }

    /**
     * 上报广告关闭
     * @param adType 广告类型
     * @param posId 位置ID
     * @param adId 唯一请求ID
     * @param stayDuration 展示多少秒后关闭
     */
    fun trackAdClose(
        adType: String,
        posId: String,
        adId: String,
        stayDuration: Long
    ) {
        TrackSDK.trackAdClose(adType, posId, adId, stayDuration)
    }

    /**
     * 将内部 adType 映射为 Meta [AppEventsConstants.EVENT_PARAM_AD_TYPE] 文档示例取值。
     */
    private fun mapAdTypeForFacebook(adType: String): String {
        return when (adType) {
            AdType.BANNER -> "banner"
            AdType.NATIVE -> "native"
            AdType.INTERSTITIAL -> "interstitial"
            AdType.REWARDED -> "rewarded_video"
            AdType.OPEN -> "interstitial" // 开屏全屏，文档无单独类型时用 interstitial
            else -> adType.lowercase()
        }
    }

//    /**
//     * 上报广告收益（推荐直接传 AdMob [AdValue]）。
//     * 会按货币将 micros 换算为 **USD** 写入 TrackSDK（含 Adjust：`revenue` + `currency=USD` + `original_currency`）。
//     */
//    fun trackAdImpressionRevenueAdValue(
//        adType: String,
//        posId: String,
//        adId: String,
//        adValue: AdValue,
//        adSourceName: String? = null
//    ) {
//        trackAdImpressionRevenueMicros(
//            adType, posId, adId,
//            adValue.valueMicros,
//            adValue.currencyCode,
//            adSourceName
//        )
//    }

    /**
     * 上报广告收益（AdMob OnPaid：micros + 货币代码）。
     * 累加逻辑和 Firebase AD_IMPRESSION 上报已下沉到 TrackSDK 层。
     * Meta 使用官方标准事件 [AppEventsConstants.EVENT_NAME_AD_IMPRESSION]，
     * [valueToSum] 为美元收入（与 TrackSDK 换算一致），参数键使用 [AppEventsConstants] 中广告相关常量。
     * 是否上报 Meta 由 `TrackConfig.Builder.enableFacebookAdRevenue` 控制（默认开启，传 false 可关闭）。
     *
     * @see <a href="https://developers.facebook.com/docs/app-events/reference">Meta App Events Reference</a>
     */
    fun trackAdImpressionRevenueMicros(
        adType: String,
        posId: String,
        adId: String,
        micros: Long,
        currency: String? = "USD",
        adSourceName: String? = null
    ) {
//        val usd = TrackSDK.trackAdRevenueFromAdMob(
//            adType, posId, adId, micros, currency ?: "USD",
//        )
//        if (usd <= 0) return

//        ImpExt.priceAd(micros,"${currency}")

//        TrackSDK.reportFacebookAdImpression(
//            mapAdTypeForFacebook(adType),
//            posId,
//            adId,
//            usd,
//            adSourceName
//        )
    }

//    fun flux_revenue(value: Double,currency: String){
//        val properties :HashMap<String, Any>  =  HashMap<String, Any>();
//        properties.put("value", "${value}");
//        properties.put("currency", currency);
//        TrackSDK.track("ad_impression_revenue", properties, TrackPolicy.IMMEDIATE);
//    }
//
//
//    fun flux_001(value: Double,currency: String){
//        val properties :HashMap<String, Any>  =  HashMap<String, Any>();
//        properties.put("value", "${value}");
//        properties.put("currency", currency);
//        TrackSDK.track("total_ads_renenue_001", properties, TrackPolicy.IMMEDIATE);
//    }

}
