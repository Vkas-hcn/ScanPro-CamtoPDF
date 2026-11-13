package com.mymy.mymy.ads.poi


//import com.google.android.gms.ads.AdValue
import com.bockcock.adjust.FirebaseAna
import com.bockcock.adjust.Pin
import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy

object TrackAd {

    // posID :广告的位置(posName)
    fun loadStart(bean: TrackBean){
//        Util.iii { "point ad_request ${bean.toString()}" }
        FirebaseAna.logEvent("ad_request_${bean.adType}")

        AdTrackingHelperMy.trackAdRequestGMA(bean.adType, bean.posID, bean.sdkName, bean.adId)
        val requestStartTime: Long = System.currentTimeMillis()
        bean.startTime = requestStartTime
    }

    fun loadSuccess(bean: TrackBean){
//        Util.iii { "point ad_filled ${bean.toString()}" }
        FirebaseAna.logEvent("ad_filled_${bean.adType}")

        val duration:Long = System.currentTimeMillis() - bean.startTime
        AdTrackingHelperMy.trackAdFilledWithSource(
            adType = bean.adType,
            posId = bean.posID,
            sdkName = bean.sdkName,
            adId = bean.adId,
            adSource = bean.adSource,
            duration = duration
        )
    }
    fun loadFailed(bean: TrackBean, errCode: String, errMsg: String){
//        Util.iii { "point ad_failed ${bean.toString()}" }
        FirebaseAna.logEvent("ad_failed_${bean.adType}")

        AdTrackingHelperMy.trackAdFailed(
            adType = bean.adType,
            posId = bean.posID,
            sdkName = bean.sdkName,
            adId = bean.adId,
            errCode = errCode,
            errMsg = errMsg
        )
    }


    fun showSuccess(bean: TrackBean){
//        Util.iii { "point ad_show ${bean.toString()}" }
        // 不再上报。展示与展示级收入统一走 ad_impression_revenue
//        FirebaseAna.logEvent("ad_show_${bean.adType}")
//
//        AdTrackingHelperMy.trackAdImpressionWithSource(
//            adType = bean.adType,
//            posId = bean.posID,
//            sdkName = bean.sdkName,
//            adId = bean.adId,
//            adSource = bean.adSource,
//            isVisiblePct = 100,
//            scene = ""
//        )
        bean.startTime = System.currentTimeMillis()
    }

    fun showDismiss(bean: TrackBean){
//        Util.iii { "point ad_close ${bean.toString()}" }
        FirebaseAna.logEvent("ad_close_${bean.adType}")

        val stayTime: Long = (System.currentTimeMillis() - bean.startTime) / 1000
        val properties: MutableMap<String, Any> = HashMap<String, Any>()
        properties.put("ad_type", bean.adType)
        properties.put("pos_id", bean.posID)
        properties.put("sdk_name", bean.sdkName)
        properties.put("ad_id", bean.adId)
        properties.put("stay_duration", stayTime)
        TrackSDK.track("ad_close", properties)
//        AdTrackingHelperMy.trackAdClose(
//            adType = bean.adType,
//            posId = bean.posID,
//            sdkName = bean.sdkName,
//            adId = bean.adId,
//            stayDuration = stayTime
//        )
    }

    fun showClick(bean: TrackBean){
//        Util.iii { "point ad_click ${bean.toString()}" }
        FirebaseAna.logEvent("ad_click_${bean.adType}")

        AdTrackingHelperMy.trackAdClickWithSource(
            adType = bean.adType,
            posId = bean.posID,
            sdkName = bean.sdkName,
            adId = bean.adId,
            adSource = bean.adSource,
            clickArea = "cta"
        )
    }


//    fun showValue(trBean: TrackBean, value: AdValue){
//
//
//    }

    fun blocked(bean: TrackBean,blockReason: String){
        FirebaseAna.logEvent("ad_blocked_${bean.adType}")


        val properties: MutableMap<String, Any> =mutableMapOf()
        properties.put("ad_type", bean.adType)
        properties.put("pos_id", bean.posID)
        properties.put("sdk_name", bean.sdkName)
        properties.put("ad_id", bean.adId)
        properties.put("ad_source", bean.adSource)
        properties.put("click_area", "cta")

        properties.put("block_reason", blockReason)
        Pin.  myTrack("ad_blocked", properties, TrackPolicy.IMMEDIATE)

    }

    fun adShowTimeout(ad_type: String,pos_id:String,wait_ms: Long){
        FirebaseAna.logEvent("ad_show_timeout_${ad_type}")

        val properties: MutableMap<String, Any> =mutableMapOf()
        properties.put("ad_type", ad_type)
        properties.put("pos_id", pos_id)

        properties.put("wait_ms", wait_ms)
       Pin. myTrack("ad_show_timeout", properties, TrackPolicy.IMMEDIATE)
    }



}