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
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.boot.SpBootActivity
import com.scanpro.cam.pdf.notice.SpJumpType
import com.scanpro.cam.pdf.notice.SpNotifyCreator
import com.scanpro.cam.pdf.notice.SpNotifyExtras
import com.scanpro.cam.pdf.notice.SpNotifyUtils
import com.scanpro.cam.pdf.notice.SpPopRefresh
import com.scanpro.cam.pdf.notice.notificationTriggerSource
import com.smart.pdf.scanner.util.LocaleHelper

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
        return Zppkoo.ii
    }



    fun coJustSend(config: UserPop,popSetting: CallingSetting, showType: Int) {
        val sceneType = when (showType) {
            CO_S_TYPE_UNLOCK -> "unlock"
            CO_S_TYPE_ALARM -> "alarm"
            CO_S_TYPE_INTERVAL -> "time"
            else -> "recent"
        }
        val triggerSource = notificationTriggerSource(sceneType)

        val ntf_id: Int = popSetting.id
//        Pin.event(fsk(ntf_id))
        Pin.event(Pin.notification_trigger, Pt("pos_id", sceneType).put("trigger_source", triggerSource))

        val manager = getManager()
        val isShowVoice = config.isVoice == 0
        // 恢复被 784583c 删除的 pop_refresh 重发循环：
        // pop_refresh_switch==1 且亮屏时，先发响铃版，再按 interval 秒重发静音版，直到 times 次；
        // 熄屏/回前台立即停止。开关关闭或非亮屏时退回单次发送。
        val popRefresh = SpAdsHub.getOrNull()?.popRefreshOf() ?: SpPopRefresh()
        val refreshEnabled = SpAdsHub.getOrNull()?.hasRemotePayload() == true &&
            SpAdsHub.getOrNull()?.hasPopRefreshConfig() == true &&
            ConfigHelper.canShowAdsss_a2() &&
            popRefresh.popRefreshSwitch == 1 &&
            SpNotifyUtils.isInteractive(coIntance())
        if (!refreshEnabled) {
            manager.notify(ntf_id, createOnceNt(isShowVoice, popSetting, ntf_id, showType))
            SpTrackEvents.notificationShow(coIntance(), sceneType, triggerSource)
            return
        }
        // 与 Apollo 一致：开关打开但 times=0 时本轮不发送。
        if (popRefresh.times <= 0) return

        val ntfSoundYes = createOnceNt(true, popSetting, ntf_id, showType)
        val ntfSoundNo = createOnceNt(false, popSetting, ntf_id, showType)
        val intervalMs = popRefresh.interval.coerceAtLeast(1) * 1000L
        rpJob?.cancel()
        rpJob = CoroutineScope(Dispatchers.IO).launch {
            for (index in 0 until popRefresh.times) {
                if (HintExt02.isForeground) return@launch
                withContext(Dispatchers.Main) {
                    manager.notify(
                        ntf_id,
                        if (index < config.rpVoiceCt) ntfSoundYes else ntfSoundNo,
                    )
                    if (index == 0) SpTrackEvents.notificationShow(coIntance(), sceneType, triggerSource)
                }
                delay(intervalMs)
                if (!SpNotifyUtils.isInteractive(coIntance()) || HintExt02.isForeground) {
                    return@launch
                }
            }
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

        val title: String = LocaleHelper.getLocalString(popSetting.title)
        val msg: String = LocaleHelper.getLocalString(popSetting.msg)
        val action: String = popSetting.route

//        val icon = when (action) {
//            Route.Garbage -> R.mipmap.ntf_def_ic_photo
//            Route.DuplicateFile -> R.mipmap.ntf_def_ic_files
//            Route.LargeFile -> R.mipmap.ntf_def_ic_scan
//            else -> R.mipmap.ntf_def_ic_scan
//        }
//        val largeIcon = BitmapFactory.decodeResource(coIntance().resources, icon)
        val builder = NotificationCompat.Builder(coIntance(), channelName).apply {
            setSmallIcon(R.drawable.sp_ic_notify)
            setContentTitle(title)
            setContentText(msg)
//            setLargeIcon(largeIcon)
            setGroupSummary(false)
            setGroup("")
            setAutoCancel(true)
            setContentIntent(createIntent(action, showType,id, popSetting.jumpType, showType))
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setOngoing(false)
        }

        builder.setStyle(DecoratedCustomViewStyle())
        if (AppCacheUtil.isAndroid12()) {
            builder.setCustomContentView(createF_SmallNotify(popSetting, title, msg, action))
            builder.setCustomBigContentView(createExpandNotify(popSetting, title, msg, action))
        } else {
            val big = crateF_LargeNotify(popSetting, title, msg, action)
            builder.setCustomContentView(big)
            builder.setCustomHeadsUpContentView(big)
            builder.setCustomBigContentView(big)
        }
        return builder.build()
    }


    private fun createF_SmallNotify(popSetting: CallingSetting,title: String,content: String,  action: String): RemoteViews {
        return HintNotifyViews.normal(popSetting, small = true)
    }
    private fun crateF_LargeNotify(popSetting: CallingSetting,title: String,content: String,  action: String): RemoteViews {
        return HintNotifyViews.normal(popSetting, small = false)
    }

    private fun createExpandNotify(popSetting: CallingSetting,title: String,content: String,  action: String): RemoteViews {
        return HintNotifyViews.normal(popSetting, small = false)
    }


    private fun bindingResourceView(
        view: RemoteViews,
        popSetting: CallingSetting,
        title: String,
        content: String
    ) {
        HintNotifyViews.bindNormal(view, popSetting, content)
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

    fun createIntent(action: String, showType: Int, id: Int): PendingIntent =
        createIntent(action, showType, id, null, null)

    fun createIntent(action: String, showType: Int, id: Int, jumpType: SpJumpType?, sceneType: Int? = null): PendingIntent {
        val intent = Intent(coIntance(), SpBootActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(RAction.ClickNav, action)
        jumpType?.let { intent.putExtra(SpNotifyExtras.JUMP_TYPE, it.name) }
        intent.putExtra(SpNotifyExtras.FROM_TYPE, when (showType) {
            HintExt01.CO_S_TYPE_BAR -> SpNotifyCreator.FROM_PERSISTENT
            4 -> SpNotifyCreator.FROM_MEDIA
            else -> SpNotifyCreator.FROM_SYS
        })
        intent.putExtra(SpNotifyExtras.SCENE_TYPE, when (sceneType ?: showType) {
            CO_S_TYPE_UNLOCK -> "unlock"
            CO_S_TYPE_ALARM -> "alarm"
            CO_S_TYPE_INTERVAL -> "time"
            HintExt01.CO_S_TYPE_BAR -> "resident"
            11 -> "recent"
            else -> "time"
        })
        intent.putExtra(CO_SHOW_TYPE, showType)
        intent.putExtra(CO_SHOW_DATA, id)
        return PendingIntent.getActivity(coIntance(), coRandomCode(), intent, coFlag())
    }


    private fun getManager(): NotificationManager {
        return coIntance().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }


}