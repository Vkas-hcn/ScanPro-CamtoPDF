package com.scanpro.cam.pdf.notice

import android.app.Notification
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 真正把通知发出去，并负责「同一 ID 反复重发」的循环。
 * 逐分支照抄 ApolloPDF `notice/launcher_scene/SendManager.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/notice/launcher_scene/SendManager.java`
 *
 * 三个入口：
 *
 * | 入口 | 通知 ID | 埋点 |
 * |---|---|---|
 * | [sendSysNotification] | 每个场景各自三个 ID 轮流（见 [SpNotifyCatalog]） | `system_notification_trigger` |
 * | [sendMediaNotification] | [MEDIA_NOTIFY_ID] 固定 | `media_notification_trigger` |
 * | [sendMedia2Notification] | [MEDIA_2_NOTIFY_ID] 固定 | 无 |
 *
 * **重发循环**（Apollo `PopRefresh`）：`pop_refresh_switch == 1` 且亮屏时，先把这条通知放进
 * 一个后台协程，重复 `times` 次、每次间隔 `interval` 秒，用同一个 ID 再发一遍。第一次发的是
 * 响铃版，后面发静音版——用户的观感是「这条提醒一直挂在最上面」。中途熄屏或回到前台就停。
 *
 * **差异**：Apollo 用反射 + AES 加密字符串去拿 `PowerManager` / `WakeLock`，这里直接调
 * [SpNotifyUtils.wakeScreen]；Samsung 机型跳过 media2 那支属于设备维度，按要求不迁。
 */
internal object SpSendManager {

    /** Apollo `MEDIA_NOTIFY_ID`。 */
    const val MEDIA_NOTIFY_ID = 945458

    /** Apollo `MEDIA_2_NOTIFY_ID`。 */
    const val MEDIA_2_NOTIFY_ID = 698228

    private const val PREFS = "sp_send_manager"
    private const val KEY_MEDIA2_LAST_TIME = "sendMedia2LastTime"

    /** 重发循环跑在进程级协程里：触发它的广播/服务随时可能结束，不能挂在页面作用域上。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var sysNotifyJob: Job? = null

    @Volatile
    private var mediaNotifyJob: Job? = null

    /** 媒体通知发送状态标记（进程内），避免重复发送 */
    @Volatile
    private var mediaNotificationSent = false

    // region 普通提醒

    /** Apollo `sendSysNotification(context, item, scene)`。 */
    fun sendSysNotification(context: Context, item: SpNotificationItem, scene: SpNotifyScene) {
        val appContext = context.applicationContext
        val blocking = SpBlockingLogic.get(appContext)
        try {
            val loud = SpNotifyCreator.createSysNotification(appContext, item, silent = false, scene = scene)
            val notificationId = SpNotifyCatalog.notificationId(appContext, scene)

            mediaNotifyJob?.cancel()
            mediaNotifyJob = null

            if (!blocking.canSendSystemNotification()) {
                AppLog.notification.i("通知被拦截 场景=${scene.sceneType} 类型=普通 原因=总闸拦截")
                return
            }
            if (SpNotifyEnv.isForeground()) {
                AppLog.notification.i("通知被拦截 场景=${scene.sceneType} 类型=普通 原因=应用在前台")
                return
            }

            val screenOn = SpNotifyUtils.isInteractive(appContext)
            if (SpNotifyEnv.popRefresh.popRefreshSwitch == 1 && screenOn) {
                sysNotifyJob?.cancel()
                sysNotifyJob = null
                val silent = SpNotifyCreator.createSysNotification(appContext, item, silent = true, scene = scene)
                if (blocking.canRefreshMediaNotification()) {
                    sysNotifyJob = startRefreshLoop(appContext, notificationId, loud, silent)
                } else {
                    notify(appContext, notificationId, loud)
                }
            } else {
                notify(appContext, notificationId, loud)
            }

            SpTracker.log(
                appContext,
                SpTracker.SYSTEM_NOTIFICATION_TRIGGER,
                sceneTypeBundle(scene),
            )
            SpTrackEvents.notificationTrigger(appContext, scene.sceneType, scene.toTriggerSource())
            SpTrackEvents.notificationShow(appContext, scene.sceneType, scene.toTriggerSource())
            if (!SpNotifyUtils.isInteractive(appContext)) {
                SpNotifyUtils.wakeScreen(appContext)
            }
            AppLog.notification.i("通知已发出 场景=${scene.sceneType} 类型=普通 id=$notificationId")
        } catch (t: Throwable) {
            AppLog.notification.w("通知发送失败 场景=${scene.sceneType} 类型=普通", t)
        }
    }
    // endregion

    // region 媒体式提醒

    /** Apollo `sendMediaNotification(context, item, scene)`。 */
    fun sendMediaNotification(context: Context, item: SpNotificationItem, scene: SpNotifyScene) {
        val appContext = context.applicationContext
        
        // 三星机型跳过媒体通知（参考 UltraClean 的逻辑）
        if (isSamsungDevice()) {
            AppLog.notification.d("通知被跳过 场景=${scene.sceneType} 类型=媒体 原因=三星机型")
            return
        }
        
        // 如果媒体通知已经在通知栏中显示，跳过发送
        if (mediaNotificationSent && hasActiveNotification(appContext, MEDIA_NOTIFY_ID)) {
            AppLog.notification.d("通知被跳过 场景=${scene.sceneType} 类型=媒体 原因=已显示")
            return
        }
        
        val blocking = SpBlockingLogic.get(appContext)
        try {
            val loud = SpNotifyCreator.createMediaNotification(appContext, item, silent = false, scene = scene)
            if (blocking.canRefreshSystemNotification()) {
                mediaNotifyJob?.cancel()
                mediaNotifyJob = startRefreshLoop(appContext, MEDIA_NOTIFY_ID, loud, loud)
            } else {
                notify(appContext, MEDIA_NOTIFY_ID, loud)
            }
            SpTracker.log(
                appContext,
                SpTracker.MEDIA_NOTIFICATION_TRIGGER,
                sceneTypeBundle(scene),
            )
            SpTrackEvents.notificationTrigger(appContext, scene.sceneType, scene.toTriggerSource())
            SpTrackEvents.notificationShow(appContext, scene.sceneType, scene.toTriggerSource())
            if (!SpNotifyUtils.isInteractive(appContext)) {
                SpNotifyUtils.wakeScreen(appContext)
            }
            AppLog.notification.i("通知已发出 场景=${scene.sceneType} 类型=媒体 id=$MEDIA_NOTIFY_ID")
            mediaNotificationSent = true
        } catch (t: Throwable) {
            AppLog.notification.w("通知发送失败 场景=${scene.sceneType} 类型=媒体", t)
        }
    }

