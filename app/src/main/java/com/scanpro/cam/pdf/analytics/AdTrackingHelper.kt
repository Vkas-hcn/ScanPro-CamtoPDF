package com.scanpro.cam.pdf.analytics

import com.flux.tracksdk.core.TrackSDK
import com.google.android.gms.ads.ResponseInfo
import com.scanpro.cam.pdf.util.AppLog
import java.util.UUID

/**
 * 广告全生命周期埋点，对接 TrackSDK 专用广告接口。
 * 能力对齐 EfileScan `AdTrackingHelper`：`adId` 在请求时生成，填充/失败/曝光/点击/关闭回传同一个值。
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

    fun newAdId(): String = UUID.randomUUID().toString()

    fun adSourceOf(networkName: String?): String =
        networkName?.takeIf { it.isNotBlank() } ?: SOURCE_ADMOB

    fun parseErrCode(errCode: String): Int = errCode.toIntOrNull() ?: -1

    fun stayDurationSeconds(showStartMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
        if (showStartMs <= 0L) return 0L
        return ((nowMs - showStartMs).coerceAtLeast(0L)) / 1000L
    }

    /** 一次 GMA 请求。返回的 [AdTrackLife.adId] 贯穿后续回调。 */
    fun begin(adType: String, posId: String): AdTrackLife {
        val started = System.currentTimeMillis()
        val adId = trackAdRequestGMA(adType, posId)
        return AdTrackLife(adId, adType, posId, started)
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
        if (life.showStartMs == 0L) life.showStartMs = System.currentTimeMillis()
        trackAdImpressionWithSource(
            adType = life.adType,
            posId = life.posId,
            adId = life.adId,
            sdkName = SDK_ADMOB,
            adSource = life.adSource,
            scene = scene,
        )
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

    fun trackAdRequestGMA(adType: String, posId: String): String {
        val adId = newAdId()
        report("trackAdRequestGMA") {
            TrackSDK.trackAdRequest(adType, posId, SDK_ADMOB, adId)
        }
        AppLog.tracking.d("埋点已提交 平台=tracksdk 事件=trackAdRequest 参数=adType=$adType, posId=$posId, sdk=$SDK_ADMOB, adId=$adId")
        return adId
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

    fun trackAdImpressionWithSource(
        adType: String,
        posId: String,
        adId: String,
        sdkName: String,
        adSource: String,
        isVisiblePct: Int = 100,
        scene: String = "",
    ) {
        report("trackAdImpressionWithSource") {
            TrackSDK.trackAdImpression(
                adType,
                posId,
                sdkName,
                adId,
                adSource,
                isVisiblePct.toFloat(),
                scene,
            )
        }
        AppLog.tracking.d(
            "埋点已提交 平台=tracksdk 事件=trackAdImpression 参数=adType=$adType, posId=$posId, sdk=$sdkName, adId=$adId, adSource=$adSource, visible=$isVisiblePct, scene=$scene",
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
        runCatching(block).onFailure { AppLog.tracking.w("埋点提交失败 平台=tracksdk 事件=$name", it) }
    }
}

/** 单次广告请求的关联上下文，`adId` 贯穿请求到关闭。 */
class AdTrackLife(
    val adId: String,
    val adType: String,
    val posId: String,
    val requestStartMs: Long,
    var adSource: String = AdTrackingHelper.SOURCE_ADMOB,
    var showStartMs: Long = 0L,
)
