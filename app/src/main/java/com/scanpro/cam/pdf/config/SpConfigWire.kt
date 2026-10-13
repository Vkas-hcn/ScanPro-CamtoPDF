package com.scanpro.cam.pdf.config

import com.scanpro.cam.pdf.ads.SpAdPosition

/**
 * 中台「配置下发」接入点。
 *
 * ⚠️ 网络部分为占位。按《中台需求：配置下发平台》的设计，**每个包名 + 域名生成一份接入文档，
 * 文档内参数都是随机字符串**。ScanPro 的接入文档尚未提供，[BASE_URL] / [PATH] 必须在拿到
 * 文档后替换，不许照抄其它包的域名或混淆字段。
 *
 * 依据：`prd/中台产品文档/中台需求/config接入PRD.md`（请求逻辑、缓存、重试、混淆字段）
 *
 * **业务 key 不是自创的**：下面 response 区的 key 逐个取自 ApolloPDF 的远端配置，
 * 出处 `data/apks/apollo/sources/sources/com/zj/apollopdf/remote/RemoteConfig.java`。
 * 中台只要原样透传这组 key 即可，客户端解析不用改。
 */
object SpConfigWire {

    /**
     * 配置接口 Base URL，必须以 `/` 结尾。
     * 占位：等 ScanPro 的接入文档。
     */
    const val BASE_URL = ""

    /** 配置接口路径（接入文档给）。 */
    const val PATH = ""

    // region 请求字段（混淆名由接入文档给；下表仅说明语义）
    /**
     * 映射后端字段 → 混淆名。接入文档到手后逐项替换。
     *
     * | 后端字段 | 语义 | 必传 |
     * |---|---|---|
     * | appName | 产品 applicationId | true |
     * | version | `versionName.versionCode` | true |
     * | distinctId | 与 BI / Adjust 同一个设备标识 | true |
     * | gaid | Google Advertising ID | false |
     * | phoneModel | Build.MODEL | false |
     * | refer | Install Referrer 原文 | true |
     * | language | 语言代码，如 EN | false |
     * | osVersion | 系统版本，如 13 | false |
     */
    const val KEY_REQ_APP_NAME = "appName"
    const val KEY_REQ_VERSION = "version"
    const val KEY_REQ_DISTINCT_ID = "distinctId"
    const val KEY_REQ_GAID = "gaid"
    const val KEY_REQ_PHONE_MODEL = "phoneModel"
    const val KEY_REQ_REFER = "refer"
    const val KEY_REQ_LANGUAGE = "language"
    const val KEY_REQ_OS_VERSION = "osVersion"
    // endregion

    // region 响应字段（key 名照抄 Apollo RemoteConfig）
    /** `ac_ad_config`：广告位配置。[KEY_ADS] 作为我们中台的容器名。 */
    const val KEY_ADS = "ac_ad_config"

    /** `ac_nat_config`：原生关闭键行为。 */
    const val KEY_NAT_CONFIG = "ac_nat_config"

    /** `new_config`：退后台通知策略。 */
    const val KEY_NEW_CONFIG = "new_config"

    /** `acpop_config`：广告点击弹窗策略。 */
    const val KEY_AC_POP_CONFIG = "acpop_config"

    /** `pop_refresh`：通知自刷新。 */
    const val KEY_POP_REFRESH = "pop_refresh"

    /** `media_config`：通知渠道开关。 */
    const val KEY_MEDIA_CONFIG = "media_config"

    /** `win_pop_config`：窗口弹窗误触。 */
    const val KEY_WIN_POP_CONFIG = "win_pop_config"

    // 广告位条目的字段（Apollo `ad/AdInfo.java` 的 @SerializedName）
    const val KEY_AD_UNIT_ID = "ad_unit_id"
    const val KEY_AD_PLATFORM = "ad_paltfrom"
    const val KEY_AD_TYPE = "ad_type"
    const val KEY_AD_TIME_LIMIT = "ad_timelimit"

    // 原生配置字段（Apollo `ad/NatConfig.java`）
    const val KEY_NAT_SWITCH_OPEN = "switch_open"
    const val KEY_NAT_JUMP_PERCENT = "jump_percent"

    // 通知配置字段（Apollo `notice/NewConfig.java` 等）
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
    /** Apollo 默认值是 `ac_u_Limit`（大写 L），解析时两种写法都认。 */
    const val KEY_AC_U_LIMIT = "ac_u_Limit"
    const val KEY_AC_A = "ac_a"
    const val KEY_AC_A_LIMIT = "ac_a_limit"

    const val KEY_POP_REFRESH_SWITCH = "pop_refresh_switch"
    const val KEY_POP_REFRESH_TIMES = "times"
    const val KEY_POP_REFRESH_INTERVAL = "interval"

    const val KEY_MEDIA_SWITCH = "switch"
    const val KEY_MEDIA_INTERVAL_TIME = "intervalTime"

    const val KEY_WIN_POP_FIRST_INTERVAL = "first_interval"
    const val KEY_WIN_POP_JUMP_SWITCH = "winpop_jump_switch"

    /** 允许空串的校验：Base URL 未配置时整个 config 模块整体跳过，不请求、不报错。 */
    fun isConfigured(): Boolean = BASE_URL.isNotBlank() && PATH.isNotBlank()

    /** 位置名做一次白名单校验，避免下发拼错的 key 被当成新位置。 */
    fun isKnownPosition(position: String): Boolean = SpAdPosition.isKnown(position)
    // endregion
}
