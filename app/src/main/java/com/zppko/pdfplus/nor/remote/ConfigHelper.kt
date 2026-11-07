package com.zppko.pdfplus.nor.remote

import android.content.Context
import android.os.Build
import com.adjust.sdk.AdjustConfig
import com.buncead.common.util.Util
import com.scanpro.cam.pdf.BuildConfig
import com.scanpro.cam.pdf.config.SpConfigStore
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.tool.MKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

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

    // bi 下发用。B 的 isAcquiredUser 替代 A ConfigBean.volxo；不是 B 通知总闸。
    private fun canShowOnRemote(): Boolean {
        val payload = RemoteConfigHelper.currentPayload()
        if (SpConfigStore().dispatchMeta(payload).isAcquiredUser){
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
            if (canShowOnRemote() && isAdjustMuser() && !notCanShowOnDevice()){
                return true
            }
        }else{
            if (canShowOnRemote() && !notCanShowOnDevice()){
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
                return true
            }else{
                return false
            }
        }
        return true
    }

    // A AdJustAttribute 的持久化资格状态；绝不把 dispatch 买量当作 Adjust 回调。
    private const val SG_ADJUST_MUSER = 56216
    private const val KEY_ADJUST_MUSER_INFO = "h4s65gdf53cv2"
    private const val KEY_CHECK_NATURE_COUNT = "g4rf65gdfg23"

    private val adjustPrefs by lazy {
        // A KvUtil's original namespace/keys, without introducing another SDK/helper instance.
        Zppkoo.ii.getSharedPreferences("pdf_ad_save", Context.MODE_PRIVATE)
    }

    fun isAdjustMuser(): Boolean = adjustPrefs.getInt(KEY_ADJUST_MUSER_INFO, 0) == SG_ADJUST_MUSER

    /** Optional wiring on B's EXISTING AdjustConfig, before its one Adjust.initSdk call. */
    fun userTypeListener(config: AdjustConfig) {
        if (isAdjustMuser()) return
        val count = adjustPrefs.getInt(KEY_CHECK_NATURE_COUNT, 0)
        if (count > 5) return
        config.setOnAttributionChangedListener { attribution ->
            // Preserve A's captured registration-time count, not a new per-callback counter.
            applyAdjustAttribution(attribution?.network, count)
        }
    }

    /** Alternative forwarding entry for B's real callback; does not register/init an SDK. */
    fun onAdjustAttributionChanged(network: String?) {
        if (isAdjustMuser()) return
        val count = adjustPrefs.getInt(KEY_CHECK_NATURE_COUNT, 0)
        if (count > 5) return
        applyAdjustAttribution(network, count)
    }

    private fun applyAdjustAttribution(network: String?, count: Int) {
        if (network == null) return
        // Preserve A's non-bundle test suffix; B DEBUG substitutes for A IS_BUNDLE.
        val usrInfo = network.lowercase() + if (BuildConfig.DEBUG) "aaa" else ""
        if ("organic" == usrInfo) {
            adjustPrefs.edit().putInt(KEY_CHECK_NATURE_COUNT, count + 1).apply()
        } else {
            adjustPrefs.edit().putInt(KEY_ADJUST_MUSER_INFO, SG_ADJUST_MUSER).apply()
            RemoteConfigHelper.onEligibilityChanged()
        }
    }
}
