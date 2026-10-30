package com.scanpro.cam.pdf.notice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.boot.SpBootActivity

/**
 * 造通知。逐分支照抄 ApolloPDF `notice/NotifyCreator.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/notice/NotifyCreator.java`
 *
 * 三类通知：
 *
 * | 方法 | 渠道 | 版式 | 用途 |
 * |---|---|---|---|
 * | [createSysNotification] | `channel_reader_normal[_silent]` | `sp_notify_sys_big/small` | 普通提醒，会响/会亮灯 |
 * | [createMediaNotification] | `channel_reader_media[_silent]` | 大图 + 按钮 | 媒体式提醒，优先级更高、更抗下拉 |
 * | [createMedia2Notification] | `channel_ado_pdf_media_two` | 大图 + 按钮 | 独立一路，自己带 30 分钟间隔（见 [SpSendManager]） |
 *
 * `silent = true` 的那一份不带任何声光震：Apollo 用它做「同一 ID 反复重发」里的后续几次
 * （见 [SpSendManager] 的重发循环），避免用户被连续响铃。
 *
 * **和 Apollo 的差异**：Apollo 的 media 版式走 MediaStyle + 假 MediaSession。MediaStyle 要
 * `androidx.media` 依赖，本工程没引，这里用「大图 + 自定义按钮」的等价格式替代，用户可见结果
 * 一致。要真正变成媒体通知，升级路径是加 `androidx.media:media` 再换成 `MediaStyle`。
 */
internal object SpNotifyCreator {

    const val CHANNEL_MEDIA = "channel_reader_media"
    const val CHANNEL_MEDIA_SILENT = "channel_reader_media_silent"
    const val CHANNEL_MEDIA_TWO = "channel_ado_pdf_media_two"
    const val CHANNEL_NORMAL = "channel_reader_normal"
    const val CHANNEL_NORMAL_SILENT = "channel_reader_normal_silent"

    /** 通知优先级：Apollo 建渠道时统一用 `IMPORTANCE_HIGH`(5)。 */
    private const val CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH

    // region 普通提醒

