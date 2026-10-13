package com.scanpro.cam.pdf.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdValue
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Firebase Taichi / ARO ad revenue events.
 *
 * Event names must match Firebase conversion setup exactly.
 */
object SpAdRevenue {

    private const val TAG = "SpAdRevenue"

    /** Per-impression revenue for Taichi. */
    const val EVENT_AD_IMPRESSION_REVENUE = "Ad_Impression_Revenue"

    /** Accumulated revenue (≥ 0.01 USD) for Taichi tROAS. */
    const val EVENT_TOTAL_ADS_REVENUE_001 = "Total_Ads_Revenue_001"

    fun trackImpressionEarn(
        context: Context,
        usd: Double,
        precisionType: Int? = null,
    ) {
        runCatching {
            val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
            val params = Bundle().apply {
                putDouble(FirebaseAnalytics.Param.VALUE, usd)
                putString(FirebaseAnalytics.Param.CURRENCY, "USD")
                precisionType?.let { putString("precisionType", precisionLabel(it)) }
            }
            analytics.logEvent(EVENT_AD_IMPRESSION_REVENUE, params)
            Log.i(TAG, "$EVENT_AD_IMPRESSION_REVENUE usd=$usd")
        }.onFailure {
            Log.w(TAG, "log $EVENT_AD_IMPRESSION_REVENUE failed", it)
        }
    }

    fun trackTotalEarn(context: Context, usd: Double) {
        runCatching {
            val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
            val params = Bundle().apply {
                putDouble(FirebaseAnalytics.Param.VALUE, usd)
                putString(FirebaseAnalytics.Param.CURRENCY, "USD")
            }
            analytics.logEvent(EVENT_TOTAL_ADS_REVENUE_001, params)
            Log.i(TAG, "$EVENT_TOTAL_ADS_REVENUE_001 usd=$usd")
        }.onFailure {
            Log.w(TAG, "log $EVENT_TOTAL_ADS_REVENUE_001 failed", it)
        }
    }

    private fun precisionLabel(precisionType: Int): String = when (precisionType) {
        AdValue.PrecisionType.UNKNOWN -> "UNKNOWN"
        AdValue.PrecisionType.ESTIMATED -> "ESTIMATED"
        AdValue.PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER_PROVIDED"
        AdValue.PrecisionType.PRECISE -> "PRECISE"
        else -> "Invalid"
    }
}
