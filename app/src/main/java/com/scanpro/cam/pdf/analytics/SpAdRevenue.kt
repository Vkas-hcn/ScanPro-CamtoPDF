package com.scanpro.cam.pdf.analytics

import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.AdValue
import com.google.firebase.analytics.FirebaseAnalytics
import com.scanpro.cam.pdf.util.AppLog

/**
 * Firebase Taichi / ARO ad revenue events.
 *
 * Event names must match Firebase conversion setup exactly.
 */
object SpAdRevenue {

    /** Per-impression revenue for Taichi. */
    const val EVENT_AD_IMPRESSION_REVENUE = "Ad_Impression_Revenue"

    /** Accumulated revenue (≥ 0.01 USD) for Taichi tROAS. */
    const val EVENT_TOTAL_ADS_REVENUE_001 = "Total_Ads_Revenue_001"

    fun trackImpressionEarn(
        context: Context,
        usd: Double,
        precisionType: Int? = null,
    ) {
        val params = Bundle().apply {
            putDouble(FirebaseAnalytics.Param.VALUE, usd)
            putString(FirebaseAnalytics.Param.CURRENCY, "USD")
            precisionType?.let { putString("precisionType", precisionLabel(it)) }
        }
        runCatching {
            val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
            analytics.logEvent(EVENT_AD_IMPRESSION_REVENUE, params)
            val precision = precisionType?.let { ", precisionType=${precisionLabel(it)}" }.orEmpty()
            AppLog.tracking.i("埋点已提交 平台=firebase 事件=$EVENT_AD_IMPRESSION_REVENUE 参数=value=$usd, currency=USD$precision")
        }.onFailure {
            AppLog.tracking.w("埋点提交失败 平台=firebase 事件=$EVENT_AD_IMPRESSION_REVENUE", it)
        }
        SpTracker.mirrorToTrackSdk(EVENT_AD_IMPRESSION_REVENUE, params)
    }

    fun trackTotalEarn(context: Context, usd: Double) {
        val params = Bundle().apply {
            putDouble(FirebaseAnalytics.Param.VALUE, usd)
            putString(FirebaseAnalytics.Param.CURRENCY, "USD")
        }
        runCatching {
            val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
            analytics.logEvent(EVENT_TOTAL_ADS_REVENUE_001, params)
            AppLog.tracking.i("埋点已提交 平台=firebase 事件=$EVENT_TOTAL_ADS_REVENUE_001 参数=value=$usd, currency=USD")
        }.onFailure {
            AppLog.tracking.w("埋点提交失败 平台=firebase 事件=$EVENT_TOTAL_ADS_REVENUE_001", it)
        }
        SpTracker.mirrorToTrackSdk(EVENT_TOTAL_ADS_REVENUE_001, params)
    }

    private fun precisionLabel(precisionType: Int): String = when (precisionType) {
        AdValue.PrecisionType.UNKNOWN -> "UNKNOWN"
        AdValue.PrecisionType.ESTIMATED -> "ESTIMATED"
        AdValue.PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER_PROVIDED"
        AdValue.PrecisionType.PRECISE -> "PRECISE"
        else -> "Invalid"
    }
}
