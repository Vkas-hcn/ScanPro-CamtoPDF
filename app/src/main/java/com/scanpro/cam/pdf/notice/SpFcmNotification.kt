package com.scanpro.cam.pdf.notice

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.scanpro.cam.pdf.R
import com.zppko.pdfplus.nor.horus.ChannelUtil
import com.zppko.pdfplus.nor.horus.HintExt02
import com.zppko.pdfplus.nor.horus.HintUtil

/**
 * data-only FCM 到达且应用在后台时，由应用主动创建的系统通知。
 *
 * 关键约束：
 * - 只在后台发送；前台收到 FCM 不新增任何通知（本轮验收口径）。
 * - 使用系统通知槽位轮换（70101/70102）：与定时/解锁/闹钟共用，普通通知最多同时 2 条。
 * - 点击进入 SpBootActivity（开屏），与其它通知入口保持一致。
 * - 标题/正文优先取 data["title"] / data["body"]；缺失时回退现有本地化文案，避免新增 i18n 键。
 */
internal object SpFcmNotification {

    private const val TAG = "SpFcmNotify"
    private const val KEY_TITLE = "title"
    private const val KEY_BODY = "body"
    private const val KEY_ACTION = "action"
    private const val DEFAULT_ACTION = "b_home"

    fun post(context: Context, message: RemoteMessage) {
        if (HintExt02.isForeground) {
            Log.i(TAG, "skip: app foreground")
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: run { Log.w(TAG, "NotificationManager unavailable"); return }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "skip: notifications disabled")
            return
        }

        val data = message.data
        val title = data[KEY_TITLE]?.trim()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.app_name)
        val body = data[KEY_BODY]?.trim()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.nt_scheduled_1)
        val action = data[KEY_ACTION]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_ACTION

        val channel = ChannelUtil().get(true)
        // 一次事件只取一个槽位，点击意图和实际发送用同一个 id。
        val slot = SpNotifyIds.nextSys()
        val contentIntent = HintUtil.createIntent(action, HintUtil.CO_S_TYPE_INTERVAL, slot)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.sp_ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // 与 HintUtil 的系统通知同一个口径：显式 group 退出系统自动分组，避免被收进一个组。
            .setGroup("sp_sys_$slot")
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            // 与系统通知同一排序口径：CATEGORY_ALARM 提高排名权重，PRIORITY_MAX 对齐 HintUtil。
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(contentIntent)
            .build()

        val posted = runCatching { manager.notify(slot, notification) }.isSuccess
        Log.i(
            TAG,
            "post result=$posted slot=$slot testId=${mask(data["test_id"].orEmpty())} action=$action",
        )
    }

    private fun mask(value: String): String =
        if (value.length <= 8) "***" else value.substring(0, 6) + "***"
}
