package com.zppko.pdfplus.nor.tool

import com.bockcock.adjust.Pin
import com.bockcock.adjust.Pt
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zppko.pdfplus.nor.horus.HintExt01


class MFrMessageRec : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {

        try {
            Pin.event(Pin.keepalive_trigger, Pt("trigger_type","fcm"))
        }catch (e: Exception) {
            e.printStackTrace()
        }

    }
}