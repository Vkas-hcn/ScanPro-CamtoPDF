package com.bockcock.adjust

import android.os.Bundle
import com.scanpro.cam.pdf.CamPdfApp
import com.buncead.common.util.Util

import com.google.firebase.analytics.FirebaseAnalytics


object FirebaseAna {

    val TAG = "FirebaseAna"

    var firebaseAnalytics:FirebaseAnalytics?=null
    fun getAna(): FirebaseAnalytics {
        var ana:FirebaseAnalytics? = firebaseAnalytics
        if (ana == null){
            ana = FirebaseAnalytics.getInstance(CamPdfApp.app)
            firebaseAnalytics = ana
        }
        return ana
    }

    fun logEvent(eventName: String,map: Bundle?=null){
        runCatching {
            Util.iii { "$TAG event, eventName=${eventName}, bundle=${map}" }
            getAna().logEvent(eventName,map)
        }.onFailure { it.printStackTrace() }
    }

//    fun firebase_revenue(value: Double,currency: String){
//        val bun = Bundle()
//        bun.putDouble(FirebaseAnalytics.Param.VALUE, value)
//        bun.putString(FirebaseAnalytics.Param.CURRENCY, currency)
//        FirebaseAna.logEvent("ad_impression",bun)
////        FirebaseAna.logEvent("ad_impression_revenue",bun)
//    }

//    fun firebase_001(value: Double,currency: String){
//        val bun = Bundle()
//        bun.putDouble(FirebaseAnalytics.Param.VALUE, value)
//        bun.putString(FirebaseAnalytics.Param.CURRENCY, currency)
//        FirebaseAna.logEvent("total_ads_revenue_001",bun)
//    }

}