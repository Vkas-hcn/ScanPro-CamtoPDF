package com.bockcock.adjust

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.buncead.common.util.Util
import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy
import com.mymy.mymy.SaveKv
import com.zppko.pdfplus.nor.remote.ConfigHelper
import com.zppko.pdfplus.nor.remote.RemoteConfigHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Pin {

    val TAG = "Pin"

    // adjust

//    val ad_impression = "ad_impression"// FirebaseAnalytics.Event.AD_IMPRESSION
    val ad_impression_revenue = "ad_impression_revenue"
    val total_ads_revenue_001 = "total_ads_revenue_001"

    val  ntf_perm_no = "ntf_perm_no"
    val  ntf_perm_ok = "ntf_perm_ok"

    val  trigger_scanpdf_ntf = "trigger_scanpdf_ntf"
    val  click_scanpdf_ntf = "click_scanpdf_ntf"
    val  trigger_imgpdf_ntf = "trigger_imgpdf_ntf"
    val  click_imgpdf_ntf = "click_imgpdf_ntf"
    val  trigger_depdf_ntf = "trigger_depdf_ntf"
    val  click_depdf_ntf = "click_depdf_ntf"

    val  trigger_media_ntf = "trigger_media_ntf"
    val  click_media_ntf = "click_media_ntf"

    val  ntf_top_trigger = "ntf_top_trigger"
    val  ntf_top_click = "ntf_top_click"

    fun event(eventName: String,pt:Pt?=null){
        // Util.iii { "$TAG event, eventName=${eventName}, pt=${pt}" }
        FirebaseAna.logEvent(eventName,pt?.bundle)
        AdJustHelp.event(eventName,pt?.map)
//
        TrackSDK.track(eventName,pt?.map?.toMap(), TrackPolicy.IMMEDIATE)
    }


    fun myTrack(eventName: String, properties:MutableMap<String,Any>,policy: TrackPolicy ){
        TrackSDK.track(eventName, properties, TrackPolicy.IMMEDIATE)
    }

    fun anaMUSer(canShowAdsss_a2: Boolean){
        if (SaveKv.isAlrMUser()){

        }else{
            if (canShowAdsss_a2){
                SaveKv.setAlrMUser()
                Pin.event(Pin.activated)
            }
        }
    }

    fun anaMUSer2(){
        if (SaveKv.isAlrMUser()){

        }else{
            val canShowAdsss_a2 = ConfigHelper.canShowAdsss_a2()
            if (canShowAdsss_a2){
                SaveKv.setAlrMUser()
                Pin.event(Pin.activated)
            }
        }
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


    fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }

    private fun firebaseHeader(context: Context): Map<String, Any> {
        val appState = if (isAppInForeground()) {
            1
        }else{
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