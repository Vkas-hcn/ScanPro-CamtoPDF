package com.scanpro.cam.pdf.analytics

import android.content.Context
import android.os.Bundle
import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy
import com.google.firebase.analytics.FirebaseAnalytics
import com.scanpro.cam.pdf.util.AppLog

/**
 * 产品埋点。Firebase 仍是原入口；同一事件、同一参数再同步一份到 TrackSDK。
 * 广告收入事件带 `value` / `currency` / `network`（ApolloPDF `AdIncomeReport` 的口径）。
 */
object SpTracker {

    fun log(context: Context, event: String) {
        if (event.isBlank()) return
        runCatching {
            FirebaseAnalytics.getInstance(context.applicationContext)
                .logEvent(event, null)
            AppLog.tracking.i("埋点已提交 平台=firebase 事件=$event 参数=无")
        }.onFailure {
            AppLog.tracking.w("埋点提交失败 平台=firebase 事件=$event", it)
        }
        mirrorToTrackSdk(event, null)
    }

    /** 带参数上报。Firebase 参数原样再交给 TrackSDK。 */
    fun log(context: Context, event: String, params: Bundle?) {
        if (event.isBlank()) return
        val text = params.toLogText()
        runCatching {
            FirebaseAnalytics.getInstance(context.applicationContext).logEvent(event, params)
            AppLog.tracking.i("埋点已提交 平台=firebase 事件=$event 参数=$text")
        }.onFailure {
            AppLog.tracking.w("埋点提交失败 平台=firebase 事件=$event 参数=$text", it)
        }
        mirrorToTrackSdk(event, params)
    }

    /**
     * 与 Firebase 同一事件名、同一 Bundle。
     * ponytail: TrackSDK.track 只收 Map<String, String>（与 EfileScan BiTracker 相同），
     * 数值用 toString() 原样写入，不改键名。
     */
    internal fun mirrorToTrackSdk(event: String, params: Bundle?) {
        if (event.isBlank()) return
        val text = params.toLogText()
        runCatching {
            TrackSDK.track(event, params.toTrackParams(), TrackPolicy.BATCH)
            AppLog.tracking.i("埋点已提交 平台=tracksdk 事件=$event 参数=$text")
        }.onFailure {
            AppLog.tracking.w("埋点提交失败 平台=tracksdk 事件=$event 参数=$text", it)
        }
    }

    private fun Bundle?.toLogText(): String {
        val map = toTrackParams()
        if (map.isEmpty()) return "无"
        return map.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }

    private fun Bundle?.toTrackParams(): Map<String, String> {
        if (this == null || isEmpty) return emptyMap()
        val map = LinkedHashMap<String, String>(size())
        for (key in keySet()) {
            when (val value = get(key)) {
                null -> Unit
                is String -> map[key] = value
                else -> map[key] = value.toString()
            }
        }
        return map
    }

    // region Notification permission (enable / disable)
    /** User enabled notification permission. */
    const val OPEN_AUTH_NTF = "open_auth_ntf"
    /** User disabled / denied notification permission. */
    const val CLOSE_AUTH_NTF = "close_auth_ntf"
    /** 首次观察到「通知已授权」，只报一次（Apollo `InsertEvent.SYSTEM_NOTIFICATION_ALLOW`）。 */
    const val SYSTEM_NOTIFICATION_ALLOW = "system_notification_allow"

    /** 通知授权弹窗弹出（Apollo `InsertEvent.SYSTEM_NOTIFICATION_POPUP_VIEW`）。 */
    const val SYSTEM_NOTIFICATION_POPUP_VIEW = "system_notification_popup_view"

    fun logNotifAuth(context: Context, granted: Boolean) {
        log(context, if (granted) OPEN_AUTH_NTF else CLOSE_AUTH_NTF)
    }
    // endregion

