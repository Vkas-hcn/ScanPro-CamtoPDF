package com.scanpro.cam.pdf.notice

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.keepalive.SpForegroundServiceLauncher
import com.scanpro.cam.pdf.util.AppLog

/**
 * FCM 入口（Topic [FCM_TOPIC]）。
 * 展示与判断见 [FcmNotificationDecision] / [FcmNotificationPresenter]，与系统通知 / 悬浮窗 / 常驻栏无关。
 *
 * **自定义样式前提**：服务端必须发 **data-only**（不要带 `notification` 节点）。
 * Console「发送测试消息」若带 Notification 标题，后台由系统画默认样式，不会进 [onMessageReceived]。
 */
class PdfFMS : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // 规范 E15 notification_received：下发消息到达客户端（pos_id / msg_id 均可空）
        val posId = message.data["pos_id"].orEmpty()
        SpTrackEvents.notificationReceived(posId, message.messageId.orEmpty())
        SpForegroundServiceLauncher.markTriggerType(TRIGGER_FCM)
        // 高优先级 FCM 属于 Android 12+ 允许后台启动前台服务的系统豁免，直接拉；
        // 普通优先级走 Job 兜底（大概率会被系统拒，但不影响通知本身）。
        val highPriority = message.priority == RemoteMessage.PRIORITY_HIGH
        if (highPriority) {
            runCatching { SpForegroundServiceLauncher.launchForegroundService(applicationContext) }
                .onFailure { AppLog.notification.w("FCM 高优先级保活拉起失败", it) }
        }
        runCatching { SpForegroundServiceLauncher.startLaunchService(applicationContext) }
            .onFailure { AppLog.notification.w("FCM 保活拉起失败", it) }
    }

    companion object {
        const val FCM_TOPIC = "scanpro_pdf"

        private const val TRIGGER_FCM = "fcm"

        fun subscribeTopic(source: String = "启动") {
            AppLog.notification.i("FCM 主题订阅开始：主题=$FCM_TOPIC, 来源=$source")
            runCatching {
                FirebaseMessaging.getInstance().subscribeToTopic(FCM_TOPIC)
                    .addOnSuccessListener {
                        AppLog.notification.i(
                            "FCM 主题订阅成功：主题=$FCM_TOPIC, 来源=$source",
                        )
                    }
                    .addOnFailureListener { error ->
                        AppLog.notification.w(
                            "FCM 主题订阅失败：主题=$FCM_TOPIC, 来源=$source",
                            error,
                        )
                    }
            }.onFailure { error ->
                AppLog.notification.w(
                    "FCM 主题订阅发起失败：主题=$FCM_TOPIC, 来源=$source",
                    error,
                )
            }
        }
    }
}