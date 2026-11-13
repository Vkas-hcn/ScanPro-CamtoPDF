//package com.scanpro.cam.pdf.notice
//
//import android.content.Context
//import android.util.Log
//import androidx.core.app.NotificationManagerCompat
//import com.google.firebase.messaging.RemoteMessage
//import com.zppko.pdfplus.nor.horus.HintExt02
//import com.zppko.pdfplus.nor.horus.HintSup
//import com.zppko.pdfplus.nor.horus.HintUtil
//import com.zppko.pdfplus.nor.remote.RemoteConfigHelper
//
///**
// * FCM 到达且应用在后台时，由应用主动创建的系统通知。
// *
// * 同时服务两类消息：
// * - data-only：由 FCM SDK 回调 onMessageReceived；
// * - notification（含 Firebase Console 下发的 Campaign）：在 MFrMessageRec.handleIntent
// *   处拦截、阻止 SDK 自动绘制后进入同一路径。
// *
// * 关键约束：
// * - 只在后台发送；前台收到 FCM 不新增任何通知（本轮验收口径）。
// * - 通知栏已有系统通知（定时/解锁/闹钟/上次 FCM 弹出的，槽位 70101/70102）时不再补发。
// * - 复用「定时(time)」目录的文案与样式，发送路径与定时通知一致（含 pop_refresh 重发）；
// *   场景类型用 [HintUtil.CO_S_TYPE_FCM]，埋点 pos_id/trigger_source 均为 fcm。
// *   不使用 FCM 下发的 title/body/action，也不使用默认通知样式。
// * - 点击进入 SpBootActivity（开屏），与其它通知入口保持一致。
// */
//internal object SpFcmNotification {
//
//    private const val TAG = "SpFcmNotify"
//
//    fun post(context: Context, message: RemoteMessage) {
//        Log.e(TAG, "post: FCM消息=${message.notification?.title} ${message.notification?.body}", )
//        if (HintExt02.isForeground) {
//            Log.i(TAG, "skip: app foreground")
//            return
//        }
//        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
//            Log.w(TAG, "skip: notifications disabled")
//            return
//        }
//        // 已有系统通知（70101/70102 任一）就不补发。
//        if (SpNotifyPermit.isNotificationShowing(context, SpNotifyIds.SYS) ||
//            SpNotifyPermit.isNotificationShowing(context, SpNotifyIds.SYS_2)
//        ) {
//            Log.i(
//                TAG,
//                "skip: system notification already showing testId=${mask(message.data["test_id"].orEmpty())}",
//            )
//            return
//        }
//        // 复用「定时(time)」目录的文案/样式；场景类型走 fcm（含 pop_refresh 重发）。
////        val config = RemoteConfigHelper.getReceiveConfig()
////        val popSetting = HintSup.forType(HintUtil.CO_S_TYPE_INTERVAL).random()
////        HintUtil.coJustSend(config, popSetting, HintUtil.CO_S_TYPE_FCM)
////        Log.i(TAG, "post via system-notification path testId=${mask(message.data["test_id"].orEmpty())}")
//    }
//
//    private fun mask(value: String): String =
//        if (value.length <= 8) "***" else value.substring(0, 6) + "***"
//}
