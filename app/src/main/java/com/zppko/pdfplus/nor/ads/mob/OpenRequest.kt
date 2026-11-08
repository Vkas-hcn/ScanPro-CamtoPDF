package com.zppko.pdfplus.nor.ads.mob

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad

class OpenRequest {
    fun start(id: String?, loadCallback:AppOpenAdLoadCallback ){
//        Util.iii { "OpenRequest=" + id }
        if (id.isNullOrEmpty()){
        }else{
            AppOpenAd.load(Zppkoo.Companion.ii,id, AdRequest.Builder().build(), loadCallback)
        }
    }

     fun sendValueToServer(baseAdsLoad: BaseAdsLoad, adValue: AdValue, adSourceName: String?) {
        baseAdsLoad. sendValueToServer(adValue, adSourceName)
    }


}