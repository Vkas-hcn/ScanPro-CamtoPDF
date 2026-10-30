package com.scanpro.cam.pdf.config

import android.content.Context
import com.scanpro.cam.pdf.BuildConfig
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpAdPosition
import com.scanpro.cam.pdf.util.AppLog

/**
 * 买量配置接口 `api/dispatch` 的接入点。
 *
 * 行为对齐 EfileScan `SdkConfigLoader`：Base URL 必须 `https://` 开头、`/` 结尾；
 * 8 个请求字段 Key 必须非空、互不重复、且不是 `REPLACE_KEY`。
 * 校验不通过时返回 null（不崩溃），配置模块整体跳过。
 *
 * 响应业务 key 照抄 ApolloPDF `remote/RemoteConfig.java`。
 */
data class SpConfigRequestFields(
    val packageName: String,
    val versionName: String,
    val distinctId: String,
    val gaid: String,
    val deviceModel: String,
    val installReferrer: String,
    val language: String,
    val osVersion: String,
) {
    fun asList(): List<String> = listOf(
        packageName,
        versionName,
        distinctId,
        gaid,
        deviceModel,
        installReferrer,
        language,
        osVersion,
    )
}

data class SpDispatchWire(
    val baseUrl: String,
    val environment: String,
    val requestFields: SpConfigRequestFields,
)

object SpConfigWire {

    const val PATH_DISPATCH = "api/dispatch"
    const val PATH_CLOAK = "api/cloak"

    const val PLACEHOLDER_KEY = "REPLACE_KEY"

    // region 响应字段（key 名照抄 Apollo RemoteConfig）

    const val KEY_ADS = "ac_ad_config"
    const val KEY_NAT_CONFIG = "ac_nat_config"
    const val KEY_NEW_CONFIG = "new_config"
    const val KEY_AC_POP_CONFIG = "acpop_config"
    const val KEY_POP_REFRESH = "pop_refresh"
    const val KEY_MEDIA_CONFIG = "media_config"
    const val KEY_WIN_POP_CONFIG = "win_pop_config"

    /** 与广告配置同一层，不在 `ac_ad_config` 里面。 */
    const val KEY_FACEBOOK_APP_ID = "facebook_app_id"
    const val KEY_FACEBOOK_CLIENT_TOKEN = "client_token"
    const val KEY_IS_ACQUIRED_USER = "isAcquiredUser"

    const val KEY_ADS_LIMIT = "ads_limit"
    const val KEY_AC_ADS_LIMIT = "ac_ads_limit"
    const val KEY_GLOBAL_BLOCK = "global_block"
    const val KEY_GLOBAL_BLOCK_SWITCH = "global_block_switch"
    const val KEY_GOOGLE_PLAY_BLOCK = "google_play_block"
    const val KEY_VPN_CONFIG_SWITCH = "vpn_config_switch"
    const val KEY_VIRTUAL_BLOCK_SWITCH = "virtual_block_switch"

    const val KEY_AD_UNIT_ID = "ad_unit_id"
    const val KEY_AD_PLATFORM = "ad_paltfrom"
    const val KEY_AD_TYPE = "ad_type"
    const val KEY_AD_TIME_LIMIT = "ad_timelimit"

    /** 开屏展示时序（可选块，缺项走 [com.scanpro.cam.pdf.ads.SpAdShowConfig] 默认值）。 */
    const val KEY_AD_SHOW_CONFIG = "ad_show_config"
    const val KEY_LAUNCH_MIN_MS = "launch_min_ms"
    const val KEY_LAUNCH_WAIT_MS = "launch_wait_ms"
    const val KEY_CATCHUP_MS = "catchup_ms"
    const val KEY_PRELOAD_ON_BACKGROUND = "preload_on_background"
    const val KEY_CATCHUP_ENABLED = "catchup_enabled"

    const val KEY_NAT_SWITCH_OPEN = "switch_open"
    const val KEY_NAT_JUMP_PERCENT = "jump_percent"

