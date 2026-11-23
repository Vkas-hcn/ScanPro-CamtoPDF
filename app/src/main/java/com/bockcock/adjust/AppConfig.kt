package com.bockcock.adjust

import com.adjust.sdk.AdjustConfig
import com.scanpro.cam.pdf.BuildConfig

object AppConfig {

    fun isBundle(): Boolean {
        return BuildConfig.IS_BUNDLE
    }


    // TODO: xxx adjust 检查下面两个方法
    fun getAdJustEnv(): String {
        if (AppConfig.isBundle()) {
            val environment: String = AdjustConfig.ENVIRONMENT_PRODUCTION
            return environment
        } else {
            val environment: String = AdjustConfig.ENVIRONMENT_SANDBOX
            return environment
        }
    }


    fun getTradPlusEventName(id: String): String? {
        if (isBundle()){
            return posIDMapRelease.get(id)
        }else{
            return posIDMapTest.get(id)
        }
    }
    val posIDMapRelease = mutableMapOf<String,String>(
        "0D8D3A9B057C4827D87B426CB6474912" to "cuu_launch",
        "9F1231142C0FF2A55D6E72F414B4AC12" to "co_scan_int",
        "B2CA3CE97BFE7EB5B5704495928C7112" to "co_result_int",
        "923B0C2B262328595283C4B85C7DAD12" to "co_main_nat",
    )
    val posIDMapTest = mutableMapOf<String,String>(
        "D9118E91DD06DF6D322369455CAED618" to "cuu_launch",
        "E609A0A67AF53299F2176C3A7783C46D" to "co_scan_int",
        "E609A0A67AF53299F2176C3A7783C46Dx" to "co_result_int",
        "DDBF26FBDA47FBE2765F1A089F1356BF" to "co_main_nat",
    )
}