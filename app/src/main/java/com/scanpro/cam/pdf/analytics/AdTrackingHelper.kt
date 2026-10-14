package com.scanpro.cam.pdf.analytics

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustEvent
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy
import com.google.android.gms.ads.ResponseInfo
import com.google.firebase.analytics.FirebaseAnalytics
import com.scanpro.cam.pdf.CamPdfApp
import com.scanpro.cam.pdf.util.AppLog

/**
 * 广告全生命周期埋点，对接 TrackSDK 专用广告接口。
 * `adId` 使用广告单元 ID（规范 §2.3，形如 ca-app-pub-…/…），填充/失败/点击/关闭各环回传同一个值。
 */
object AdTrackingHelper {
    object AdType {
        const val BANNER = "banner"
        const val NATIVE = "native"
        const val INTERSTITIAL = "interstitial"
        const val OPEN = "open"
        const val REWARDED = "rewarded"
    }

    private val revenueLock = Any()
    private const val PREF_NAME = "ad_tracking_prefs"
    private const val KEY_TOTAL_ADS_REVENUE = "total_ads_revenue_001_accumulated"
    const val SDK_ADMOB = "AdMob"
    const val SOURCE_ADMOB = "admob"

    private val lives = HashMap<String, AdTrackLife>()

    fun adSourceOf(networkName: String?): String =
        networkName?.takeIf { it.isNotBlank() } ?: SOURCE_ADMOB

    fun parseErrCode(errCode: String): Int = errCode.toIntOrNull() ?: -1

    fun stayDurationSeconds(showStartMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
        if (showStartMs <= 0L) return 0L
        return ((nowMs - showStartMs).coerceAtLeast(0L)) / 1000L
    }

    /** 一次 GMA 请求。`adUnitId` 为广告单元 ID（规范 §2.3 的 ad_id，形如 ca-app-pub-…/…），贯穿后续回调。 */
    fun begin(adType: String, posId: String, adUnitId: String, scene: String = ""): AdTrackLife {
        val started = System.currentTimeMillis()
        trackAdRequestGMA(adType, posId, adUnitId, scene)
        return AdTrackLife(adUnitId, adType, posId, started)
    }

    fun remember(sessionId: String, life: AdTrackLife) {
        if (sessionId.isNotEmpty()) lives[sessionId] = life
    }

    fun recall(sessionId: String): AdTrackLife? = lives[sessionId]

    fun forget(sessionId: String) {
        lives.remove(sessionId)
    }

    fun filled(life: AdTrackLife, responseInfo: ResponseInfo?) {
        life.adSource = adSourceOf(responseInfo?.adapterResponses?.firstOrNull()?.adSourceName)
        trackAdFilledWithSource(
            adType = life.adType,
            posId = life.posId,
            adId = life.adId,
            sdkName = SDK_ADMOB,
            adSource = life.adSource,
            duration = (System.currentTimeMillis() - life.requestStartMs).coerceAtLeast(0L),
        )
    }

    fun failed(life: AdTrackLife, code: Int, message: String) {
        trackAdFailed(life.adType, life.posId, life.adId, code.toString(), message)
    }

    fun impression(life: AdTrackLife, scene: String = "") {
        // 规范 §7：展示不另立事件（ad_show / ad_impression 已作废），展示与展示级收入统一走 ad_impression_revenue。
        // 这里只记录展示起始时间，供 ad_close 的 stay_duration 计算，不再发 SDK 展示事件。
        if (life.showStartMs == 0L) life.showStartMs = System.currentTimeMillis()
    }

    fun click(life: AdTrackLife) {
        trackAdClickWithSource(
            adType = life.adType,
            posId = life.posId,
            adId = life.adId,
            sdkName = SDK_ADMOB,
            adSource = life.adSource,
        )
    }

    fun close(life: AdTrackLife) {
        trackAdClose(
            adType = life.adType,
            posId = life.posId,
            adId = life.adId,
            stayDuration = stayDurationSeconds(life.showStartMs),
        )
        flush()
    }