    const val KEY_AC_ON = "ac_on"
    const val KEY_AC_HOME = "ac_home"
    const val KEY_AC_HOME_LIMIT = "ac_home_limit"
    const val KEY_HOME_DELAY = "home_delay"
    const val KEY_AC_EXIT = "ac_exit"
    const val KEY_AC_EXIT_LIMIT = "ac_exit_limit"
    const val KEY_EXIT_DELAY = "exit_delay"
    const val KEY_AC_RECENT = "ac_recent"
    const val KEY_AC_RECENT_LIMIT = "ac_recent_limit"
    const val KEY_RECENT_DELAY = "recent_delay"
    const val KEY_AC_ADCLICK = "ac_adclick"
    const val KEY_AC_ADCLICK_LIMIT = "ac_adclick_limit"
    const val KEY_ADCLICK_DELAY = "adclick_delay"

    const val KEY_NO_POP_START = "nopop_start"
    const val KEY_NO_POP_END = "nopop_end"
    const val KEY_AC_T = "ac_t"
    const val KEY_AC_T_LIMIT = "ac_t_limit"
    const val KEY_AC_U = "ac_u"
    /** `AcPopConfig` 的 SerializedName 是小写 l；`ac_u_Limit` 是 Apollo DefaultConfig 的笔误，解析时两种都认。 */
    const val KEY_AC_U_LIMIT = "ac_u_limit"
    const val KEY_AC_U_LIMIT_LEGACY = "ac_u_Limit"
    const val KEY_AC_A = "ac_a"
    const val KEY_AC_A_LIMIT = "ac_a_limit"

    const val KEY_POP_REFRESH_SWITCH = "pop_refresh_switch"
    const val KEY_POP_REFRESH_TIMES = "times"
    const val KEY_POP_REFRESH_INTERVAL = "interval"

    const val KEY_MEDIA_SWITCH = "switch"
    const val KEY_MEDIA_INTERVAL_TIME = "intervalTime"

    const val KEY_WIN_POP_FIRST_INTERVAL = "first_interval"
    const val KEY_WIN_POP_JUMP_SWITCH = "winpop_jump_switch"
    // endregion

    fun load(context: Context): SpDispatchWire? {
        val app = context.applicationContext
        val baseUrl = runCatching { app.getString(R.string.sp_dispatch_base_url).trim() }
            .getOrDefault("")
        val fields = SpConfigRequestFields(
            packageName = stringOrEmpty(app, R.string.sp_dispatch_request_package),
            versionName = stringOrEmpty(app, R.string.sp_dispatch_request_version),
            distinctId = stringOrEmpty(app, R.string.sp_dispatch_request_distinct_id),
            gaid = stringOrEmpty(app, R.string.sp_dispatch_request_gaid),
            deviceModel = stringOrEmpty(app, R.string.sp_dispatch_request_device_model),
            installReferrer = stringOrEmpty(app, R.string.sp_dispatch_request_referrer),
            language = stringOrEmpty(app, R.string.sp_dispatch_request_language),
            osVersion = stringOrEmpty(app, R.string.sp_dispatch_request_os_version),
        )
        val environment = if (BuildConfig.DEBUG) "staging" else "prod"
        val wire = SpDispatchWire(baseUrl, environment, fields)
        val reason = validate(wire) ?: return wire
        AppLog.network.w("配置接口未配置 原因=$reason")
        return null
    }

    fun isConfigured(context: Context): Boolean = load(context) != null

    fun isKnownPosition(position: String): Boolean = SpAdPosition.isKnown(position)

    private fun validate(wire: SpDispatchWire): String? {
        if (!wire.baseUrl.startsWith("https://") || !wire.baseUrl.endsWith("/")) {
            return "base url must be https and end with /"
        }
        val keys = wire.requestFields.asList()
        if (keys.any { it.isBlank() || it == PLACEHOLDER_KEY || it.startsWith("REPLACE_") }) {
            return "request keys are placeholder or blank"
        }
        if (keys.distinct().size != keys.size) {
            return "request keys must be unique"
        }
        return null
    }

    private fun stringOrEmpty(context: Context, id: Int): String =
        runCatching { context.getString(id).trim() }.getOrDefault("")
}
