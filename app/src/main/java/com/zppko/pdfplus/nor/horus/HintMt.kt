package com.zppko.pdfplus.nor.horus

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.bockcock.adjust.Pin
import com.bockcock.adjust.Pt
import com.scanpro.cam.pdf.R
import com.smart.pdf.scanner.util.LocaleHelper
import com.zppko.pdfplus.nor.horus.HintUtil.coIntance
import com.zppko.pdfplus.nor.horus.HintUtil.createIntent

object HintMt {

    private val CO_MEDIA_NOTFIY_ID = 8562
    private val CO_S_TYPE_MEDIA = 4

    private fun getManager(): NotificationManager {
        return coIntance().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }




//    fun stopCalling(){
//        mediaJob?.cancel()
//    }
//    private var mediaJob: Job?=null

    fun coJustSendMt(popSetting: CallingSetting) {
        runCatching {
//            EVEtnUploader.mine("trigger_media_notify")
//            Pin.event(Pin.trigger_media_ntf)
            Pin.event(Pin.notification_trigger, Pt("pos_id","type_media").put("trigger_source","type_media"))

            val notification = createMtNt(popSetting, CO_S_TYPE_MEDIA)
            val manager = getManager()
//            if (RemoteConfigHelper.mediaRepeatIsOn()){
//                mediaJob?.cancel()
//                mediaJob = CoroutineScope(Dispatchers.IO).launch {
//                    repeat(30){
//                        withContext(Dispatchers.Main){
//                            manager.notify(CO_MEDIA_NOTFIY_ID, notification)
//                        }
//                        delay(2000)
//                    }
//                }
//            }else{
            manager.notify(CO_MEDIA_NOTFIY_ID, notification)
//            }
        }
    }


    //创建媒体通知
    private fun createMtNt(
        popSetting: CallingSetting,
        showType: Int
    ): Notification {
        val channelName = "default_msg_ntf_low"

        NotificationManagerCompat.from(coIntance())
            .createNotificationChannel(
                NotificationChannelCompat.Builder(channelName, NotificationManagerCompat.IMPORTANCE_MAX)
                    .setSound(null, null)
                    .setLightsEnabled(false)
                    .setVibrationEnabled(false)
                    .setShowBadge(false)
                    .setName(channelName)
                    .build()
            )
        val action: String = popSetting.route
        val title: String = LocaleHelper.getLocalString(popSetting.msg)
        val btnStr: String = LocaleHelper.getLocalString(popSetting.btnStr)
        val largeIcon:Int = popSetting.bigImg

        val vectorDrawable: Drawable = ContextCompat.getDrawable(coIntance(), largeIcon)!!
        val bitmap: Bitmap = createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)

        val mMediaSession = MediaSessionCompat(coIntance(), "MediaSession")
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mMediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY)
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                .build()
        )
        mMediaSession.isActive = true


        val style = androidx.media.app.NotificationCompat.MediaStyle()
//            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(mMediaSession.sessionToken)

        val builder = NotificationCompat.Builder(coIntance(), channelName)
            .setStyle(style)
            .setLargeIcon(bitmap)
            .setSmallIcon(R.drawable.sp_ic_notify)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(createIntent(action, showType,CO_MEDIA_NOTFIY_ID, popSetting.jumpType, popSetting.sceneType))
            .setContentTitle(btnStr)
            .setContentText(title)

        return builder.build()
    }


    fun removeNOtify() {
        val manager = getManager()
        manager.cancel(CO_MEDIA_NOTFIY_ID)
    }

}