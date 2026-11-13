package com.bockcock.adjust

import android.content.Context

import com.flux.tracksdk.core.TrackConfig
import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy
import com.scanpro.cam.pdf.BuildConfig

class BiHelp {

    fun initBI(context: Context){

        val baseUrl = if (AppConfig.isBundle()) "https://bi.googletogoogle.com" else "https://testbi.googletogoogle.com"
        val trackConfig = TrackConfig.Builder(context)
            .baseUrl(baseUrl)
            .debug(BuildConfig.DEBUG)
            .appId(context.packageName)
            .channel("google_play")
//            .enableAppsFlyer(false)
//            .appsFlyerDevKey("tZJUR7H5LUwXJ8M54fmHpf")
            .batchSize(20)
            .defaultUploadPolicy(TrackPolicy.IMMEDIATE)
            .uploadThreadCount(2)
            .connectTimeout(30_000)
            .readTimeout(30_000)
            .enablePiggyback(true)
            .build()
        TrackSDK.init(trackConfig)

    }


}