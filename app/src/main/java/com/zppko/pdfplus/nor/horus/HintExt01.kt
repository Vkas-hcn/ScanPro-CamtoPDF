package com.zppko.pdfplus.nor.horus

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.DecoratedCustomViewStyle
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scanpro.cam.pdf.R
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
        return Zppkoo.Companion.ii
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
            setSmallIcon(R.drawable.ic_scan_small)
            setPriority(NotificationCompat.PRIORITY_DEFAULT)
            setCategory(NotificationCompat.CATEGORY_SERVICE)
            setOnlyAlertOnce(true)
            setGroupSummary(false)
            setGroup("")
            setSound(null)
            setOngoing(true)
            setDeleteIntent(deleteIndex)
        }

        if (AppCacheUtil.isAndroid12()) {
            builder.setCustomContentView(createSSSview())
            builder.setCustomBigContentView(createLLLview())
            builder.setCustomHeadsUpContentView(createLLLview())
            builder.setStyle(DecoratedCustomViewStyle())
        } else {
            builder.setCustomContentView(createLLLview())
            builder.setCustomBigContentView(createLLLview())
            builder.setCustomHeadsUpContentView(createLLLview())
        }
        return builder.build()
    }


    private fun createSSSview(): RemoteViews {
        val fpkName = coIntance().packageName
        val view = RemoteViews(fpkName, R.layout.notification_tool_bar_simple)
        bindingClickEvent(view)
        return view
    }

    private fun createLLLview(): RemoteViews {
        val fpkName = coIntance().packageName
        val view = RemoteViews(fpkName, R.layout.notification_tool_bar_large)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            view.setViewPadding(R.id.root, 0, 20, 0, 20)
        }
        bindingClickEvent(view)
        return view
    }


    private fun bindingClickEvent(view: RemoteViews) {
        val ctx = coIntance()
        view.setOnClickPendingIntent(R.id.delete_cache, createIntent(Route.Garbage, CO_S_TYPE_BAR))
        view.setTextViewText(R.id.tv_delete_cache, ctx.getString(R.string.pop_top_1))

        view.setOnClickPendingIntent(R.id.delete_large, createIntent(Route.LargeFile, CO_S_TYPE_BAR))
        view.setTextViewText(R.id.tv_delete_large, ctx.getString(R.string.pop_top_2))

        view.setOnClickPendingIntent(R.id.delete_dup, createIntent(Route.DuplicateFile, CO_S_TYPE_BAR))
        view.setTextViewText(R.id.tv_delete_dup, ctx.getString(R.string.pop_top_3))
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