    // region Permanent dock (常驻通知)
    const val TRIGGER_TOP_NTF = "trigger_top_ntf"
    const val CLICK_TOP_NTF = "click_top_ntf"
    /** 常驻（前台服务）通知发出（Apollo `InsertEvent.ALWAYS_NOTIFICATION_TRIGGER`）。 */
    const val ALWAYS_NOTIFICATION_TRIGGER = "always_notification_trigger"
    /** 普通提醒发出，带 `type` 参数（Apollo `InsertEvent.SYSTEM_NOTIFICATION_TRIGGER`）。 */
    const val SYSTEM_NOTIFICATION_TRIGGER = "system_notification_trigger"
    /** 媒体式提醒发出，带 `type` 参数（Apollo `InsertEvent.MEDIA_NOTIFICATION_TRIGGER`）。 */
    const val MEDIA_NOTIFICATION_TRIGGER = "media_notification_trigger"

    /** 普通提醒被点，带 `type` 参数（Apollo `InsertEvent.SYSTEM_NOTIFICATION_CLICK`）。 */
    const val SYSTEM_NOTIFICATION_CLICK = "system_notification_click"
    /** 媒体式提醒被点，带 `type` 参数（Apollo `InsertEvent.MEDIA_NOTIFICATION_CLICK`）。 */
    const val MEDIA_NOTIFICATION_CLICK = "media_notification_click"
    /** 常驻通知被点（Apollo `InsertEvent.ALWAYS_NOTIFICATION_CLICK`）。 */
    const val ALWAYS_NOTIFICATION_CLICK = "always_notification_click"
    // endregion

    // region Regular cue notifications (普通通知)
    /** Aggregate count for every cue show. */
    const val TRIGGER_NOTIF_COUNT_NTF = "trigger_notif_count_ntf"
    /** Aggregate count for every cue click. */
    const val CLICK_NOTIF_COUNT_NTF = "click_notif_count_ntf"

    const val TRIGGER_SCAN_PDF_NTF = "trigger_scan_pdf_ntf"
    const val CLICK_SCAN_PDF_NTF = "click_scan_pdf_ntf"
    const val TRIGGER_IMG_PDF_NTF = "trigger_img_pdf_ntf"
    const val CLICK_IMG_PDF_NTF = "click_img_pdf_ntf"
    const val TRIGGER_DOC_PDF_NTF = "trigger_doc_pdf_ntf"
    const val CLICK_DOC_PDF_NTF = "click_doc_pdf_ntf"
    const val TRIGGER_PDF_ONE_NTF = "trigger_pdf_one_ntf"
    const val CLICK_PDF_ONE_NTF = "click_pdf_one_ntf"
    const val TRIGGER_PDF_TWO_NTF = "trigger_pdf_two_ntf"
    const val CLICK_PDF_TWO_NTF = "click_pdf_two_ntf"
    const val TRIGGER_PDF_THREE_NTF = "trigger_pdf_three_ntf"
    const val CLICK_PDF_THREE_NTF = "click_pdf_three_ntf"

    /** Returns trigger/click event pair for a cue creative id, or null if unknown. */
    fun cueTemplateEvents(templateId: String?): Pair<String, String>? = when (templateId) {
        "scan_pdf" -> TRIGGER_SCAN_PDF_NTF to CLICK_SCAN_PDF_NTF
        "photos" -> TRIGGER_IMG_PDF_NTF to CLICK_IMG_PDF_NTF
        "ready" -> TRIGGER_DOC_PDF_NTF to CLICK_DOC_PDF_NTF
        "dont_lose" -> TRIGGER_PDF_ONE_NTF to CLICK_PDF_ONE_NTF
        "receipts" -> TRIGGER_PDF_TWO_NTF to CLICK_PDF_TWO_NTF
        "send_doc" -> TRIGGER_PDF_THREE_NTF to CLICK_PDF_THREE_NTF
        else -> null
    }
    // endregion

    // region Cue skip diagnostics (internal; not in product sheet)
    const val CUE_SKIP_FREQ = "cue_skip_freq"
    const val CUE_SKIP_FOREGROUND = "cue_skip_foreground"
    const val CUE_SKIP_KOREA_SAMSUNG = "cue_skip_korea_samsung"
    const val CUE_SKIP_MASTER_OFF = "cue_skip_master_off"
    const val CUE_SKIP_HOUR_WINDOW = "cue_skip_hour_window"
    const val CUE_SKIP_DEDUPE = "cue_skip_dedupe"
    // endregion

