package com.scanpro.cam.pdf.notice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.keepalive.SpPersistentNotifyManager
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 七个场景的触发总控。逐分支照抄 ApolloPDF
 * `notice/launcher_scene/TriggerManager.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/notice/launcher_scene/TriggerManager.java`
 *
 * 每个 `send*Notification` 都走同一条三段式：
 *
 * 1. **挑文案**：从 [SpNotifyCatalog] 的轮换池里取下一条（顺序推进，跨进程重启接着轮）。
 * 2. **分流**：
 *    - 有通知权限且总闸放行（[SpNotifyFilter.canSendSysNotification]）→ 再过一次场景频控
 *      （[SpNotifyFilter.sendNotificationFilter]）。过了就等一个延迟秒数，然后
 *      **普通提醒 + 媒体式提醒各发一条**，最后把当日计数 +1。
 *    - 没有通知权限 → 退到媒体式那一路（[SpNotifyFilter.canSendMediaNotification]）：
 *      只发媒体式提醒，计数同样 +1。
 * 3. **先发 media2**：`scheduled` / `unlock` / `alarm_clock` / `ad_click` 四个场景在分流之前
 *    还会先发一条独立渠道的 media2 提醒（[SpSendManager.sendMedia2Notification]），
 *    它自带 30 分钟节流，和上面的频控互不影响。
 *
 * 各场景的差别就三列（延迟秒数取自 `new_config`）：
 *
 * | 场景 | 文案池 | 先发 media2 | 延迟 |
 * |---|---|---|---|
 * | `scheduled` | 定时池 | 是 | 无 |
 * | `unlock` | 解锁池 | 是 | 无 |
 * | `alarm_clock` | 闹钟池 | 是 | 无 |
 * | `ad_click` | **解锁池** | 是 | `adclick_delay` |
 * | `home_click` | **定时池** | 否 | `home_delay` |
 * | `recent_click` | **定时池** | 否 | `recent_delay` |
 * | `no_page_exist` | **闹钟池** | 否 | `exit_delay` |
 *
 * 文案池交叉复用（ad_click 用解锁池、home/recent 用定时池、no_page 用闹钟池）是 Apollo 的
 * 原样，不是笔误。`home/recent/exit/ad_click` 四个还多一道前置：远端配置没回来之前不发。
 *
 * 延迟之后、真正发之前再查一次「是不是回到前台了」——用户已经回到 App 里就不要打扰他。
 */
internal object SpTriggerManager {

    /** Apollo `ALARM_REQUEST_CODE`。 */
    private const val ALARM_REQUEST_CODE = 41385

    /** Apollo `set_alarm_next_time`。 */
    private const val KEY_NEXT_ALARM_TIME = "sp_set_alarm_next_time"

    private const val PREFS = "sp_trigger_manager"

    /** Apollo `startWhileTask`：4 秒预热，之后每 60 秒一次。 */
    private const val SCHEDULE_WARMUP_MS = 4000L
    private const val SCHEDULE_INTERVAL_MS = 60_000L

    /** 最后一条提醒发完之后隔多久把常驻通知重排回最上面（要盖过同一波里的后续场景）。 */
    private const val PERSISTENT_REFRESH_DELAY_MS = 8000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 常驻通知重排的防抖任务，见 [schedulePersistentRefresh]。 */
    @Volatile
    private var persistentRefreshJob: Job? = null

    @Volatile
    private var whileTaskJob: Job? = null

    // region 七个入口

    /** Home 键。 */
    fun sendHomeClickNotification(context: Context) {
        val delaySeconds = SpNotifyEnv.newConfig.homeDelay
        dispatchDeferred(context, SpNotifyScene.HOME_CLICK, delaySeconds, media2 = false) {
            SpNotifyCatalog.scheduleCopywriting(context)
        }
    }

    /** 最近任务键。 */
    fun sendRecentClickNotification(context: Context) {
        val delaySeconds = SpNotifyEnv.newConfig.recentDelay
        dispatchDeferred(context, SpNotifyScene.RECENT_CLICK, delaySeconds, media2 = false) {
            SpNotifyCatalog.scheduleCopywriting(context)
        }
    }

    /** 退到桌面（没有页面了）。 */
    fun sendNoPageNotification(context: Context) {
        val delaySeconds = SpNotifyEnv.newConfig.exitDelay
        dispatchDeferred(context, SpNotifyScene.NO_PAGE_EXIST, delaySeconds, media2 = false) {
            SpNotifyCatalog.clockCopywriting(context)
        }
    }

