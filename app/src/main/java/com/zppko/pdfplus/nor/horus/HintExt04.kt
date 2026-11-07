package com.zppko.pdfplus.nor.horus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.buncead.common.util.Util
import com.zppko.pdfplus.nor.Route
import com.zppko.pdfplus.nor.ads.tool.SpUtil
import com.zppko.pdfplus.nor.ads.tool.UserParms

import com.zppko.pdfplus.nor.horus.HintUtil.CO_S_TYPE_ALARM
import com.zppko.pdfplus.nor.horus.HintUtil.CO_S_TYPE_INTERVAL
import com.zppko.pdfplus.nor.horus.HintUtil.CO_S_TYPE_UNLOCK
import com.zppko.pdfplus.nor.remote.ConfigHelper

import com.zppko.pdfplus.nor.remote.RemoteConfigHelper
import com.zppko.pdfplus.nor.tool.Tool.isToday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

object HintExt04 {


    private val CO_SP_UNLOCK_N = "g465ds"
    private val CO_SP_UNLOCK_T = "4gs6df65"
    private val CO_SP_INTERAL_N = "g5sdg545"
    private val CO_SP_INTERAL_T = "g456srrfa"
    private val CO_SP_ALARM_N = "g45dfa511"
    private val CO_SP_ALARM_T = "gy54f66g"

    fun my_test_send(){
        val config: UserPop = RemoteConfigHelper.getReceiveConfig()

        Util.iii { "my_test_send()aaa" }
        if (coCanSMT(config, HintUtil.CO_S_TYPE_INTERVAL)) {
            Util.iii { "my_test_send()111" }
            val random = HintSup.forType(CO_S_TYPE_INTERVAL).random()

            if ( ConfigHelper.canShowAdsss_a2()) {
                Util.iii { "my_test_send()222" }
                HintMt.coJustSendMt(random)
            }

            if (coCanS()) {
                Util.iii { "my_test_send()333" }
                HintUtil.coJustSend(config, random, CO_S_TYPE_INTERVAL)
            }
        }
    }

    fun my_test_send2(){
        val config: UserPop = RemoteConfigHelper.getReceiveConfig()

        val random = HintSup.forType(CO_S_TYPE_INTERVAL).random()
        HintMt.coJustSendMt(random)
        HintUtil.coJustSend(config, random, CO_S_TYPE_INTERVAL)
    }

    //定时弹窗
    suspend fun sendTone() {
        val config: UserPop = RemoteConfigHelper.getReceiveConfig()

        if (coCanSMT(config, HintUtil.CO_S_TYPE_INTERVAL)) {
            val random = HintSup.forType(CO_S_TYPE_INTERVAL).random()
            withContext(Dispatchers.Main) {
                if ( ConfigHelper.canShowAdsss_a2()) {
//                if (EVenPlusUploader.canShow() && RemoteConfigHelper.canShowAdsss_a2()) {
                    HintMt.coJustSendMt(random)
                } else {
//                    Log.e(
//                        "通知弹窗",
//                        "tba_clock: ${EVenPlusUploader.canShow()}，referrer: ${RemoteConfigHelper.canShowAdsss()}",
//                    )
                }
            }
//            val config = RemoteConfigHelper.getReceiveConfig()
            if (coCanS()) {
                withContext(Dispatchers.Main) {
                    HintUtil.coJustSend(config, random, CO_S_TYPE_INTERVAL)
//                    Log.e(
//                        "通知弹窗",
//                        "弹出定时通知:[${SpUtil.get(CO_SP_INTERAL_N, 0L) + 1}/${config.fmtCount}]",
//                    )
                }
            }
            SpUtil.put(CO_SP_INTERAL_T, System.currentTimeMillis())
            SpUtil.put(CO_SP_INTERAL_N, SpUtil.get(CO_SP_INTERAL_N, 0L) + 1L)
        }

    }

    //解锁弹窗
    fun sendTtwo() {
        val config: UserPop = RemoteConfigHelper.getReceiveConfig()

        if (coCanSMT(config, HintUtil.CO_S_TYPE_UNLOCK)) {
            val random = HintSup.forType(CO_S_TYPE_UNLOCK).random()
            if ( ConfigHelper.canShowAdsss_a2()) {
//            if (EVenPlusUploader.canShow() && RemoteConfigHelper.canShowAdsss_a2()) {
                HintMt.coJustSendMt(random)
            } else {
//                Log.e("通知弹窗","tba_clock: ${EVenPlusUploader.canShow()}，referrer: ${RemoteConfigHelper.canShowAdsss()}",)
            }
//            val config = RemoteConfigHelper.getReceiveConfig()

            if (config.delayTm <= 0){
                if (coCanS()) {
                    HintUtil.coJustSend(config, random, CO_S_TYPE_UNLOCK)
//                Log.e("hhhh通知弹窗", "弹出解锁通知:[${SpUtil.get(CO_SP_UNLOCK_N, 0L) + 1}/${config.fmuCount}]", )
                }
            }else {
                CoroutineScope(Dispatchers.IO).launch {
                    delay(config.delayTm*60_000L)
                    if (coCanS()) {
                        withContext(Dispatchers.Main){
                            HintUtil.coJustSend(config, random, CO_S_TYPE_UNLOCK)
                        }
                    }
                }
            }

            SpUtil.put(CO_SP_UNLOCK_T, System.currentTimeMillis())
            SpUtil.put(CO_SP_UNLOCK_N, SpUtil.get(CO_SP_UNLOCK_N, 0L) + 1L)
        }
    }

