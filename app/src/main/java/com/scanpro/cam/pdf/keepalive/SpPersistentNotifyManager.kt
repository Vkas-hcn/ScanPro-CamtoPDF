package com.scanpro.cam.pdf.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.boot.SpBootActivity
import com.scanpro.cam.pdf.notice.SpJumpType
import com.scanpro.cam.pdf.notice.SpNotifyCatalog
import com.scanpro.cam.pdf.notice.SpNotifyCreator
import com.scanpro.cam.pdf.notice.SpNotifyExtras
import com.scanpro.cam.pdf.notice.SpNotifyPermit
import com.scanpro.cam.pdf.notice.SpNotifyTheme
import com.scanpro.cam.pdf.notice.SpNotifyUtils

/**
 * 常驻通知（就是那条「挂在前台服务上、划不掉」的通知）。
 * 照抄 ApolloPDF `keep_alive/PersistentNotifyManager.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/keep_alive/PersistentNotifyManager.java`
 *
 * 三个快捷入口（PDF / EXCEL / PPT）各挂一个 PendingIntent，点了都进
 * [SpBootActivity]，靠 [SpNotifyExtras] 里的 `jump_type` 区分。
 *
 * 两个造法：
 * - [createPersistentNotification]：自定义版式（三个文件的图标），正常走这条
 * - [createStandardPersistentNotification]：自定义版式炸了时的兜底，退化成一条普通通知
 *   （Apollo `createStandardPersistentNotification`）
 *
 * **和 Apollo 的差异**：这条通知要求「置顶」。Android 8.0 起没有公开 API 能把通知钉死在
 * 通知栏第一行，能用的把手全在这里：
 * - 渠道 `IMPORTANCE_HIGH` + `PRIORITY_MAX`：保证它落在「提醒」区，不掉进无声那一堆；
 * - `setGroup` + `setGroupSummary`：自成一个组，不被系统跟自家提醒打成一个 bundle 吞掉；
 * - `FOREGROUND_SERVICE_IMMEDIATE`：12+ 前台服务通知默认延后 10 秒才上栏；
 * - [refreshPersistentNotification]：**换 key 重发**。系统按「首次上栏时间」排序，
 *   同一个 key 再 `notify` 多少次都不重排（实测），只有换 ID 发一条新的才会往上走。
 *
 * 同一区域里再往下排按时间，这一点系统不给改；要做到「永远第一」只能上
 * MediaStyle 媒体卡那套（会变成播放器卡片），本工程不走。
 */
internal object SpPersistentNotifyManager {

    /**
     * 常驻渠道 ID。
     *
     * 由 `channel_sp_persistent` 改名而来，为的是**改掉渠道重要度**：重要度只在
     * `createNotificationChannel` 建渠道那一刻定死，对已存在的渠道再调只更新名字/描述，
     * 改不动重要度。老渠道是 `IMPORTANCE_DEFAULT`，常驻通知会跟着一堆无声通知沉在
     * 通知栏偏下的位置；提到 `IMPORTANCE_HIGH` 之后它落在最上面的「提醒」区。
     * 老渠道由 [ensureChannel] 顺手删掉，免得用户在设置里看到两条同名渠道。
     */
    const val CHANNEL_PERSISTENT = "channel_sp_persistent_high"

    /** 老渠道 ID，只在 [ensureChannel] 里用来删。 */
    private const val CHANNEL_PERSISTENT_LEGACY = "channel_sp_persistent"

    /** 常驻通知自己的组（只挂它一个，目的是不进系统给同应用自动打的 bundle）。 */
    private const val GROUP_PERSISTENT = "sp_keepalive"

    /** Apollo `persistent_notification_id`。 */
    const val PERSISTENT_NOTIFICATION_ID = 234397

    /** 刷新时轮换的第二个 ID：换 key 才有新的排序时间，见 [refreshPersistentNotification]。 */
    private const val PERSISTENT_NOTIFICATION_ID_ALT = 234398

    /** 当前这条常驻通知挂在哪个 ID 上；前台服务 `startForeground` 必须跟它保持一致。 */
    @Volatile
    private var liveNotificationId: Int = PERSISTENT_NOTIFICATION_ID

    /** 规范 E16「常驻通知栏 pos_id 标常驻位」用。 */
    const val POS_ID_PERSISTENT = "persistent"