    fun trackAdRequestGMA(adType: String, posId: String, adUnitId: String, scene: String = "") {
        report("trackAdRequestGMA") {
            TrackSDK.trackAdRequest(adType, posId, SDK_ADMOB, adUnitId)
        }
        mirrorFirebase(SpTracker.AD_REQUEST, adBaseBundle(adType, posId, SDK_ADMOB, adUnitId).apply {
            // 规范 §4 E05：预加载用 scene=preload 标；现拉/边拉边展示不带 scene
            if (scene.isNotBlank()) putString("scene", scene)
        })
        AppLog.tracking.d("埋点已提交 平台=tracksdk 事件=trackAdRequest 参数=adType=$adType, posId=$posId, sdk=$SDK_ADMOB, adId=$adUnitId, scene=$scene")
    }

    fun trackAdFilledWithSource(
        adType: String,
        posId: String,
        adId: String,
        sdkName: String,
        adSource: String,
        duration: Long,
    ) {
        report("trackAdFilledWithSource") {
            TrackSDK.trackAdFilled(adType, posId, sdkName, adId, adSource, duration)
        }
        mirrorFirebase(SpTracker.AD_FILLED, adBaseBundle(adType, posId, sdkName, adId).apply {
            putString(SpTracker.PARAM_AD_SOURCE, adSource)
            putLong("duration", duration)
        })
        AppLog.tracking.d(
            "埋点已提交 平台=tracksdk 事件=trackAdFilled 参数=adType=$adType, posId=$posId, sdk=$sdkName, adId=$adId, adSource=$adSource, duration=$duration",
        )
    }

    fun trackAdFailed(
        adType: String,
        posId: String,
        adId: String,
        errCode: String,
        errMsg: String,
    ) {
        val errCodeInt = parseErrCode(errCode)
        report("trackAdFailed") {
            TrackSDK.trackAdFailed(adType, posId, SDK_ADMOB, adId, errCodeInt, errMsg)
        }
        mirrorFirebase(SpTracker.AD_FAILED, adBaseBundle(adType, posId, SDK_ADMOB, adId).apply {
            putString("err_msg", errMsg)
            putInt("err_code", errCodeInt)
        })
        AppLog.tracking.d(
            "埋点已提交 平台=tracksdk 事件=trackAdFailed 参数=adType=$adType, posId=$posId, sdk=$SDK_ADMOB, adId=$adId, errCode=$errCodeInt, errMsg=$errMsg",
        )
    }

    fun trackAdClickWithSource(
        adType: String,
        posId: String,
        adId: String,
        sdkName: String,
        adSource: String,
        clickArea: String = "",
    ) {
        report("trackAdClickWithSource") {
            TrackSDK.trackAdClick(adType, posId, sdkName, adId, adSource, clickArea)
        }
        mirrorFirebase(SpTracker.AD_CLICK, adBaseBundle(adType, posId, sdkName, adId).apply {
            putString(SpTracker.PARAM_AD_SOURCE, adSource)
            if (clickArea.isNotBlank()) putString("click_area", clickArea)
        })
        AppLog.tracking.d(
            "埋点已提交 平台=tracksdk 事件=trackAdClick 参数=adType=$adType, posId=$posId, sdk=$sdkName, adId=$adId, adSource=$adSource, clickArea=$clickArea",
        )
    }

    fun trackAdClose(
        adType: String,
        posId: String,
        adId: String,
        stayDuration: Long,
    ) {
        report("trackAdClose") {
            TrackSDK.trackAdClose(adType, posId, adId, stayDuration)
        }
        mirrorFirebase(SpTracker.AD_CLOSE, adBaseBundle(adType, posId, SDK_ADMOB, adId).apply {
            putLong("stay_duration", stayDuration)
        })
        AppLog.tracking.d(
            "埋点已提交 平台=tracksdk 事件=trackAdClose 参数=adType=$adType, posId=$posId, adId=$adId, stayDuration=$stayDuration",
        )
    }

    fun flush() {
        report("flush") { TrackSDK.flush() }
    }

    private fun report(name: String, block: () -> Unit) {
        runCatching(block).onFailure {
            AppLog.tracking.w(
                "埋点提交失败 平台=tracksdk 事件=$name",
                it
            )
        }
    }

    /** 广告生命周期事件的 firebase 出口（规范 §3 E05-E09 出口为 bi、firebase）。 */
    private fun mirrorFirebase(event: String, bundle: Bundle) {
        runCatching {
            FirebaseAnalytics.getInstance(CamPdfApp.get()).logEvent(event, bundle)
        }.onFailure {
            AppLog.tracking.w("埋点提交失败 平台=firebase 事件=$event", it)
        }
    }

