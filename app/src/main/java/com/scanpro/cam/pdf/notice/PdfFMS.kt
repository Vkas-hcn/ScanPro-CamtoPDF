package com.scanpro.cam.pdf.notice

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
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

    }

    companion object {
        const val FCM_TOPIC = "scanpro_pdf"

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