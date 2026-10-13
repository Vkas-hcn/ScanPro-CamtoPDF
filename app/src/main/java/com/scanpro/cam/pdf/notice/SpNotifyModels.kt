package com.scanpro.cam.pdf.notice

/**
 * 通知/触发的三个基本模型。逐字段照抄 ApolloPDF：
 *
 * - [SpNotifyScene] ← `notice/NotifyScene.java`
 * - [SpJumpType] ← `notice/JumpType.java`
 * - [SpNotificationItem] ← `notice/NotificationItem.java`
 *
 * `sceneType` 是 Apollo `SendManager.getSceneTypeValue()` 的映射，埋点里当 `type` 参数用。
 */

/**
 * 触发场景。共 7 个，没有一个多余：
 *
 * | 场景 | 谁触发 |
 * |---|---|
 * | [UNLOCK] | `USER_PRESENT` / `USER_UNLOCKED` 广播 |
 * | [ALARM_CLOCK] | `AlarmManager` 到点回调 |
 * | [SCHEDULED] | 回前台后的定时轮询（4s 预热，之后每 60s） |
 * | [AD_CLICK] | 用户点了广告 |
 * | [HOME_CLICK] | Home 键 |
 * | [RECENT_CLICK] | 最近任务键 |
 * | [NO_PAGE_EXIST] | 退到桌面（没有页面了） |
 */
enum class SpNotifyScene(val sceneType: String) {
    UNLOCK("unlock"),
    ALARM_CLOCK("alarm"),
    SCHEDULED("time"),
    AD_CLICK("adclick"),
    HOME_CLICK("home"),
    RECENT_CLICK("recent"),
    NO_PAGE_EXIST("exit"),
}

/** 点通知以后落到哪个文件类型页。 */
enum class SpJumpType {
    WORD,
    EXCEL,
    PPT,
    PDF,
}

/**
 * 一条通知文案：点哪、正文说什么、按钮写什么。
 *
 * @param jump 点击后的跳转类型
 * @param textRes 正文文案资源（`nt_scheduled_*` / `nt_unlock_*` / `nt_alarm_*`）
 * @param buttonRes 按钮文案资源（统一 `R.string.sp_notify_action`）
 */
data class SpNotificationItem(
    val jump: SpJumpType,
    val textRes: Int,
    val buttonRes: Int,
)

/**
 * 通知点进来带的三个附加信息。出处：ApolloPDF `app/Constants.java`
 * （`JUMP_TYPE` / `FROM_TYPE` / `SCENE_TYPE`）。
 */
object SpNotifyExtras {

    /** 落到哪个文件类型页，值是 [SpJumpType] 的名字。 */
    const val JUMP_TYPE = "sp_jump_type"

    /** 从哪种通知点进来的：`sys_notify` / `media_notify` / `media_notify_2` / `persistent_notify`。 */
    const val FROM_TYPE = "sp_from_type"

    /** 触发场景，值是 [SpNotifyScene.sceneType]。 */
    const val SCENE_TYPE = "sp_scene_type"
}