    /** Apollo `createSysNotification(context, item, silent, scene)`。 */
    fun createSysNotification(
        context: Context,
        item: SpNotificationItem,
        silent: Boolean,
        scene: SpNotifyScene,
    ): Notification {
        val channelId = if (silent) CHANNEL_NORMAL_SILENT else CHANNEL_NORMAL
        ensureChannel(
            context = context,
            channelId = channelId,
            sound = !silent,
            showBadge = true,
        )

        val cover = SpNotifyCatalog.coverIcon(item.jump)
        val content = Intent(context, SpBootActivity::class.java).apply {
            // Apollo: FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK（0x10008000）
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(SpNotifyExtras.JUMP_TYPE, item.jump.name)
            putExtra(SpNotifyExtras.FROM_TYPE, FROM_SYS)
            putExtra(SpNotifyExtras.SCENE_TYPE, scene.sceneType)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SpNotifyUtils.randomCode(),
            content,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.sp_ic_notify)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(item.textRes))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        // Apollo：31+ 折叠态用小图、展开态用大图；31 以下直接把大图塞满三个位置。
        // 原本还有一支按 xiaomi / oneplus / samsung 分流的写法，和设备维度一起不迁。
        if (isAndroid12()) {
            builder.setCustomContentView(createSysSmallView(context, item))
            builder.setCustomBigContentView(createSysBigView(context, item, cover))
        } else {
            val big = createSysBigView(context, item, cover)
            builder.setCustomContentView(big)
            builder.setCustomBigContentView(big)
            builder.setCustomHeadsUpContentView(big)
        }
        return builder.build()
    }

    /** Apollo `createSysBigNotifyView`。 */
    private fun createSysBigView(
        context: Context,
        item: SpNotificationItem,
        coverIconRes: Int,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.sp_notify_sys_big).apply {
        setImageViewResource(R.id.cover, coverIconRes)
        setTextViewText(R.id.messageText, context.getString(item.textRes))
        // 自定义版式不跟系统深浅色反色，正文色自己盖（见 SpNotifyTheme）。
        setTextColor(R.id.messageText, SpNotifyTheme.textColor(context))
        setTextViewText(R.id.btn, context.getString(item.buttonRes))
        if (Build.VERSION.SDK_INT > 28) {
            setImageViewResource(R.id.anim_btn, R.drawable.sp_notify_btn_anim)
        } else {
            setInt(R.id.btn, "setBackgroundResource", R.drawable.sp_shape_notify_btn)
        }
    }

    /** Apollo `createSysSmallNotifyView`。 */
    private fun createSysSmallView(context: Context, item: SpNotificationItem): RemoteViews =
        RemoteViews(context.packageName, R.layout.sp_notify_sys_small).apply {
            if (Build.VERSION.SDK_INT < 30) {
                setViewPadding(R.id.root, 20, 20, 20, 20)
            }
            setTextViewText(R.id.messageText, context.getString(item.textRes))
            setTextColor(R.id.messageText, SpNotifyTheme.textColor(context))
            setTextViewText(R.id.btnText, context.getString(item.buttonRes))
            if (Build.VERSION.SDK_INT > 28) {
                setImageViewResource(R.id.anim_btn, R.drawable.sp_notify_btn_anim)
            } else {
                setInt(R.id.btnText, "setBackgroundResource", R.drawable.sp_shape_notify_btn)
            }
        }
    // endregion

    // region 媒体式提醒

    /**
     * Apollo `createMediaNotification(context, item, silent, scene)`。
     *
     * 和普通提醒的区别：渠道独立、`ongoing`（下拉不能划掉）、`category = CATEGORY_SERVICE`，
     * 大图用大版式。跳转带的 `from_type` 是 `media_notify`。
     */
    fun createMediaNotification(
        context: Context,
        item: SpNotificationItem,
        silent: Boolean,
        scene: SpNotifyScene,
    ): Notification {
        val channelId = if (silent) CHANNEL_MEDIA_SILENT else CHANNEL_MEDIA
        ensureChannel(
            context = context,
            channelId = channelId,
            sound = !silent,
            showBadge = false,
        )
        return buildMediaLike(context, item, scene, channelId, FROM_MEDIA)
    }

    /** Apollo `createMedia2Notification(context, item, scene)`。独立渠道，自己带间隔。 */
    fun createMedia2Notification(
        context: Context,
        item: SpNotificationItem,
        scene: SpNotifyScene,
    ): Notification {
        ensureChannel(
            context = context,
            channelId = CHANNEL_MEDIA_TWO,
            sound = true,
            showBadge = false,
        )
        return buildMediaLike(context, item, scene, CHANNEL_MEDIA_TWO, FROM_MEDIA_TWO)
    }

    private fun buildMediaLike(
        context: Context,
        item: SpNotificationItem,
        scene: SpNotifyScene,
        channelId: String,
        fromType: String,
    ): Notification {
        val content = Intent(context, SpBootActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(SpNotifyExtras.JUMP_TYPE, item.jump.name)
            putExtra(SpNotifyExtras.FROM_TYPE, fromType)
            putExtra(SpNotifyExtras.SCENE_TYPE, scene.sceneType)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SpNotifyUtils.randomCode(),
            content,
            PendingIntent.FLAG_IMMUTABLE,
        )
        val view = createSysBigView(context, item, SpNotifyCatalog.coverIcon(item.jump))
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.sp_ic_notify)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(item.textRes))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(view)
            .setCustomBigContentView(view)
            .build()
    }
    // endregion

    /**
     * 建渠道。Apollo 每次造通知都调 `createNotificationChannel`，已存在时系统会忽略，
     * 幂等且便宜，这里照做。
     *
     * @param sound true 用系统默认提示音，false 完全静音（Apollo 的 `silent` 变体）
     */
    private fun ensureChannel(
        context: Context,
        channelId: String,
        sound: Boolean,
        showBadge: Boolean,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(channelId, channelId, CHANNEL_IMPORTANCE).apply {
            description = null
            setShowBadge(showBadge)
            if (sound) {
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, Notification.AUDIO_ATTRIBUTES_DEFAULT)
            } else {
                setSound(null, null)
            }
            enableLights(sound)
            lightColor = 0
            vibrationPattern = null
            enableVibration(sound)
        }
        manager.createNotificationChannel(channel)
    }

    /** Apollo `SDKVersionUtilKt.isAndroid12()`：`SDK_INT >= 31`。 */
    private fun isAndroid12(): Boolean = Build.VERSION.SDK_INT >= 31

    const val FROM_SYS = "sys_notify"
    const val FROM_MEDIA = "media_notify"
    const val FROM_MEDIA_TWO = "media_notify_2"
    const val FROM_PERSISTENT = "persistent_notify"
}