    private fun adBaseBundle(adType: String, posId: String, sdkName: String, adId: String) =
        Bundle().apply {
            putString(SpTracker.PARAM_AD_TYPE, adType)
            putString(SpTracker.PARAM_POS_ID, posId)
            putString(SpTracker.PARAM_SDK_NAME, sdkName)
            putString(SpTracker.PARAM_AD_ID, adId)
        }

    fun trackAdRevenueAdjust(
        usd: Double
    ) {
        runCatching {
            val adRevenue = AdjustAdRevenue("admob_sdk")
            if (usd < 0) return
            adRevenue.setRevenue(usd, "USD")
            adRevenue.setAdRevenueNetwork("Admob")
            Adjust.trackAdRevenue(adRevenue)
            AppLog.tracking.d("trackAdRevenueAdjust usd=$usd")
        }.onFailure {
            AppLog.tracking.e("trackAdRevenueAdjust failed ", it)
        }
    }

    /**
     * 上报广告收益（AdMob OnPaidEvent，使用 micros）
     */
    fun trackAdImpressionRevenueMicros(
        context: Context,
        revenue: Double,
        adType: String,
        posId: String,
        adId: String,
        adSource: String,
    ) {
        if (revenue < 0) return
        val currencyCode = "USD"
        trackRevenueEvent(
            event = SpTracker.AD_IMPRESSION_REVENUE,
            revenue = revenue,
            currencyCode = currencyCode,
            adType = adType,
            posId = posId,
            adId = adId,
            adSource = adSource,
        )
        trackTotalAdsRevenueIfNeeded(context, revenue)
        AppLog.tracking.e("BI Firebase Facebook usd=$revenue adType=$adType")
    }


    /**
     * 各平台互相独立上报：任一平台抛出异常不得影响其余平台，否则排在后面的平台会被整条丢弃。
     */
    fun trackRevenueEvent(
        event: String,
        revenue: Double,
        currencyCode: String = "USD",
        adType: String,
        posId: String,
        adId: String,
        adSource: String,
    ) {
        val sdkName = SDK_ADMOB
        //Firebase：ad_impression_revenue 全参数（AD-SOURCE + value + currency）
        runCatching {
            val bundle = Bundle().apply {
                putString(SpTracker.PARAM_AD_TYPE, adType)
                putString(SpTracker.PARAM_POS_ID, posId)
                putString(SpTracker.PARAM_SDK_NAME, sdkName)
                putString(SpTracker.PARAM_AD_ID, adId)
                putString(SpTracker.PARAM_AD_SOURCE, adSource)
                putDouble(FirebaseAnalytics.Param.VALUE, revenue)
                putString(FirebaseAnalytics.Param.CURRENCY, currencyCode)
            }
            FirebaseAnalytics.getInstance(CamPdfApp.get()).logEvent(event, bundle)
        }.onFailure {
            AppLog.tracking.e("trackRevenueEvent Firebase failed: $event", it)
        }
        //Adjust：作为 ad_impression_revenue 事件上报（token 见 adjustTokenFor），带 revenue/currency
        runCatching {
            adjustPoint(event, revenue, currencyCode)
        }.onFailure {
            AppLog.tracking.e("trackRevenueEvent Adjust failed: $event", it)
        }
        //BI：ad_impression_revenue 全参数，金额字段用 value
        runCatching {
            val properties: MutableMap<String?, Any?> = java.util.HashMap<String?, Any?>()
            properties[SpTracker.PARAM_AD_TYPE] = adType
            properties[SpTracker.PARAM_POS_ID] = posId
            properties[SpTracker.PARAM_SDK_NAME] = sdkName
            properties[SpTracker.PARAM_AD_ID] = adId
            properties[SpTracker.PARAM_AD_SOURCE] = adSource
            properties["value"] = revenue
            properties["currency"] = currencyCode
            TrackSDK.track(event, properties, TrackPolicy.IMMEDIATE)
            AppLog.tracking.e("trackRevenueEvent BI: $event value=$revenue currency=$currencyCode posId=$posId adId=$adId")
        }.onFailure {
            AppLog.tracking.e("trackRevenueEvent BI failed: $event", it)
        }
        //Facebook：金额已是 USD，不再传原始币种二次换算
        runCatching {
            trackAdRevenueFacebook(adType, posId, revenue)
        }.onFailure {
            AppLog.tracking.e("trackRevenueEvent Facebook failed: $event", it)
        }

    }


