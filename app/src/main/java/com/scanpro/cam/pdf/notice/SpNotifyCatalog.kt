package com.scanpro.cam.pdf.notice

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.scanpro.cam.pdf.R

/**
 * 通知的「文案池 + 通知 ID 池」。逐项照抄 ApolloPDF `notice/NotifyConfigKt.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/notice/NotifyConfigKt.java`
 *
 * 两件事：
 * 1. **文案轮换**：三种普通通知（定时 / 解锁 / 闹钟）各 8 条文案，每次取「上一条的下一条」，
 *    顺序写进 SharedPreferences，重启后接着轮。索引**先加后用**（首次返回下标 1，不是 0），
 *    这是 Apollo 的原行为。
 * 2. **通知 ID 轮换**：每个场景三个 ID，发通知时挑一个「当前状态栏里没占用」的；
 *    三个都占着就退回最后一个。这样反复发不会被前一条顶掉、也不会堆成三条。
 */
object SpNotifyCatalog {

    private const val PREFS = "sp_notify_catalog"
    private const val KEY_LAST_CLOCK_INDEX = "last_clock_notify_index"
    private const val KEY_LAST_SCHEDULE_INDEX = "last_schedule_notify_index"
    private const val KEY_LAST_UNLOCK_INDEX = "last_unlock_notify_index"

    /** 定时场景文案（Apollo `scheduledArray`）。 */
    private val scheduledArray = arrayOf(
        item(SpJumpType.WORD, R.string.nt_scheduled_1),
        item(SpJumpType.WORD, R.string.nt_scheduled_2),
        item(SpJumpType.EXCEL, R.string.nt_scheduled_3),
        item(SpJumpType.EXCEL, R.string.nt_scheduled_4),
        item(SpJumpType.PPT, R.string.nt_scheduled_5),
        item(SpJumpType.PPT, R.string.nt_scheduled_6),
        item(SpJumpType.PDF, R.string.nt_scheduled_7),
        item(SpJumpType.PDF, R.string.nt_scheduled_8),
    )

    /** 解锁场景文案（Apollo `unlockArray`）。 */
    private val unlockArray = arrayOf(
        item(SpJumpType.WORD, R.string.nt_unlock_1),
        item(SpJumpType.WORD, R.string.nt_unlock_2),
        item(SpJumpType.EXCEL, R.string.nt_unlock_3),
        item(SpJumpType.EXCEL, R.string.nt_unlock_4),
        item(SpJumpType.PPT, R.string.nt_unlock_5),
        item(SpJumpType.PPT, R.string.nt_unlock_6),
        item(SpJumpType.PDF, R.string.nt_unlock_7),
        item(SpJumpType.PDF, R.string.nt_unlock_8),
    )

    /** 闹钟场景文案（Apollo `alarmClockArray`）。 */
    private val alarmClockArray = arrayOf(
        item(SpJumpType.WORD, R.string.nt_alarm_1),
        item(SpJumpType.WORD, R.string.nt_alarm_2),
        item(SpJumpType.EXCEL, R.string.nt_alarm_3),
        item(SpJumpType.EXCEL, R.string.nt_alarm_4),
        item(SpJumpType.PPT, R.string.nt_alarm_5),
        item(SpJumpType.PPT, R.string.nt_alarm_6),
        item(SpJumpType.PDF, R.string.nt_alarm_7),
        item(SpJumpType.PDF, R.string.nt_alarm_8),
    )

    private val scheduleIdArray = intArrayOf(1345141, 1098992, 1965755)
    private val unlockIdArray = intArrayOf(2176732, 2984563, 2295638)
    private val alarmClockIdArray = intArrayOf(3317982, 3016683, 3712931)
    private val homeClickIdArray = intArrayOf(4849184, 4886674, 4714216)
    private val recentClickIdArray = intArrayOf(5138216, 5426203, 58786810)
    private val noPageIdArray = intArrayOf(6949829, 6126092, 6876659)
    private val adClickIdArray = intArrayOf(7121207, 7352892, 7871372)

    // region 文案

    /** Apollo `getScheduleCopyWriting()`。 */
    fun scheduleCopywriting(context: Context): SpNotificationItem =
        next(scheduledArray, context, KEY_LAST_SCHEDULE_INDEX)

    /** Apollo `getUnlockCopyWriting()`。 */
    fun unlockCopywriting(context: Context): SpNotificationItem =
        next(unlockArray, context, KEY_LAST_UNLOCK_INDEX)

    /** Apollo `getClockCopyWriting()`。 */
    fun clockCopywriting(context: Context): SpNotificationItem =
        next(alarmClockArray, context, KEY_LAST_CLOCK_INDEX)
    // endregion

    // region 通知 ID

    /** Apollo `getScheduleID()` / `getUnlockID()` … 的统一入口。 */
    fun notificationId(context: Context, scene: SpNotifyScene): Int = when (scene) {
        SpNotifyScene.SCHEDULED -> checkId(context, scheduleIdArray)
        SpNotifyScene.UNLOCK -> checkId(context, unlockIdArray)
        SpNotifyScene.ALARM_CLOCK -> checkId(context, alarmClockIdArray)
        SpNotifyScene.HOME_CLICK -> checkId(context, homeClickIdArray)
        SpNotifyScene.RECENT_CLICK -> checkId(context, recentClickIdArray)
        SpNotifyScene.NO_PAGE_EXIST -> checkId(context, noPageIdArray)
        SpNotifyScene.AD_CLICK -> checkId(context, adClickIdArray)
    }
    // endregion

    /** 系统通知大图的封面。Apollo 用 `mipmap/{pdf,ppt,excel,word}_sys`，这里用首页同一套列表图标。 */
    fun coverIcon(jump: SpJumpType): Int = when (jump) {
        SpJumpType.PDF -> R.drawable.ic_home_list_pdf
        SpJumpType.PPT -> R.drawable.ic_home_list_ppt
        SpJumpType.EXCEL -> R.drawable.ic_home_list_xls
        SpJumpType.WORD -> R.drawable.ic_home_list_doc
    }

    /**
     * Apollo `getCheckNotificationID(context, ids)`：**从后往前**找第一个状态栏里没有的 ID；
     * 都在就退回最后一个。
     */
    private fun checkId(context: Context, ids: IntArray): Int {
        val active = runCatching {
            NotificationManagerCompat.from(context).activeNotifications.map { it.id }.toSet()
        }.getOrDefault(emptySet())
        for (i in ids.indices.reversed()) {
            if (ids[i] !in active) return ids[i]
        }
        return ids.last()
    }

    /**
     * Apollo 的轮换：`index = stored + 1`（越界回 0），写回后返回该条。
     * `stored + 1` 而不是 `stored`，所以首次调用拿到的是第 2 条——原样保留。
     */
    private fun next(
        array: Array<SpNotificationItem>,
        context: Context,
        key: String,
    ): SpNotificationItem {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return runCatching {
            val stored = prefs.getInt(key, 0)
            val index = if (stored < array.size - 1) stored + 1 else 0
            prefs.edit().putInt(key, index).apply()
            array[index]
        }.getOrElse {
            prefs.edit().putInt(key, 0).apply()
            array[0]
        }
    }

    private fun item(jump: SpJumpType, textRes: Int) =
        SpNotificationItem(jump = jump, textRes = textRes, buttonRes = R.string.sp_notify_action)
}
