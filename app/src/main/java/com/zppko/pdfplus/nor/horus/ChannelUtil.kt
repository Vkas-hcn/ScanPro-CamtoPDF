package com.zppko.pdfplus.nor.horus

import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.zppko.pdfplus.nor.horus.HintUtil.coIntance

class ChannelUtil {

    // 获取是否有声音的 Channel
    fun get(is_voice: Boolean): String {

        if (is_voice){
            val channelName = "def_msg_ntf_ok"
            NotificationManagerCompat.from(coIntance())
                .createNotificationChannel(
                    NotificationChannelCompat.Builder(channelName, NotificationManagerCompat.IMPORTANCE_MAX)
                        .setLightsEnabled(true)
                        .setVibrationEnabled(true)
                        .setShowBadge(true)
                        .setName(channelName)
                        .build()
                )
            return channelName
        }else{
            val channelName = "def_msg_ntf_no"
            NotificationManagerCompat.from(coIntance())
                .createNotificationChannel(
                    NotificationChannelCompat.Builder(channelName, NotificationManagerCompat.IMPORTANCE_MAX)
                        .setSound(null, null)
                        .setLightsEnabled(true)
                        .setVibrationEnabled(true)
                        .setShowBadge(true)
                        .setName(channelName)
                        .build()
                )

            return channelName
        }

    }

}