package com.zppko.pdfplus.nor.tool

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.scanpro.cam.pdf.analytics.SpTrackEvents


class MFrMessageRec : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // E15：下发消息到达客户端（FCM）。pos_id / msg_id 可空，取不到填空串。
        val posId = message.data?.get("pos_id").orEmpty()
        val msgId = message.messageId.orEmpty()
        SpTrackEvents.notificationReceived(posId, msgId)
    }
}