    private fun trackTotalAdsRevenueIfNeeded(
        context: Context,
        revenue: Double,
    ) {
        synchronized(revenueLock) {
            val prefs = context.applicationContext.trackingPrefs()
            val totalRevenue = prefs.getDouble(KEY_TOTAL_ADS_REVENUE, 0.0) + revenue
            if (totalRevenue >= 0.01) {
                trackValueEvent(
                    event = "total_ads_revenue_001",
                    revenue = totalRevenue,
                    currencyCode = "USD"
                )
                prefs.putDouble(KEY_TOTAL_ADS_REVENUE, 0.0)
            } else {
                prefs.putDouble(KEY_TOTAL_ADS_REVENUE, totalRevenue)
            }
        }
    }


    private fun Context.trackingPrefs(): SharedPreferences {
        return applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun SharedPreferences.putDouble(key: String, value: Double) {
        edit { putLong(key, value.toBits()) }
    }

    private fun SharedPreferences.getDouble(key: String, defaultValue: Double): Double {
        if (!contains(key)) return defaultValue
        return Double.fromBits(getLong(key, defaultValue.toBits()))
    }

    private fun trackValueEvent(
        event: String,
        revenue: Double,
        currencyCode: String = "USD"
    ) {
        // 规范 E11 total_ads_revenue_001 出口为 bi、adjust，不发 firebase。
        adjustPoint(event, revenue, currencyCode)
        runCatching {
            //properties为map类型
            val properties = mutableMapOf<String, Any>().apply {
                put("value", revenue)
                put("currency", currencyCode)
            }
            TrackSDK.track(event, properties, TrackPolicy.IMMEDIATE)
            AppLog.tracking.d("trackValueEvent: $event revenue=$revenue currency=$currencyCode")
        }.onFailure {
            AppLog.tracking.e("trackValueEvent failed: $event", it)
        }
    }


    fun adjustPoint(
        key: String,
        revenue: Double? = null,
        currencyCode: String? = null,
    ) {
        val token = adjustTokenFor(key)
        if (token.isNullOrBlank()) return
        runCatching {
            val adjustEvent = AdjustEvent(token)
            revenue?.let { adjustEvent.addCallbackParameter("revenue", it.toString()) }
            currencyCode?.let { adjustEvent.addCallbackParameter("currency", it) }
            Adjust.trackEvent(adjustEvent)
        }.onFailure {
            AppLog.tracking.e("adjustPoint failed: $key", it)
        }
    }

    private fun adjustTokenFor(key: String): String? {
        return when (key) {
            "ad_impression_revenue" -> "82ahw3"
            "total_ads_revenue_001" -> "hbsfq9"
            "notification_trigger" -> "ovwl7i"
            "notification_show" -> "bn7qcg"
            "notification_click" -> "j6k5sd"
            else -> null
        }
    }

    /**
     * 上报 Facebook 广告价值。入参已是美元，币种固定 USD，不在此处二次换算。
     */
    fun trackAdRevenueFacebook(
        adType: String,
        posId: String,
        revenueUsd: Double,
        adSourceName: String? = null
    ) {
        val context = TrackSDK.getContext() ?: return
        if (!FacebookSdk.isInitialized()) return
        try {
            val fbParams = Bundle().apply {
                putString(AppEventsConstants.EVENT_PARAM_AD_TYPE, mapAdTypeForFacebook(adType))
                putString(AppEventsConstants.EVENT_PARAM_CURRENCY, "USD")
                putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, posId)
                adSourceName?.takeIf { it.isNotBlank() }?.let { putString("_ad_source", it) }
            }
            AppEventsLogger.newLogger(context).logEvent(
                AppEventsConstants.EVENT_NAME_PURCHASED,
                revenueUsd,
                fbParams
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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


}

/** 单次广告请求的关联上下文，`adId`（广告单元 ID）贯穿请求到关闭。 */
class AdTrackLife(
    val adId: String,
    val adType: String,
    val posId: String,
    val requestStartMs: Long,
    var adSource: String = AdTrackingHelper.SOURCE_ADMOB,
    var showStartMs: Long = 0L,
    /** 规范 §4 E10：一次展示被多次回调收入时只报一次。 */
    var revenueReported: Boolean = false,
)
