package com.zppko.pdfplus.nor.horus

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.DecoratedCustomViewStyle
import kotlin.random.Random
import com.bockcock.adjust.Pin
import com.bockcock.adjust.Pt
import com.buncead.common.util.Util
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.boot.SpBootActivity

import com.zppko.pdfplus.nor.RAction
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.remote.ConfigHelper

import com.zppko.pdfplus.nor.tool.AppCacheUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object HintUtil {


    const val CO_SHOW_TYPE = "g2sd65"
    const val CO_SHOW_DATA = "gsd3ax"
    const val CO_S_TYPE_UNLOCK = 0
    const val CO_S_TYPE_INTERVAL = 1
    const val CO_S_TYPE_ALARM = 2



    fun getTypeName(showType: Int): String {
        val event = when (showType) {
            CO_S_TYPE_UNLOCK -> "type_unlock"
            CO_S_TYPE_ALARM -> "type_alarm"
            CO_S_TYPE_INTERVAL -> "type_time"
            else -> "type_recents"
        }
        return event
    }








    private fun coRandomCode(): Int {
        return Random.nextInt(1, Int.MAX_VALUE)
    }

    fun coIntance(): Context {
        return Zppkoo.Companion.ii
    }



    fun coJustSend(config: UserPop,popSetting: CallingSetting, showType: Int) {
        val event = when (showType) {
            CO_S_TYPE_UNLOCK -> "type_unlock"
            CO_S_TYPE_ALARM -> "type_alarm"
            CO_S_TYPE_INTERVAL -> "type_time"
            else -> "type_recents"
        }
//        EVEtnUploader.mine("trigger_inexact_notify", mapOf("type" to event))

        val ntf_id: Int = popSetting.id
//        Pin.event(fsk(ntf_id))
        Pin.event(Pin.notification_trigger, Pt("pos_id",event).put("trigger_source",event))

        val manager = getManager()

        if (ConfigHelper.canShowAdsss_a2()){
            val rpCount = config.rpCount
            if (rpCount==0){
             Util.iii { "ntf_a01 rpCount=${rpCount}, isVoice=${config.isVoice}" }
                val isShowVoice = config.isVoice == 0
                val notification = createOnceNt(isShowVoice, popSetting,ntf_id, showType)
                manager.notify(ntf_id, notification)
            }else{
                val rpVoiceCt = config.rpVoiceCt // 通知重复次数的声音次数

                val ntfSoundYes = createOnceNt(true, popSetting,ntf_id, showType)
                val ntfSoundNo = createOnceNt(false, popSetting,ntf_id, showType)
				Util.iii { "ntf_a02 rpCount=${rpCount}, rpVoiceCt=${rpVoiceCt}" }
                var sendCount = 0
                rpJob?.cancel()
                rpJob = CoroutineScope(Dispatchers.IO).launch {
                    repeat(rpCount){
                        Util.iii { "ntf_a04 ${rpCount} rp++" }
                        if (HintExt02.isForeground){
                            rpJob?.cancel()
                        }
                        withContext(Dispatchers.Main){
                            if (sendCount>=rpVoiceCt){
                                manager.notify(ntf_id, ntfSoundNo)
                            }else{
                                manager.notify(ntf_id, ntfSoundYes)
                            }
                        }
                        sendCount++
                        delay(2000)
                    }
                }

            }
        }else{
            val isShowVoice = config.isVoice == 0
            val notification = createOnceNt(isShowVoice, popSetting,ntf_id, showType)
            manager.notify(ntf_id, notification)
        }


//        Log.e("通知弹窗", "开始弹窗")
    }

    fun stopCalling(){
        rpJob?.cancel()
    }
    private var rpJob: Job?=null















//    fun fsk(id:Int): String {
//        if (id== 70101){
//            return Pin.trigger_scanpdf_ntf
//        } else  if (id== 70102){
//            return Pin.trigger_imgpdf_ntf
//        } else  if (id== 70103){
//            return Pin.trigger_depdf_ntf
//        }
//        return Pin.trigger_scanpdf_ntf
//    }


    private fun createOnceNt(
        is_voice: Boolean,
        popSetting: CallingSetting,
        id: Int,
        showType: Int
    ): Notification {

        val channelName = ChannelUtil().get(is_voice)
        val ctx = coIntance()
        val title: String = ctx.getString(popSetting.title)
        val msg: String = ctx.getString(popSetting.msg)
        val action: String = popSetting.route

//        val icon = when (action) {
//            Route.Garbage -> R.mipmap.ntf_def_ic_photo
//            Route.DuplicateFile -> R.mipmap.ntf_def_ic_files
//            Route.LargeFile -> R.mipmap.ntf_def_ic_scan
//            else -> R.mipmap.ntf_def_ic_scan
//        }
//        val largeIcon = BitmapFactory.decodeResource(coIntance().resources, icon)
        val builder = NotificationCompat.Builder(coIntance(), channelName).apply {
            setSmallIcon(R.drawable.ic_scan_small)
//            setLargeIcon(largeIcon)
            setGroupSummary(false)
            setGroup("")
            setAutoCancel(true)
            setContentIntent(createIntent(action, showType,id))
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setOngoing(false)
        }

        if (AppCacheUtil.isAndroid12()) {
            builder.setCustomContentView(createF_SmallNotify(popSetting,title,msg,  action))
            builder.setCustomHeadsUpContentView(createF_SmallNotify(popSetting,title,msg,  action))
            builder.setCustomBigContentView(createExpandNotify(popSetting,title,msg,  action))
            builder.setStyle(DecoratedCustomViewStyle())
        } else {
            builder.setCustomContentView(crateF_LargeNotify(popSetting,title, msg, action))
            builder.setCustomHeadsUpContentView(createExpandNotify(popSetting,title,msg,  action))
            builder.setCustomBigContentView(crateF_LargeNotify(popSetting,title,msg,  action))
        }
        return builder.build()
    }


    private fun createF_SmallNotify(popSetting: CallingSetting,title: String,content: String,  action: String): RemoteViews {
        val fpkName = coIntance().packageName
        val view = RemoteViews(fpkName, R.layout.notification_cos)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            view.setViewPadding(R.id.root, 20, 20, 20, 20)
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            view.setViewVisibility(R.id.cover, View.GONE)
        }

        bindingResourceView(view,popSetting, title, content)
        return view
    }
    private fun crateF_LargeNotify(popSetting: CallingSetting,title: String,content: String,  action: String): RemoteViews {
        val fpkName = coIntance().packageName
        val view = RemoteViews(fpkName, R.layout.notification_col)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            view.setViewPadding(R.id.root, 20, 20, 20, 20)
        }

        bindingResourceView(view,popSetting, title, content)
        return view
    }

    private fun createExpandNotify(popSetting: CallingSetting,title: String,content: String,  action: String): RemoteViews {
        val fpkName = coIntance().packageName
        val view = RemoteViews(fpkName, R.layout.notification_coy)
        bindingResourceView(view,popSetting, title, content)
        return view
    }


    private fun bindingResourceView(
        view: RemoteViews,
        popSetting: CallingSetting,
        title: String,
        content: String
    ) {
        val ctx = coIntance()
        val btnStr: String = ctx.getString(popSetting.btnStr)

//        view.setTextViewText(R.id.title, title)
        view.setTextViewText(R.id.content, content)
        view.setTextViewText(R.id.btn, btnStr)
        view.setImageViewResource(R.id.cover, popSetting.bigImg)
        view.setInt(R.id.btn, "setBackgroundResource", popSetting.btnBg)
    }



    private fun coFlag(): Int {
        return if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    fun createIntent(action: String, showType: Int,id:Int): PendingIntent {
        val intent = Intent(coIntance(), SpBootActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(RAction.ClickNav, action)
        intent.putExtra(CO_SHOW_TYPE, showType)
        intent.putExtra(CO_SHOW_DATA, id)
        return PendingIntent.getActivity(coIntance(), coRandomCode(), intent, coFlag())
    }


    private fun getManager(): NotificationManager {
        return coIntance().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }


}