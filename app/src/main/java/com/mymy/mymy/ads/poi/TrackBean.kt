package com.mymy.mymy.ads.poi

import com.mymy.mymy.ads.poi.AdTrackingHelperMy

class TrackBean(val adType: String){
    var posID: String = ""
    var sdkName: String = ""
    var adId : String = ""
    var adSource: String = ""
    var startTime:Long = 0L



    companion object {

        fun getType(zType: String,posID: String, sdkName: String, adId : String): TrackBean {
            if (zType.equals("op")){
                return open(posID,sdkName,adId)
            }else if (zType.equals("int")){
                return inter(posID,sdkName,adId)
            }else {
                return  nati(posID,sdkName,adId)
            }
        }
        fun getTradBean(zType: String,posID: String, sdkName: String, adId : String): TrackBean {
            if (zType.equals("op")){
                return open(posID,sdkName,adId)
            }else if (zType.equals("int")){
                return inter(posID,sdkName,adId)
            }else {
              return  nati(posID,sdkName,adId)
            }
        }

        fun inter(posID: String, sdkName: String, adId : String): TrackBean {
            val bean = TrackBean(AdTrackingHelperMy.AdType.INTERSTITIAL)
            bean.posID = posID
            bean.sdkName = sdkName
            bean.adId = adId
            return  bean
        }

        fun open(posID: String, sdkName: String, adId : String): TrackBean {
            val bean = TrackBean(AdTrackingHelperMy.AdType.OPEN)
            bean.posID = posID
            bean.sdkName = sdkName
            bean.adId = adId
            return  bean
        }

        fun nati(posID: String, sdkName: String, adId : String): TrackBean {
            val bean = TrackBean(AdTrackingHelperMy.AdType.NATIVE)
            bean.posID = posID
            bean.sdkName = sdkName
            bean.adId = adId
            return  bean
        }

    }

    override fun toString(): String {
        return "TrackBean(adType='$adType', posID='$posID', sdkName='$sdkName', adId='$adId', adSource='$adSource', startTime=$startTime)"
    }

}