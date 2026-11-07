package com.zppko.pdfplus.nor.horus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zppko.pdfplus.nor.remote.ConfigHelper

import com.zppko.pdfplus.nor.remote.RemoteConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

object HintExt04_lan {


    const val CO_S_TYPE_LAUNCHER = 11


    var waitJob: Job?=null
    fun schedule(context: Context, reason: String ){
        waitJob?.cancel()
        waitJob = CoroutineScope(Dispatchers.IO).launch {
            val delaySec: Long = 4* 1000L
            delay(delaySec )

            sendTone()
        }
    }



    // 触发桌面弹窗
    suspend fun sendTone() {
        val config: UserPop = RemoteConfigHelper.getReceiveConfig()

        if (coCanSMT(config, CO_S_TYPE_LAUNCHER)) {
            val random = HintSup.forType(CO_S_TYPE_LAUNCHER).random()
            withContext(Dispatchers.Main) {
                if ( ConfigHelper.canShowAdsss_a2()) {
//                if (EVenPlusUploader.canShow() && RemoteConfigHelper.canShowAdsss_a2()) {
                    HintMt.coJustSendMt(random)
                } else {
//                    Log.e("通知弹窗", "tba_clock: ${EVenPlusUploader.canShow()}，referrer: ${RemoteConfigHelper.canShowAdsss()}", )
                }
            }
//            val config = RemoteConfigHelper.getReceiveConfig()
            if (coCanS()) {
                withContext(Dispatchers.Main) {
                    HintUtil. coJustSend(config,random, CO_S_TYPE_LAUNCHER)
//                    Log.e("通知弹窗", "弹出定时通知:[${SpUtil.get(CO_SP_INTERAL_N, 0L) + 1}/${config.fmtCount}]", )
                }
            }

        }

    }


    private fun coCanS(): Boolean {
        if (!coHavePermission()) {
//            Log.e("hhhh通知弹窗", "通知权限没有")
            return false
        }

        return true
    }




    private fun coCanSMT(config: UserPop,showType: Int): Boolean {

        if (!config.coIsSwitch) {
//            Log.e("hhhh通知弹窗-媒", "远程开关关闭")
            return false
        }

        // 不要
//        if (UserParms.isKorean() && UserParms.isSamsung()) {
////            Log.e("hhhh通知弹窗-媒", "三星韩国用户")
//            return false
//        }

        if (HintExt02.isForeground) {
//            Log.e("hhhh通知弹窗-媒", "应用在前台")
            return false
        }

        runCatching {
            val hour = toNightTime()
            val min = min(config.freeTimeOne, config.freeTimeTwo)
            val max = max(config.freeTimeOne, config.freeTimeTwo)
            if (hour !in (min..max)) {
//                Log.e("hhhh通知弹窗-媒", "非工作时间 ${min} - ${max}")
                return false
            }
        }

        return true
    }



    fun coHavePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(HintUtil.coIntance(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(HintUtil.coIntance()).areNotificationsEnabled()
        }
    }


    private fun toNightTime(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalTime.now().hour
        } else {
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }

//    private fun timeFormat(timestamp: Long): String {
//        val sdf = SimpleDateFormat("MM月dd日 HH:mm:ss", Locale.getDefault())
//        return sdf.format(Date(timestamp))
//    }


}