    //闹钟弹窗
    fun sendTthree() {
        val config: UserPop = RemoteConfigHelper.getReceiveConfig()

        if (coCanSMT(config, HintUtil.CO_S_TYPE_ALARM)) {
            val random = HintSup.forType(CO_S_TYPE_ALARM).random()
            if (ConfigHelper.canShowAdsss_a2()) {
//            if (EVenPlusUploader.canShow() && RemoteConfigHelper.canShowAdsss_a2()) {
                HintMt.coJustSendMt(random)
            } else {
//                Log.e(
//                    "通知弹窗",
//                    "tba_clock: ${EVenPlusUploader.canShow()}，referrer: ${RemoteConfigHelper.canShowAdsss()}",
//                )
            }
//            val config = RemoteConfigHelper.getReceiveConfig()
            if (coCanS()) {
                HintUtil.coJustSend(config, random, CO_S_TYPE_ALARM)
//                Log.e(
//                    "通知弹窗",
//                    "弹出闹钟通知:[${SpUtil.get(CO_SP_ALARM_N, 0L) + 1}/${config.fmaCount}]",
//                )
            }
            SpUtil.put(CO_SP_ALARM_T, System.currentTimeMillis())
            SpUtil.put(CO_SP_ALARM_N, SpUtil.get(CO_SP_ALARM_N, 0L) + 1L)
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
//        if (UserParms.isKo2Samsung()) {
////            Log.e("hhhh通知弹窗-媒", "三星韩国用户")
//            return false
//        }

        if (HintExt02.isForeground) {
//            Log.e("hhhh通知弹窗-媒", "应用在前台")
            return false
        }

        val coLastPopTime = when (showType) {
            HintUtil.CO_S_TYPE_UNLOCK -> SpUtil.get(CO_SP_UNLOCK_T, 0L)
            HintUtil.CO_S_TYPE_INTERVAL -> SpUtil.get(CO_SP_INTERAL_T, 0L)
            else -> SpUtil.get(CO_SP_ALARM_T, 0L)
        }

        if (!coLastPopTime.isToday()) {
//            Log.e("hhhh通知弹窗-媒", "重制次数: ")
            when (showType) {
                HintUtil.CO_S_TYPE_UNLOCK -> SpUtil.put(CO_SP_UNLOCK_N, 0L)
                HintUtil.CO_S_TYPE_INTERVAL -> SpUtil.put(CO_SP_INTERAL_N, 0L)
                else -> SpUtil.put(CO_SP_ALARM_N, 0L)
            }
        }
        val coPopNum = when (showType) {
            HintUtil.CO_S_TYPE_UNLOCK -> SpUtil.get(CO_SP_UNLOCK_N, 0L)
            HintUtil.CO_S_TYPE_INTERVAL -> SpUtil.get(CO_SP_INTERAL_N, 0L)
            else -> SpUtil.get(CO_SP_ALARM_N, 0L)
        }
        val coNumLimit = when (showType) {
            HintUtil.CO_S_TYPE_UNLOCK -> config.couCount
            HintUtil.CO_S_TYPE_INTERVAL -> config.cotCount
            else -> config.coaCount
        }
        val coTimeLimit = when (showType) {
            HintUtil.CO_S_TYPE_UNLOCK -> config.couTime
            HintUtil.CO_S_TYPE_INTERVAL -> config.cotTime
            else -> config.coaTime
        }

        if (coTimeLimit * 60_000 > System.currentTimeMillis() - coLastPopTime) {
//            Log.e("hhhh通知弹窗-媒", "冷却时间不够 后台设置间隔${coTimeLimit}分钟，上次弹窗时间：${timeFormat(coLastPopTime)}，当前时间：${ timeFormat(System.currentTimeMillis()) }" )
            return false
        }

        if (coNumLimit <= coPopNum) {
//            Log.e("hhhh通知弹窗-媒", "次数达到限制 后台设置上限${coNumLimit}次 ,当前弹窗次数：${coPopNum}")
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