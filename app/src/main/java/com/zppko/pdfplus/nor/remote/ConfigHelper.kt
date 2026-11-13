package com.zppko.pdfplus.nor.remote

import android.os.Build
import com.bockcock.adjust.AdJustAttribute
import com.bockcock.adjust.ConfigBean
import com.buncead.common.util.Util
import com.zppko.pdfplus.nor.ads.tool.SpUtil
import com.zppko.pdfplus.nor.ads.tool.UserReferrer
import com.zppko.pdfplus.nor.tool.MKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.text.contains

object ConfigHelper {

val TAG = "RemoteConfigHelper"


    private val sp_emm="gdf533a"
    fun isEmulatorBySystem() {
        if (MKV.have(sp_emm))return
        CoroutineScope(Dispatchers.IO).launch {
            //系统命令判断是否为模拟器
            runCatching {
                val process = Runtime.getRuntime().exec("getprop ro.kernel.qemu")
                BufferedReader(InputStreamReader(process.inputStream)).use {
                    val result = it.readLine()
                    MKV.putBoolean(sp_emm,"1" == result)
                }
            }
        }
    }


    // bi 下发用
    private fun canShowOnRemote(): Boolean {
        //为0非买量用户。为1买量用户
        if (ConfigBean.volxo == 1){
            Util.iii { "$TAG refer 买量用户" }
            return true
        }
        Util.iii { "$TAG refer 非买量用户" }
        return false
    }

    // firebase 下发用
//    private fun canShowOnRemote(): Boolean {
//        val data = RemoteConfigHelper.getRecentData(RemoteConfigHelper.KEY_REFERRER_CONFIG)
//        runCatching {
//            val jsonObject = JSONObject(data)
//            val show = jsonObject.getInt("ffsws")
//            //为0显示广告。为1，包含字符串情况才显示
//            if (show==0)return true
//            val jsonArr=jsonObject.getJSONArray("pcjcs")
//            val url= SpUtil.get(UserReferrer.icnoefg,"")
//            for ( i in 0 until jsonArr.length()){
//                val key=jsonArr.getString(i)
//                if (url.contains(key)){
////                    Util.iii { "referrer判断为'买量'" }
//                    return true
//                }
//            }
//        }
////        Util.iii { "referrer判断为“非买量”" }
//        return false
//    }

    private fun notCanShowOnDevice(): Boolean {
        if (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) {
            return true
        }
        val fingerprint = Build.FINGERPRINT
        if (fingerprint.startsWith("generic") || fingerprint.startsWith("unknown")) {
            return true
        }
        val hardware = Build.HARDWARE
        if (hardware.contains("goldfish") || hardware.contains("ranchu")) {
            return true
        }
        val model = Build.MODEL
        if (model.contains("google_sdk") ||
            model.contains("Emulator") ||
            model.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HOST.startsWith("Build")
        ) {
            return true
        }
        val product = Build.PRODUCT
        if (product.contains("sdk_google") ||
            product.contains("google_sdk") ||
            product.contains("sdk") ||
            product.contains("sdk_x86") ||
            product.contains("sdk_gphone64_arm64") ||
            product.contains("vbox86p") ||
            product.contains("emulator") ||
            product.contains("simulator")
        ) return true
        if (MKV.getBoolean(sp_emm,false)) return true
        return false
    }

    fun canShowAdsss_a2(): Boolean {
        // 0 开启(默认开启)，1关闭
        if (isOpAdjustCheck()){
            if (canShowOnRemote()&& AdJustAttribute.isAdjustMuser()&& !notCanShowOnDevice()){
                return true
            }
        }else{
            if (canShowOnRemote()&& !notCanShowOnDevice()){
                return true
            }
        }
        return false
    }

    private fun isOpAdjustCheck(): Boolean {
        val data = RemoteConfigHelper.getRecentData(RemoteConfigHelper.KEY_REFERRER_CONFIG)
        runCatching {
            val jsonObject = JSONObject(data)
            val show = jsonObject.getInt("op_just_usr")
            // 0 开启(默认开启)，1关闭

            if (show ==0){
//                Util.iii { "启用adjust 买量判断" }
                return true
            }else{
//                Util.iii { "不启用adjust 买量判断" }
                return false
            }
        }
//        Util.iii { "启动adjust 买量判断" }
        return true
    }


}