    /** 点了广告。 */
    fun sendAdClickNotification(context: Context) {
        val delaySeconds = SpNotifyEnv.newConfig.adClickDelay
        dispatchDeferred(context, SpNotifyScene.AD_CLICK, delaySeconds, media2 = true) {
            SpNotifyCatalog.unlockCopywriting(context)
        }
    }

    /** 解锁（`USER_PRESENT` / `USER_UNLOCKED` 之后 1.5 秒）。无延迟。 */
    fun sendUnlockNotification(context: Context) {
        dispatchImmediate(context, SpNotifyScene.UNLOCK, media2 = true) {
            SpNotifyCatalog.unlockCopywriting(context)
        }
    }

    /** 定时轮询。无延迟。 */
    fun sendScheduleNotification(context: Context) {
        dispatchImmediate(context, SpNotifyScene.SCHEDULED, media2 = true) {
            SpNotifyCatalog.scheduleCopywriting(context)
        }
    }

    /** 闹钟到点。无延迟。 */
    fun sendClockNotification(context: Context) {
        dispatchImmediate(context, SpNotifyScene.ALARM_CLOCK, media2 = true) {
            SpNotifyCatalog.clockCopywriting(context)
        }
    }
    // endregion

    /**
     * Apollo `startWhileTask`：进前台 4 秒后先发一条定时提醒，之后每 60 秒发一条。
     *
     * 幂等：已经在跑就不重复起（Apollo 每次启动都会新建一个协程，这里收成一条）。
     */
    fun startWhileTask(context: Context) {
        val appContext = context.applicationContext
        if (whileTaskJob?.isActive == true) return
        whileTaskJob = scope.launch {
            delay(SCHEDULE_WARMUP_MS)
            while (true) {
                runCatching { sendScheduleNotification(appContext) }
                    .onFailure { AppLog.notification.w("定时通知触发失败", it) }
                delay(SCHEDULE_INTERVAL_MS)
            }
        }
    }

    /** 停掉定时轮询（App 回到前台时用，Apollo 没有这一步，属于本工程的收尾）。 */
    fun stopWhileTask() {
        whileTaskJob?.cancel()
        whileTaskJob = null
    }

    /**
     * Apollo `startAlarmTask`：启动即同步重建闹钟（评审 P0-2：不信任 prefs 时间戳，
     * 强停/系统清闹钟后冷启动必须无条件重建），闹钟提醒的发送仍走协程。
     */
    fun startAlarmTask(context: Context) {
        val appContext = context.applicationContext
        setAlarm(appContext, force = true)
        scope.launch {
            runCatching { sendClockNotification(appContext) }
                .onFailure { AppLog.notification.w("闹钟通知触发失败", it) }
        }
    }

