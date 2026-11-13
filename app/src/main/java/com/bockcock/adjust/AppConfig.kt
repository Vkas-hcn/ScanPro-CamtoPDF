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

}