    private const val TAG = "SpPersistentNotify"

    /**
     * 用 `IMPORTANCE_HIGH` 把常驻通知顶到通知栏最上面的「提醒」区。
     *
     * 渠道本身**不响不震不亮**（见 [ensureChannel]），通知也 `setSilent(true)`，
     * 所以高重要度只影响排序，不会弹横幅、不会响铃。
     */
    private const val CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH

    /**
     * 两条造法共用的那串「常驻」属性：划不掉、不响、不重复提醒。
     *
     * 排序相关的三个开关：
     * - 渠道 `IMPORTANCE_HIGH`（8.0+ 真正生效的排序依据，见 [ensureChannel]）
     * - `PRIORITY_MAX`（8.0 以下没有渠道，排序只看 priority）
     * - `FOREGROUND_SERVICE_IMMEDIATE`（12 起前台服务的通知默认压 10 秒才上栏，
     *   不加它「常驻通知」会晚十秒才出现）
     */
    private fun baseBuilder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_PERSISTENT)
            .setSmallIcon(R.drawable.sp_ic_notify)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // 自成一个组、自己当 summary：不进系统给同应用自动打的 bundle，
            // 否则会被自家提醒吞进「N 条通知」的折叠卡片里
            .setGroup(GROUP_PERSISTENT)
            .setGroupSummary(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }

    /** 造自定义版式的常驻通知。 */
    fun createPersistentNotification(context: Context): Notification {
        ensureChannel(context)
        val builder = baseBuilder(context).setStyle(NotificationCompat.DecoratedCustomViewStyle())

        // Apollo：31+ 折叠态用小版式，展开态用大版式；31 以下三个位置都塞大版式。
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setCustomContentView(createView(context, R.layout.sp_notify_persistent_small, padTopBottom = false))
            builder.setCustomBigContentView(createView(context, R.layout.sp_notify_persistent_big, padTopBottom = true))
        } else {
            val big = createView(context, R.layout.sp_notify_persistent_big, padTopBottom = true)
            builder.setCustomContentView(big)
            builder.setCustomBigContentView(big)
            builder.setCustomHeadsUpContentView(big)
        }
        return builder.build()
    }

    /**
     * 兜底版：不挂 RemoteViews，标题用 App 名、正文用定时池的下一条文案。
     * Apollo `createStandardPersistentNotification`。
     */
    fun createStandardPersistentNotification(context: Context): Notification {
        ensureChannel(context)
        val item = SpNotifyCatalog.scheduleCopywriting(context)
        return baseBuilder(context)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(item.textRes))
            .build()
    }

    /**
     * Apollo `sendPersistentNotification(context)`：已经在状态栏里、或者没通知权限，都直接返回。
     *
     * 判重那步是必要的——前台服务每次起来都会调一遍，不去重会闪一下。
     */
    fun sendPersistentNotification(context: Context) {
        val appContext = context.applicationContext
        if (isPersistentShowing(appContext)) return
        if (!SpNotifyPermit.isHaveNotifyPermission(appContext)) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            val notification = createPersistentNotification(appContext)
            Log.i(TAG, "sendPersistentNotification: $liveNotificationId")
            SpTracker.log(appContext, SpTracker.ALWAYS_NOTIFICATION_TRIGGER)
            SpTrackEvents.notificationTrigger(appContext, POS_ID_PERSISTENT, "resident")
            SpTrackEvents.notificationShow(appContext, POS_ID_PERSISTENT, "resident")
            manager.cancel(otherId(liveNotificationId))
            manager.notify(liveNotificationId, notification)
        }.onFailure { Log.w(TAG, "sendPersistentNotification failed", it) }
    }

    /** 常驻通知当前挂在哪个 ID 上；前台服务 `startForeground` 必须用它，否则会多出一条。 */
    fun currentNotificationId(context: Context): Int =
        activePersistentId(context) ?: liveNotificationId

    /** 常驻通知是否已经在状态栏里（两个轮换 ID 都算）。 */
    fun isPersistentShowing(context: Context): Boolean =
        activePersistentId(context) != null

    /** 两个轮换 ID 里当前正在状态栏里显示的那个；没有就返回 null。 */
    private fun activePersistentId(context: Context): Int? = runCatching {
        NotificationManagerCompat.from(context.applicationContext).activeNotifications
            .firstOrNull { it.id == PERSISTENT_NOTIFICATION_ID || it.id == PERSISTENT_NOTIFICATION_ID_ALT }
            ?.id
    }.getOrNull()

    /**
     * 把常驻通知重新排到通知栏最上面。
     *
     * 为什么不能只 `notify` 一次：系统按**首次上栏时间**排序，同一个 key 的更新不重排。
     * Android 15 实测：对已存在的 ID 再 `notify`、甚至 `cancel` 之后立刻用同一个 ID 重发，
     * 它排在哪还是哪。只有**换一个 key** 才会拿到新的排序时间，所以这里在两个 ID 之间轮换。
     *
     * 调用时机：自己刚发完提醒之后（那一刻新提醒必定压在常驻上面），以及前台服务被叫起来时。
     *
     * **能力边界**：换 key 只能压过比它先上栏的通知。系统仍然把「会响的」通知排在
     * 「不响的」前面，所以本通知再怎么刷新也排不到一条刚响过的提醒之上——这是系统行为，
     * 除非给它配声音/震动（那就变成打扰用户了）。
     */
    fun refreshPersistentNotification(context: Context) {
        val appContext = context.applicationContext
        if (!SpNotifyPermit.isHaveNotifyPermission(appContext)) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            val oldId = activePersistentId(appContext) ?: liveNotificationId
            val newId = otherId(oldId)
            Log.i(TAG, "refreshPersistentNotification: $oldId -> $newId")
            manager.cancel(oldId)
            manager.notify(newId, createPersistentNotification(appContext))
            liveNotificationId = newId
        }.onFailure { Log.w(TAG, "refreshPersistentNotification failed", it) }
    }

    /** 两个轮换 ID 互相取反。 */
    private fun otherId(id: Int): Int =
        if (id == PERSISTENT_NOTIFICATION_ID) PERSISTENT_NOTIFICATION_ID_ALT else PERSISTENT_NOTIFICATION_ID

    /** 按布局造一份 RemoteViews，并把三个入口的点击接上。 */
    private fun createView(context: Context, layoutRes: Int, padTopBottom: Boolean): RemoteViews =
        RemoteViews(context.packageName, layoutRes).apply {
            if (padTopBottom && Build.VERSION.SDK_INT < 30) {
                setViewPadding(R.id.root, 0, 20, 0, 20)
            }
            // 自定义版式不会跟着系统深浅色反色，文字色得自己盖（见 SpNotifyTheme）。
            val textColor = SpNotifyTheme.textColor(context)
            setTextColor(R.id.tagPdf, textColor)
            setTextColor(R.id.tagExcel, textColor)
            setTextColor(R.id.tagPpt, textColor)
            setOnClickPendingIntent(R.id.pdfLayout, createJumpIntent(context, SpJumpType.PDF))
            setOnClickPendingIntent(R.id.excelLayout, createJumpIntent(context, SpJumpType.EXCEL))
            setOnClickPendingIntent(R.id.pptLayout, createJumpIntent(context, SpJumpType.PPT))
        }

    /** Apollo `createIntent(context, jumpType)`。 */
    private fun createJumpIntent(context: Context, jumpType: SpJumpType): PendingIntent {
        val intent = Intent(context, SpBootActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(SpNotifyExtras.JUMP_TYPE, jumpType.name)
            putExtra(SpNotifyExtras.FROM_TYPE, SpNotifyCreator.FROM_PERSISTENT)
        }
        return PendingIntent.getActivity(
            context,
            SpNotifyUtils.randomCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PERSISTENT, CHANNEL_PERSISTENT, CHANNEL_IMPORTANCE).apply {
                description = null
                setShowBadge(false)
                // 重要度 HIGH 但不响不震不亮：只借它做排序，不打扰用户。
                setSound(null, null)
                enableLights(false)
                lightColor = 0
                vibrationPattern = null
                enableVibration(false)
            },
        )
        // 老渠道留着只会多一条同名项。删掉时挂在它上面的旧通知会跟着消失，
        // 但两个调用方紧接着就会重发（startForeground / notify），用户看不到空档。
        manager.deleteNotificationChannel(CHANNEL_PERSISTENT_LEGACY)
    }
}
