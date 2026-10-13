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
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.boot.SpBootActivity
import com.scanpro.cam.pdf.notice.SpNotifyCatalog
import com.scanpro.cam.pdf.notice.SpNotifyExtras
import com.scanpro.cam.pdf.notice.SpNotifyPermit
import com.scanpro.cam.pdf.notice.SpNotifyUtils
import com.scanpro.cam.pdf.notice.SpNotifyCreator
import com.scanpro.cam.pdf.notice.SpJumpType

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
 */
internal object SpPersistentNotifyManager {

    /** Apollo `channel_name_persistent`。 */
    const val CHANNEL_PERSISTENT = "channel_sp_persistent"

    /** Apollo `persistent_notification_id`。 */
    const val PERSISTENT_NOTIFICATION_ID = 234397

    private const val TAG = "SpPersistentNotify"

    /** Apollo 建渠道用 `IMPORTANCE_DEFAULT`(3)，这里保持一致。 */
    private const val CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_DEFAULT

    /** 造自定义版式的常驻通知。 */
    fun createPersistentNotification(context: Context): Notification {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_PERSISTENT)
            .setSmallIcon(R.drawable.sp_ic_notify)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

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
        return NotificationCompat.Builder(context, CHANNEL_PERSISTENT)
            .setSmallIcon(R.drawable.sp_ic_notify)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(item.textRes))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Apollo `sendPersistentNotification(context)`：已经在状态栏里、或者没通知权限，都直接返回。
     *
     * 判重那步是必要的——前台服务每次起来都会调一遍，不去重会闪一下。
     */
    fun sendPersistentNotification(context: Context) {
        val appContext = context.applicationContext
        if (SpNotifyPermit.isNotificationShowing(appContext, PERSISTENT_NOTIFICATION_ID)) return
        if (!SpNotifyPermit.isHaveNotifyPermission(appContext)) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            val notification = createPersistentNotification(appContext)
            Log.i(TAG, "sendPersistentNotification: $PERSISTENT_NOTIFICATION_ID")
            SpTracker.log(appContext, SpTracker.ALWAYS_NOTIFICATION_TRIGGER)
            manager.notify(PERSISTENT_NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "sendPersistentNotification failed", it) }
    }

    /** 按布局造一份 RemoteViews，并把三个入口的点击接上。 */
    private fun createView(context: Context, layoutRes: Int, padTopBottom: Boolean): RemoteViews =
        RemoteViews(context.packageName, layoutRes).apply {
            if (padTopBottom && Build.VERSION.SDK_INT < 30) {
                setViewPadding(R.id.root, 0, 20, 0, 20)
            }
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
                setSound(null, null)
                enableLights(false)
                lightColor = 0
                vibrationPattern = null
                enableVibration(false)
            },
        )
    }
}
