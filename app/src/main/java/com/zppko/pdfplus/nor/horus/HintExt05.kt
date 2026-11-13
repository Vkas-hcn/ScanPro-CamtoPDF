package com.zppko.pdfplus.nor.horus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.ads.tool.SpUtil
import com.zppko.pdfplus.nor.ads.tool.UserParms
import com.zppko.pdfplus.nor.remote.ConfigHelper
import com.zppko.pdfplus.nor.remote.RemoteConfigHelper
import kotlin.random.Random

object HintExt05 {
    private var CO_SP_ALARM_NEXT_TIME = "hg6fd56s5ds"



    fun coIntance(): Context {
        return Zppkoo.Companion.ii
    }

    private fun coRandomCode(): Int {
        return Random.nextInt(1, Int.MAX_VALUE)
    }

    //闹钟
    fun markLing() {

        if (!ConfigHelper.canShowAdsss_a2()) {
//            Log.e("通知弹窗", "封控 不设置闹钟")
            return
        }
        // 不要
//        if (UserParms.isKo2Samsung()) {
////            Log.e("通知弹窗", "韩国且三星用户，不设置闹钟")
//            return
//        }
        val config = RemoteConfigHelper.getReceiveConfig()
        if (config.coaTime <= 0) {
//            Log.e("通知弹窗", "远程配置时间小于等于0，不设置闹钟")
            return
        }
        val lastSetTime = SpUtil.get(CO_SP_ALARM_NEXT_TIME ,0L)
        if (lastSetTime > System.currentTimeMillis()) {
//            Log.e("通知弹窗", "已经设置过闹钟了[${timeFormat(lastSetTime)}]，不需要设置闹钟")
            return
        }
        val alarmManager = coIntance().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pIndent = PendingIntent.getBroadcast(
            coIntance(), coRandomCode(),
            Intent(coIntance(), TChWorkerRec::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val setTime = System.currentTimeMillis() + (config.cotTime * 60_000L)
        SpUtil.put(CO_SP_ALARM_NEXT_TIME, setTime)
        AlarmManagerCompat.setAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            setTime,
            pIndent
        )
//        EVEtnUploader.mine("set_inexact_alarm")
//        Log.e("通知弹窗", "设置闹钟成功[${timeFormat(setTime)}]")
    }

}