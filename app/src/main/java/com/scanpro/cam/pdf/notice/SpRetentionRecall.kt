package com.scanpro.cam.pdf.notice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 新用户 D+1 定点召回：安装后 24h/48h/72h 三档定点通知。
 *
 * - 用 setAndAllowWhileIdle（不申请 SCHEDULE_EXACT_ALARM 权限，允许分钟级漂移）；
 * - 闹钟不跨重启：开机后由 SpPhoneBootReceiver 重挂未发档位；
 * - 用户回访（安装后 2h 内有过冷启动）即整体取消，不再召回；
 * - 到点发送完全复用现有 SCHEDULED 场景管线（文案轮换/三 ID/唤屏/重发循环/埋点）。
 */
object SpRetentionRecall {

    private const val PREFS = "sp_retention_recall"
    private const val KEY_INSTALL_TIME = "install_time"
    private const val KEY_RECALL_STAGE = "recall_stage"
    private const val KEY_SCHEDULED_AT = "scheduled_at"
    private const val KEY_LAST_OPEN_AT = "last_open_at"
    private const val KEY_LAST_RECALL_AT = "last_recall_at"

    private const val REQUEST_CODE = 81_337
    private const val MAX_STAGE = 3
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** 安装后 2 小时内有过冷启动视为「已回访」，不再召回。 */
    private const val RETURNED_THRESHOLD_MS = 2 * 60 * 60 * 1000L

    /** 安装超过 7 天的不再进入召回（覆盖升级的老用户不应被补发）。 */
    private const val RECALL_MAX_AGE_DAYS = 7

    /** 两条召回之间的最小间隔（防止多档在几分钟内连发）。 */
    private const val MIN_RECALL_GAP_MS = 20 * 60 * 60 * 1000L

    fun schedule(context: Context) {
        runCatching {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val install = prefs.getLong(KEY_INSTALL_TIME, 0L).takeIf { it > 0L }
                ?: app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime.also {
                    prefs.edit().putLong(KEY_INSTALL_TIME, it).apply()
                }
            val now = System.currentTimeMillis()
            // 1) 老用户（安装超过 RECALL_MAX_AGE_DAYS）不进入召回：避免覆盖升级后把历次档位
            //    一次性补发（1.0.3 的 bug：升级后 3 分钟内连发 3 条）。
            if (now - install > RECALL_MAX_AGE_DAYS * DAY_MS) {
                prefs.edit().putInt(KEY_RECALL_STAGE, MAX_STAGE + 1).putLong(KEY_SCHEDULED_AT, 0L).apply()
                return@runCatching
            }
            var stage = prefs.getInt(KEY_RECALL_STAGE, 1).coerceAtLeast(1)
            if (stage > MAX_STAGE) return@runCatching
            val scheduledAt = prefs.getLong(KEY_SCHEDULED_AT, 0L)
            if (scheduledAt > now) return@runCatching
            val lastOpen = prefs.getLong(KEY_LAST_OPEN_AT, 0L)
            if (lastOpen > install + RETURNED_THRESHOLD_MS) return@runCatching
            // 2) 已过期的档位直接跳过（Doze 延迟 / 覆盖安装），不要 +60s 补发
            while (stage <= MAX_STAGE && install + DAY_MS * stage <= now) {
                stage++
            }
            prefs.edit().putInt(KEY_RECALL_STAGE, stage).apply()
            if (stage > MAX_STAGE) return@runCatching
            setAlarm(app, install + DAY_MS * stage)
        }.onFailure { Log.w(TAG, "schedule failed", it) }
    }

    /** 冷启动回访时调用：记录回访时间。 */
    fun markOpened(context: Context) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_OPEN_AT, System.currentTimeMillis()).apply()
        }
    }

    /** 用户已回访：取消全部未发档位。 */
    fun cancel(context: Context) {
        runCatching {
            val app = context.applicationContext
            val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            alarmManager.cancel(pendingIntent(app))
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_RECALL_STAGE, MAX_STAGE + 1)
                .putLong(KEY_SCHEDULED_AT, 0L)
                .apply()
        }.onFailure { Log.w(TAG, "cancel failed", it) }
    }

    /** 发完一档后由 Receiver 调：推进下一档并排期。 */
    fun advanceAndSchedule(context: Context) {
        runCatching {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stage = prefs.getInt(KEY_RECALL_STAGE, 1)
            if (stage > MAX_STAGE) return@runCatching
            val next = stage + 1
            prefs.edit().putInt(KEY_RECALL_STAGE, next).putLong(KEY_SCHEDULED_AT, 0L).apply()
            if (next <= MAX_STAGE) schedule(app)
        }.onFailure { Log.w(TAG, "advanceAndSchedule failed", it) }
    }

    /** 这一档是否还应该发（用户尚未回访，且距上一条召回已超过 20 小时）。 */
    fun shouldRecall(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val install = prefs.getLong(KEY_INSTALL_TIME, 0L)
        if (install <= 0L) return false
        val lastOpen = prefs.getLong(KEY_LAST_OPEN_AT, 0L)
        if (lastOpen > install + RETURNED_THRESHOLD_MS) return false
        val lastRecallAt = prefs.getLong(KEY_LAST_RECALL_AT, 0L)
        if (lastRecallAt > 0L && System.currentTimeMillis() - lastRecallAt < MIN_RECALL_GAP_MS) return false
        return true
    }

    /** 发过一条召回后调用，用于 20 小时间隔。 */
    fun markRecallSent(context: Context) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_RECALL_AT, System.currentTimeMillis()).apply()
        }
    }

    private fun setAlarm(app: Context, triggerAt: Long) {
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // 精确闹钟：召回必须准点，同时也是 Android 12+ 后台启动前台服务的豁免之一
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(app))
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(app))
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SCHEDULED_AT, triggerAt).apply()
        Log.i(TAG, "recall alarm set at=$triggerAt")
    }

    private fun pendingIntent(app: Context): PendingIntent {
        val intent = Intent(app, SpRetentionRecallReceiver::class.java)
        return PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val TAG = "SpRetentionRecall"
}
