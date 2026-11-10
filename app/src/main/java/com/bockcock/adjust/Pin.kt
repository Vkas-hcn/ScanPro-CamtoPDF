package com.bockcock.adjust

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy
import com.google.firebase.analytics.FirebaseAnalytics
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.scanpro.cam.pdf.util.AppLog
import com.zppko.pdfplus.nor.Zppkoo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Pin {

    val TAG = "Pin"

    // adjust

    val ad_impression_revenue = "ad_impression_revenue"
    val total_ads_revenue_001 = "total_ads_revenue_001"

    val ntf_perm_no = "ntf_perm_no"
    val ntf_perm_ok = "ntf_perm_ok"

    val trigger_scanpdf_ntf = "trigger_scanpdf_ntf"
    val click_scanpdf_ntf = "click_scanpdf_ntf"
    val trigger_imgpdf_ntf = "trigger_imgpdf_ntf"
    val click_imgpdf_ntf = "click_imgpdf_ntf"
    val trigger_depdf_ntf = "trigger_depdf_ntf"
    val click_depdf_ntf = "click_depdf_ntf"

    val trigger_media_ntf = "trigger_media_ntf"
    val click_media_ntf = "click_media_ntf"

    val ntf_top_trigger = "ntf_top_trigger"
    val ntf_top_click = "ntf_top_click"

    fun event(eventName: String, pt: Pt? = null) {
        val text = pt?.map?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: "无"
        // Reuse B's initialized Firebase/TrackSDK/Adjust instances and B's token mapping.
        // B's BI-only product contract overrides A's unconditional three-platform fan-out.
        if (eventName !in biOnlyEvents) {
            runCatching {
                FirebaseAnalytics.getInstance(Zppkoo.ii).logEvent(eventName, pt?.bundle)
                AppLog.tracking.i("埋点已提交 平台=firebase 事件=$eventName 参数=$text")
            }.onFailure {
                AppLog.tracking.w("埋点提交失败 平台=firebase 事件=$eventName 参数=$text", it)
            }
            AdTrackingHelper.adjustPoint(
                eventName,
                pt?.map?.get("revenue")?.toDoubleOrNull(),
                pt?.map?.get("currency"),
            )
        }
        // Do not call SpTracker.log here: it would mirror a duplicate BATCH event.
        // A's immediate TrackSDK policy and string-valued parameter map are retained.
        runCatching {
            TrackSDK.track(eventName, pt?.map?.toMap() ?: emptyMap(), TrackPolicy.IMMEDIATE)
            AppLog.tracking.i("埋点已提交 平台=tracksdk 事件=$eventName 参数=$text")
        }.onFailure {
            AppLog.tracking.w("埋点提交失败 平台=tracksdk 事件=$eventName 参数=$text", it)
        }
    }

    fun myTrack(eventName: String, properties: MutableMap<String, Any>, policy: TrackPolicy) {
        // A deliberately uses IMMEDIATE regardless of the supplied policy.
        // B TrackSDK 3.x accepts string-valued properties.
        TrackSDK.track(eventName, properties.mapValues { it.value.toString() }, TrackPolicy.IMMEDIATE)
    }

    val activated = "activated"
    val biorganic = "biorganic"

    // 通知事件
    val notification_trigger = "notification_trigger"
    val notification_show = "notification_show"
    val notification_click = "notification_click"

    // 拉活事件
    val reactivation_landing = "reactivation_landing"

    // 保活事件
    val keepalive_service_start = "keepalive_service_start"
    val keepalive_trigger = "keepalive_trigger"
    val keepalive_service_stop = "keepalive_service_stop"

    private val biOnlyEvents = setOf(
        activated, biorganic, keepalive_service_start, keepalive_trigger,
        keepalive_service_stop, reactivation_landing, "notification_received",
        "notification_frequency_limited", "ad_blocked", "ad_show_timeout",
    )

    fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }

    private fun firebaseHeader(context: Context): Map<String, Any> {
        val appState = if (isAppInForeground()) {
            1
        } else {
            2
        }

        val version = runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName.orEmpty().ifBlank { "0" }
        }.getOrDefault("0")
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val distinctId = androidId.ifBlank { TrackSDK.getInstance().effectiveDistinctId }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        return mapOf(
            "distinct_id" to distinctId,
            "device_model" to (Build.MODEL ?: ""),
            "manufacturer" to (Build.MANUFACTURER ?: ""),
            "app_version" to version,
            "os_version" to (Build.VERSION.RELEASE ?: ""),
            "app_package" to context.packageName,
            "language" to Locale.getDefault().toLanguageTag(),
            "app_state" to appState,
            "event_time" to time,
        )
    }
}
