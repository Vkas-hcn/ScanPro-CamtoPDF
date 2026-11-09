package com.zppko.pdfplus.nor.ads.tool

import android.content.res.Resources
import android.os.Build

object UserParms {


    //是否是欧盟国家
    fun isOm():Boolean{
        val country = CT()
        val codes= listOf("SG","RO", "IE", "BG", "NO", "ES", "PL", "DE", "CZ",
            "GR", "IT", "FR", "CY", "SE", "DK", "HU", "SI",
            "CH", "BE", "FI", "GB", "IS", "AT", "PT", "SK",
            "HR", "LT", "MT", "LV", "NL", "LU", "EE", "LI")
        return codes.any { it.equals(country,true) }
    }

    //国家
    fun CT():String{
        val tt = "hf533dcvbbv"
        val country= SpUtil.get(tt,"")
        if (country==""){
            val new = Resources.getSystem().configuration.locales.get(0).country
            SpUtil.put(tt,new)
            return new
        }else{
            return country
        }
    }

    fun isSamsung():Boolean= Build.MANUFACTURER.equals("samsung",true)

    private fun isKorean():Boolean= CT().equals("KR",true)

    fun isKo2Samsung(): Boolean {
        return isKorean() && isSamsung()
    }

}