    /**
     * Apollo `sendMedia2Notification(context, item, scene)`。
     *
     * 这一路自己带节流：`media_config.interval_time` 分钟之内只发一条，用
     * [KEY_MEDIA2_LAST_TIME] 记上次时间。发之前才做总闸判断（Apollo 也是先造后判）。
     */
    fun sendMedia2Notification(context: Context, item: SpNotificationItem, scene: SpNotifyScene) {
        val appContext = context.applicationContext
        try {
            val config = SpNotifyEnv.mediaConfig
            if (config.switch != 1) {
                AppLog.notification.i("通知被拦截 场景=${scene.sceneType} 类型=媒体2 原因=开关关闭")
                return
            }

            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val last = prefs.getLong(KEY_MEDIA2_LAST_TIME, 0L)
            val intervalMs = config.intervalTime * 60_000L
            if (last != 0L && System.currentTimeMillis() - last < intervalMs) {
                AppLog.notification.i("通知被拦截 场景=${scene.sceneType} 类型=媒体2 原因=未到发送间隔")
                SpTrackEvents.notificationFrequencyLimited(scene.sceneType, "未到发送间隔")
                return
            }

            val notification = SpNotifyCreator.createMedia2Notification(appContext, item, scene)
            if (!SpBlockingLogic.get(appContext).canSendMediaNotification()) {
                AppLog.notification.i("通知被拦截 场景=${scene.sceneType} 类型=媒体2 原因=总闸拦截")
                return
            }
            if (SpNotifyEnv.isForeground()) {
                AppLog.notification.i("通知被拦截 场景=${scene.sceneType} 类型=媒体2 原因=应用在前台")
                return
            }
            notify(appContext, MEDIA_2_NOTIFY_ID, notification)
            prefs.edit().putLong(KEY_MEDIA2_LAST_TIME, System.currentTimeMillis()).apply()
            SpTrackEvents.notificationTrigger(appContext, scene.sceneType, scene.toTriggerSource())
            SpTrackEvents.notificationShow(appContext, scene.sceneType, scene.toTriggerSource())
            if (!SpNotifyUtils.isInteractive(appContext)) {
                SpNotifyUtils.wakeScreen(appContext)
            }
            AppLog.notification.i("通知已发出 场景=${scene.sceneType} 类型=媒体2 id=$MEDIA_2_NOTIFY_ID")
        } catch (t: Throwable) {
            AppLog.notification.w("通知发送失败 场景=${scene.sceneType} 类型=媒体2", t)
        }
    }
    // endregion

    /** 停掉定时轮询（App 回到前台时用，Apollo 没有这一步，属于本工程的收尾）。 */
    fun stopNotifyUpdate() {
        sysNotifyJob?.cancel()
        sysNotifyJob = null
        mediaNotifyJob?.cancel()
        mediaNotifyJob = null
    }

    /** 重置媒体通知发送状态（用于测试或手动重置）。 */
    fun resetMediaNotificationState() {
        mediaNotificationSent = false
    }

    /**
     * Apollo `SendManager$sendSysNotification$1` / `$sendMediaNotification$2$1` 的循环体：
     * 先发（第 0 次用响铃版，之后用静音版），等 `interval` 秒，再检查「熄屏 / 回前台」决定是否收手。
     */
    private fun startRefreshLoop(
        context: Context,
        notificationId: Int,
        loud: Notification,
        silent: Notification,
    ): Job = scope.launch {
        val config = SpNotifyEnv.popRefresh
        var index = 0
        while (index < config.times) {
            notify(context, notificationId, if (index == 0) loud else silent)
            delay(config.interval * 1000L)
            if (!SpNotifyUtils.isInteractive(context) || SpNotifyEnv.isForeground()) {
                AppLog.notification.i("通知重发停止 原因=${if (SpNotifyEnv.isForeground()) "应用在前台" else "屏幕已关闭"}")
                stopNotifyUpdate()
                return@launch
            }
            index++
        }
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }.onFailure { AppLog.notification.w("通知发送失败 id=$id", it) }
    }

    private fun sceneTypeBundle(scene: SpNotifyScene): android.os.Bundle =
        android.os.Bundle().apply { putString("type", scene.sceneType) }

    /**
     * 判断是否为三星设备。
     * 参考 UltraClean SpPersistJobService.kt:63
     */
    private fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    /**
     * 检查指定 ID 的通知是否正在显示。
     * 参考 UltraClean HintUtil.kt:795
     */
    private fun hasActiveNotification(context: Context, notificationId: Int): Boolean {
        return try {
            val manager = NotificationManagerCompat.from(context)
            manager.activeNotifications.any { it.id == notificationId }
        } catch (e: Exception) {
            false
        }
    }
}
