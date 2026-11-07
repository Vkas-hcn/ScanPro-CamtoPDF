package com.zppko.pdfplus.nor.horus

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.DecoratedCustomViewStyle
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scanpro.cam.pdf.R
import com.smart.pdf.scanner.util.LocaleHelper
import com.zppko.pdfplus.nor.Route
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.ads.tool.UserParms
import com.zppko.pdfplus.nor.ss.CCPdfToolService

import com.zppko.pdfplus.nor.tool.AppCacheUtil

// 前台服务相关
object HintExt01 {
    const val CO_FIX_NOTIFY_ID = 4856
    const val CO_S_TYPE_BAR = 3

    fun coIntance(): Context {
        return Zppkoo.ii
    }

    fun startJob(context: Context) {
        AbcLaunceJob.enqueueWork(context)
    }

    //启动前台服务
    fun launchFg(coShowOneNt: Boolean = false) {
//        if (UserParms.isKo2Samsung()) return
        if (AppCacheUtil.isAndroid12() && coShowOneNt) {
            sendStickNt()
        } else {
            runCatching {
                if (coRuning()) {
//                    if (UserParms.isSamsung()) return
                    if (hasnTify(coIntance(), CO_FIX_NOTIFY_ID)) {
                        sendStickNt()
                    } else {

                    }
                } else {
                    ContextCompat.startForegroundService(
                        coIntance(), Intent(coIntance(), CCPdfToolService::class.java)
                    )
                }
            }.onFailure { error ->
                // Migration adaptation: expose startup failures without changing service policy.
                Log.e("HintExt01", "launchFg failed", error)
            }
        }
    }

    private fun coRuning(): Boolean = CCPdfToolService.isRunning

    private fun sendStickNt() {
        val notify = createStickNt()
        val manager = coIntance().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(CO_FIX_NOTIFY_ID, notify)
    }


    fun createStickNt(): Notification {
        val context = coIntance()

        val channelName = "nor_message"
        val channel:NotificationChannelCompat = NotificationChannelCompat
            .Builder(channelName, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setSound(null, null)
            .setLightsEnabled(false)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .setName(channelName)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)

        val deleteIndex:PendingIntent = getBroadcastPendingIntent(context)

        val builder = NotificationCompat.Builder(context, channelName).apply {
            setSmallIcon(R.drawable.sp_ic_notify)
            setPriority(NotificationCompat.PRIORITY_DEFAULT)
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            setOnlyAlertOnce(true)
            setShowWhen(false)
            setGroupSummary(false)
            setGroup("")
            setSound(null)
            setOngoing(true)
            setDeleteIntent(deleteIndex)
        }

        builder.setStyle(DecoratedCustomViewStyle())
        if (AppCacheUtil.isAndroid12()) {
            builder.setCustomContentView(createSSSview())
            builder.setCustomBigContentView(createLLLview())
        } else {
            val big = createLLLview()
            builder.setCustomContentView(big)
            builder.setCustomBigContentView(big)
            builder.setCustomHeadsUpContentView(big)
        }
        return builder.build()
    }


    private fun createSSSview(): RemoteViews {
        return HintNotifyViews.persistent(small = true)
    }

    private fun createLLLview(): RemoteViews {
        return HintNotifyViews.persistent(small = false)
    }


    private fun bindingClickEvent(view: RemoteViews) {
        HintNotifyViews.bindPersistent(view)
    }

    private fun hasnTify(context: Context, id: Int): Boolean =
        NotificationManagerCompat.from(context).activeNotifications.all { it.id != id }

    fun createIntent(action: String, showType: Int): PendingIntent {
        return HintUtil.createIntent(action,showType,CO_FIX_NOTIFY_ID)
    }





    fun getBroadcastPendingIntent (context: Context): PendingIntent {
        val flags:Int = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val intent = Intent(context, WidgetPinnedReceiver::class.java)
        intent.action = WidgetPinnedReceiver.ACTION_SHOW_NOTIFY

        val pendingIntent: PendingIntent = PendingIntent.getBroadcast(context, 1001, intent, flags)
        return pendingIntent
    }
}