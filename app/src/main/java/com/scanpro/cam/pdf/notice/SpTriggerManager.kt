package com.scanpro.cam.pdf.notice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private const val TAG = "SpTriggerManager"

    /** Apollo `ALARM_REQUEST_CODE`。 */
    private const val ALARM_REQUEST_CODE = 41385

    /** Apollo `set_alarm_next_time`。 */
    private const val KEY_NEXT_ALARM_TIME = "sp_set_alarm_next_time"

    private const val PREFS = "sp_trigger_manager"

    /** Apollo `MAX_WAIT_CONFIG_TIME`。 */
    private const val MAX_WAIT_CONFIG_MS = 5000L
    private const val CONFIG_POLL_MS = 200L

    /** Apollo `startWhileTask`：4 秒预热，之后每 60 秒一次。 */
    private const val SCHEDULE_WARMUP_MS = 4000L
    private const val SCHEDULE_INTERVAL_MS = 60_000L

    /** Apollo `startAlarmTask`：等 5 秒（让远端配置先到）再对准闹钟。 */
    private const val ALARM_WARMUP_MS = 5000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Apollo `alarmMutex`：同一时刻只允许一个协程改闹钟。 */
    private val alarmMutex = Mutex()

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
                    .onFailure { Log.w(TAG, "startWhileTask schedule failed", it) }
                delay(SCHEDULE_INTERVAL_MS)
            }
        }
    }

    /** 停掉定时轮询（App 回到前台时用，Apollo 没有这一步，属于本工程的收尾）。 */
    fun stopWhileTask() {
        whileTaskJob?.cancel()
        whileTaskJob = null
    }

    /** Apollo `startAlarmTask`：等 5 秒 → 发一条闹钟提醒 → 对准下一个闹钟。 */
    fun startAlarmTask(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            delay(ALARM_WARMUP_MS)
            runCatching { sendClockNotification(appContext) }
                .onFailure { Log.w(TAG, "startAlarmTask clock failed", it) }
            setAlarm(appContext, force = false)
        }
    }

    /**
     * 对准下一个闹钟。Apollo `setAlarm(context, force)`。
     *
     * - `force = false`：已经排过一个还没到的闹钟就跳过（开机/启动时用）。
     * - `force = true`：无条件重排（闹钟响了之后，`SpAlarmReceiver` 用这个把下一轮排上）。
     *
     * 到点时间 = 现在 + `acpop_config.ac_a` 分钟；AlarmManager 用
     * `setAndAllowWhileIdle(RTC_WAKEUP, ...)`，Doze 里也能醒。
     */
    fun setAlarm(context: Context, force: Boolean) {
        val appContext = context.applicationContext
        scope.launch {
            alarmMutex.withLock {
                runCatching {
                    if (!force) {
                        val existing = prefs(appContext).getLong(KEY_NEXT_ALARM_TIME, 0L)
                        if (existing > System.currentTimeMillis()) {
                            Log.i(TAG, "setAlarm: already scheduled at $existing, skip")
                            return@runCatching
                        }
                    }
                    val config = waitAcPopConfig() ?: run {
                        Log.w(TAG, "setAlarm: acpop_config not ready, give up")
                        return@runCatching
                    }
                    val triggerAt = System.currentTimeMillis() + config.acA * 60_000L
                    val intent = Intent(appContext, SpAlarmReceiver::class.java)
                    val pending = PendingIntent.getBroadcast(
                        appContext,
                        ALARM_REQUEST_CODE,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val alarmManager =
                        appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@runCatching
                    // type 0 = RTC_WAKEUP（Apollo 传的也是 0）
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    prefs(appContext).edit().putLong(KEY_NEXT_ALARM_TIME, triggerAt).apply()
                    Log.i(TAG, "setAlarm: triggerAt=$triggerAt acA=${config.acA}")
                }.onFailure { Log.w(TAG, "setAlarm failed", it) }
            }
        }
    }

    /**
     * Apollo `waitAcPopConfig()`：最长等 5 秒，每 200ms 看一眼 `acpop_config` 到没到。
     *
     * 我们这边 [SpNotifyEnv.acPopConfig] 在远端没回来时给的是内置默认值（`ac_a = 30`），
     * 所以正常路径第一轮就返回；保留轮询是为了跟 Apollo 的时序一致，远端配置真接进来以后
     * 这条逻辑不用改。
     */
    private suspend fun waitAcPopConfig(): SpAcPopConfig? {
        var waited = 0L
        while (waited < MAX_WAIT_CONFIG_MS) {
            val config = SpNotifyEnv.acPopConfig
            if (config.acA > 0) return config
            delay(CONFIG_POLL_MS)
            waited += CONFIG_POLL_MS
        }
        Log.w(TAG, "waitAcPopConfig: timeout")
        return null
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
        // 远端配置没回来之前不发（Apollo 这段是 `if (remoteConfig.newConfig != null)`）
        if (!SpNotifyEnv.hasRemoteConfig) return
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
        if (SpNotifyEnv.isForeground()) return
        SpSendManager.sendSysNotification(context, item, scene)
        SpSendManager.sendMediaNotification(context, item, scene)
        SpNotifyFilter.updateSendCountAndLastTime(context, scene)
    }

    /** 只发媒体式提醒，然后把计数 +1。 */
    private suspend fun sendMediaOnly(context: Context, item: SpNotificationItem, scene: SpNotifyScene) {
        if (SpNotifyEnv.isForeground()) return
        SpSendManager.sendMediaNotification(context, item, scene)
        SpNotifyFilter.updateSendCountAndLastTime(context, scene)
    }
    // endregion

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 供 [SpNotifyFilter] 之外的调用方确认总闸（Apollo 里由 `BlockingLogic` 统一管）。 */
    fun canSendSystemNotification(context: Context): Boolean =
        SpBlockingLogic.get(context).canSendSystemNotification()
}