    /**
     * 对准下一个闹钟。同步实现（评审 P0-1/P0-2）：
     * setAndAllowWhileIdle 是同步 binder 调用，直接在调用线程执行、不经过协程——
     * 广播接收器一返回进程就可能被回收，异步重排会丢下一轮闹钟。
     *
     * `force` 参数保留以兼容调用方，语义统一为「总是重建」：
     * 同一 PendingIntent（FLAG_UPDATE_CURRENT）天然覆盖旧闹钟，幂等；
     * 不再拿 prefs 时间戳当「已排期」依据（强停/系统清闹钟后时间戳是陈旧的）。
     * 配置缺失时用内置默认值（SpNotifyEnv.acPopConfig 恒非空，acA 默认 30 分钟）。
     */
    fun setAlarm(context: Context, force: Boolean) {
        val appContext = context.applicationContext
        runCatching {
            val config = SpNotifyEnv.acPopConfig
            val triggerAt = System.currentTimeMillis() + config.acA * 60_000L
            val intent = Intent(appContext, SpAlarmReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                appContext,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmManager =
                appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            // type 0 = RTC_WAKEUP（Apollo 传的也是 0）
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            prefs(appContext).edit().putLong(KEY_NEXT_ALARM_TIME, triggerAt).apply()
            AppLog.notification.i("闹钟已设置 触发时间=$triggerAt 间隔分钟=${config.acA}")
        }.onFailure { AppLog.notification.w("闹钟设置失败", it) }
    }

    // region 分流

    /** 无延迟的三条（scheduled / unlock / alarm_clock）。 */
    private fun dispatchImmediate(
        context: Context,
        scene: SpNotifyScene,
        media2: Boolean,
        item: () -> SpNotificationItem,
    ) {
        val appContext = context.applicationContext
        AppLog.notification.i("通知触发 场景=${scene.sceneType}")
        val notificationItem = item()
        if (media2) {
            SpSendManager.sendMedia2Notification(appContext, notificationItem, scene)
        }
        if (SpNotifyFilter.canSendSysNotification(appContext, scene)) {
            if (!SpNotifyFilter.sendNotificationFilter(appContext, scene)) return
            scope.launch {
                sendBoth(appContext, notificationItem, scene)
            }
        } else if (SpNotifyFilter.canSendMediaNotification(appContext, scene)) {
            scope.launch {
                sendMediaOnly(appContext, notificationItem, scene)
            }
        }
    }

    /** 有延迟的四条（home_click / recent_click / no_page_exist / ad_click）。 */
    private fun dispatchDeferred(
        context: Context,
        scene: SpNotifyScene,
        delaySeconds: Int,
        media2: Boolean,
        item: () -> SpNotificationItem,
    ) {
        val appContext = context.applicationContext
        AppLog.notification.i("通知触发 场景=${scene.sceneType} 延迟=${delaySeconds}秒")
        // 远端配置没回来之前不发（Apollo 这段是 `if (remoteConfig.newConfig != null)`）
        if (!SpNotifyEnv.hasRemoteConfig) {
            AppLog.notification.i("通知被拦截 场景=${scene.sceneType} 原因=远端配置未就绪")
            return
        }
        val notificationItem = item()
        if (media2) {
            SpSendManager.sendMedia2Notification(appContext, notificationItem, scene)
        }
        if (SpNotifyFilter.canSendSysNotification(appContext, scene)) {
            if (!SpNotifyFilter.sendNotificationFilter(appContext, scene)) return
            scope.launch {
                delay(delaySeconds * 1000L)
                sendBoth(appContext, notificationItem, scene)
            }
        } else if (SpNotifyFilter.canSendMediaNotification(appContext, scene)) {
            scope.launch {
                delay(delaySeconds * 1000L)
                sendMediaOnly(appContext, notificationItem, scene)
            }
        }
    }

    /** 普通提醒 + 媒体式提醒各一条，然后把计数 +1。 */
    private suspend fun sendBoth(context: Context, item: SpNotificationItem, scene: SpNotifyScene) {
        if (SpNotifyEnv.isForeground()) {
            AppLog.notification.i("通知被拦截 场景=${scene.sceneType} 原因=应用在前台")
            return
        }
        SpSendManager.sendSysNotification(context, item, scene)
        SpSendManager.sendMediaNotification(context, item, scene)
        SpNotifyFilter.updateSendCountAndLastTime(context, scene)
        schedulePersistentRefresh(context)
    }

    /** 只发媒体式提醒，然后把计数 +1。 */
    private suspend fun sendMediaOnly(context: Context, item: SpNotificationItem, scene: SpNotifyScene) {
        if (SpNotifyEnv.isForeground()) {
            AppLog.notification.i("通知被拦截 场景=${scene.sceneType} 原因=应用在前台")
            return
        }
        SpSendManager.sendMediaNotification(context, item, scene)
        SpNotifyFilter.updateSendCountAndLastTime(context, scene)
        schedulePersistentRefresh(context)
    }

    /**
     * 提醒发完之后，隔 [PERSISTENT_REFRESH_DELAY_MS] 把常驻通知重排回最上面。
     *
     * 用防抖而不是直接调：一次退回桌面会连着触发好几个场景（`home_click` 马上一条、
     * `no_page_exist` 五六秒后又一条），抢在最后一个之前重排等于白排。
     * 每来一条新提醒就取消上一次、重新计时，只留最后那一次。
     */
    private fun schedulePersistentRefresh(context: Context) {
        val appContext = context.applicationContext
        persistentRefreshJob?.cancel()
        persistentRefreshJob = scope.launch {
            delay(PERSISTENT_REFRESH_DELAY_MS)
            SpPersistentNotifyManager.refreshPersistentNotification(appContext)
        }
    }
    // endregion

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 供 [SpNotifyFilter] 之外的调用方确认总闸（Apollo 里由 `BlockingLogic` 统一管）。 */
    fun canSendSystemNotification(context: Context): Boolean =
        SpBlockingLogic.get(context).canSendSystemNotification()
}
