package com.scanpro.cam.pdf.notice

/**
 * 通知/触发相关的远端配置。**逐字段照抄 ApolloPDF `notice/` 下的五个配置类**，
 * 默认值取 `remote/DefaultConfig.java` 里的 `new_config` / `acpop_config` / `pop_refresh` /
 * `media_config`（samsung 变体不引入，见文末说明）。
 *
 * 出处：
 * - `data/apks/apollo/sources/sources/com/zj/apollopdf/notice/NewConfig.java`
 * - `notice/AcPopConfig.java`、`notice/PopRefresh.java`、`notice/Media2Config.java`、
 *   `notice/WinPopConfig.java`
 * - `data/apks/apollo/sources/sources/com/zj/apollopdf/remote/DefaultConfig.java`
 */

/**
 * 退后台触发通知的策略（Apollo `new_config`）。
 *
 * 每个场景三个量：冷却分钟数 `ac_*`（距上次发送至少隔这么久）、当日次数上限 `*_limit`、
 * 延迟秒数 `*_delay`（收到按键/切后台后等几秒再发）。
 */
data class SpNewConfig(
    /** `ac_on`：总开关，0 关闭全部退后台通知。 */
    val acOn: Int = 1,
    /** `ac_home`：Home 键场景冷却（分钟）。 */
    val acHome: Int = 30,
    val acHomeLimit: Int = 40,
    /** `home_delay`：Home 键后延迟（秒）。 */
    val homeDelay: Int = 3,
    val acExit: Int = 30,
    val acExitLimit: Int = 40,
    val exitDelay: Int = 3,
    val acRecent: Int = 30,
    val acRecentLimit: Int = 40,
    val recentDelay: Int = 5,
    val acAdClick: Int = 30,
    val acAdClickLimit: Int = 40,
    val adClickDelay: Int = 2,
)

/**
 * 广告点击后弹窗（Apollo `acpop_config`）。`ac_t/ac_u/ac_a` 是三种弹窗各自的冷分钟数，
 * `*_limit` 是当日上限；`nopop_start`~`nopop_end` 是免打扰时段（小时）。
 */
data class SpAcPopConfig(
    val noPopStart: Int = 24,
    val noPopEnd: Int = 6,
    val acOn: Int = 1,
    val acT: Int = 30,
    val acTLimit: Int = 30,
    val acU: Int = 5,
    val acULimit: Int = 40,
    val acA: Int = 30,
    val acALimit: Int = 40,
)

/**
 * 通知自刷新（Apollo `pop_refresh`）。用来把常驻通知反复重发，
 * 顶回状态栏最上面。
 */
data class SpPopRefresh(
    val popRefreshSwitch: Int = 1,
    /** `times`：一轮重发次数。 */
    val times: Int = 30,
    /** `interval`：两次重发间隔（秒）。 */
    val interval: Int = 2,
)

/** 通知渠道开关（Apollo `media_config`）。 */
data class SpMediaConfig(
    val switch: Int = 1,
    val intervalTime: Int = 30,
)

/** 窗口弹窗误触（Apollo `win_pop_config`）。 */
data class SpWinPopConfig(
    val firstInterval: Int = 0,
    val jumpPercent: Int = 0,
    val winPopJumpSwitch: Int = 0,
)
