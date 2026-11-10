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
import java.util.Locale

/**
 * 广告全生命周期埋点，对接 TrackSDK 专用广告接口。
 * `adId` 使用广告单元 ID（规范 §2.3 的 `ad_id`），填充/失败/点击/关闭各环回传同一个值。
 */
object AdTrackingHelper {
    object AdType {
        const val BANNER = "banner"
        const val NATIVE = "native"
        const val INTERSTITIAL = "interstitial"
        const val OPEN = "open"
        const val REWARDED = "rewarded"
    }
    const val SDK_ADMOB = "AdMob"
    const val SOURCE_ADMOB = "admob"

    private val lives = HashMap<String, AdTrackLife>()

    /**
     * `ad_source` 取值规范 §2.3 要求是可枚举的小写子平台名（`admob` / `pangle` / `facebook` / `mtg`），
     * 而 AdMob 的 `adSourceName` 是 `AdMob Network` 这类展示名，直接上报会让 BI 维度对不上。
     */
    fun adSourceOf(networkName: String?): String {
        val raw = networkName?.trim().orEmpty()
        if (raw.isEmpty()) return SOURCE_ADMOB
        val lower = raw.lowercase(Locale.US)
        return when {
            lower.contains("admob") -> SOURCE_ADMOB
            lower.contains("meta") || lower.contains("facebook") -> "facebook"
            lower.contains("pangle") || lower.contains("bytedance") -> "pangle"
            lower.contains("unity") -> "unity"
            lower.contains("applovin") -> "applovin"
            lower.contains("mintegral") || lower.contains("mtg") -> "mtg"
            else -> lower.replace(' ', '_').replace("-", "_")
        }
    }

    fun parseErrCode(errCode: String): Int = errCode.toIntOrNull() ?: -1

    fun stayDurationSeconds(showStartMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
        if (showStartMs <= 0L) return 0L
        return ((nowMs - showStartMs).coerceAtLeast(0L)) / 1000L
    }

    /** 一次 GMA 请求。`adUnitId` 即规范里的 `ad_id`，贯穿后续回调。 */
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
        // 规范 §5.5 / §7：展示类事件只保留 ad_impression_revenue（E10），客户端不另发展示事件。
        // 这里只记录展示起始时间，供 E09 `ad_close` 的 `stay_duration` 使用。
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


    fun trackAdRevenueAdjust(
        usd: Double
    ) {
        runCatching {
            val adRevenue = AdjustAdRevenue("admob_sdk")
            if (usd < 0) return
            adRevenue.setRevenue(usd, "USD")
            adRevenue.setAdRevenueNetwork("Admob")
            // 规范 §5.2：AdjustAdRevenue 必须同时声明展示次数，否则收入无法按曝光归因。
            adRevenue.setAdImpressionsCount(1)
            Adjust.trackAdRevenue(adRevenue)
            AppLog.tracking.d("trackAdRevenueAdjust usd=$usd")
        }.onFailure {
            AppLog.tracking.e("trackAdRevenueAdjust failed ", it)
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
            // ad_impression_revenue 的 Adjust 出口已改为 AdjustAdRevenue，不再走事件 token。
            "total_ads_revenue_001" -> "hbsfq9"
            "notification_trigger" -> "ovwl7i"
            "notification_show" -> "bn7qcg"
            "notification_click" -> "j6k5sd"
            else -> null
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