    // region FCM
    const val FCM_MESSAGE_RECEIVED = "fcm_message_received"
    const val FCM_TOPIC_SUBSCRIBE_OK = "fcm_topic_subscribe_ok"
    // endregion

    // region Ads
    /** SDK 初始化完成（本工程自己的探针；Apollo 无对应事件）。 */
    const val AD_SDK_READY = "ad_sdk_ready"
    /** 展示级收入，唯一入口，带 value / currency / network 参数（Apollo `AdIncomeReport`）。 */
    const val AD_IMPRESSION_REVENUE = "ad_impression_revenue"

    // Apollo `report/InsertEvent.java` 的事件名，原样照抄
    const val SHARK_AD_CHANCE = "Shark_ad_chance"
    const val SHARK_AD_PLACEMENT_REQUEST = "Shark_ad_placement_request"
    const val SHARK_AD_SOURCE_REQUEST = "Shark_ad_source_request"
    const val SHARK_AD_SOURCE_ERROR = "Shark_ad_source_error"
    const val SHARK_AD_IMPRESSION = "Shark_ad_impression"
    const val SHARK_AD_IMPRESSION_ERROR = "Shark_ad_impression_error"
    const val SHARK_AD_CLICK = "Shark_ad_click"
    const val SHARK_AD_CLOSE = "Shark_ad_close"

    const val AD_SHOW_OPEN = "ad_show_splash"
    const val AD_SHOW_INTERSTITIAL = "ad_show_interstitial"
    const val AD_SHOW_BANNER = "ad_show_banner"
    const val AD_SKIP_TUNNEL = "ad_skip_vpn"
    const val AD_SKIP_CONSENT = "ad_skip_ump"
    const val AD_SKIP_CAP = "ad_skip_freq"
    const val CONSENT_FALLBACK = "ump_fallback"
    const val HOT_START_SPLASH_SHOW = "hot_start_splash_show"
    // endregion

    // region Floating window (悬浮窗)
    const val TRIGGER_FLOAT_POPUP = "trigger_float_popup"
    const val CLICK_FLOAT_ENTER = "click_float_enter"
    const val CLICK_FLOAT_DISMISS = "click_float_dismiss"
    const val FLOAT_SKIP_MASTER_OFF = "float_skip_master_off"
    const val FLOAT_SKIP_NOT_PAID = "float_skip_not_paid"
    const val FLOAT_SKIP_NO_OVERLAY = "float_skip_no_overlay"
    const val FLOAT_SKIP_FOREGROUND = "float_skip_foreground"
    const val FLOAT_SKIP_HOUR_WINDOW = "float_skip_hour_window"
    // endregion

    // region App / product funnel
    const val APP_COLD_BOOT = "app_open_cold"
    const val APP_WARM_RESUME = "app_open_hot"
    const val STORAGE_PERMISSION_GRANT = "storage_permission_grant"
    const val STORAGE_PERMISSION_DENY = "storage_permission_deny"
    const val SCAN_START = "scan_start"
    const val SCAN_FINISH = "scan_finish"
    const val CONVERT_SUCCESS = "convert_success"

    // region Rating
    const val RATING_DIALOG_SHOW = "rating_dialog_show"
    const val RATING_SUBMIT_LOW = "rating_submit_low"
    const val RATING_SUBMIT_HIGH = "rating_submit_high"
    // endregion

    const val USER_TYPE_PAID = "user_type_paid"
    const val USER_TYPE_ORGANIC = "user_type_organic"
    const val ADJUST_ATTRIBUTION_UPDATED = "adjust_attribution_updated"
    /** Prefix for sanitized Adjust network event (keep total name ≤ 40). */
    const val ADJUST_NETWORK_PREFIX = "adj_net_"
    const val ADJUST_CAMPAIGN_PREFIX = "adj_camp_"

    /** 每次发起 `api/dispatch` 前上报（EfileScan `biconfig_request`）。 */
    const val BICONFIG_REQUEST = "biconfig_request"

    /** 下发配置 `isAcquiredUser == true` 时上报一次。 */
    const val ACTIVATED = "activated"
    // endregion
}
