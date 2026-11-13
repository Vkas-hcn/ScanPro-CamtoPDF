package com.mymy.mymy.http

import android.os.Build
import com.bockcock.adjust.AppConfig
import com.flux.tracksdk.core.TrackSDK
import com.mymy.mymy.GaidUtil
import com.scanpro.cam.pdf.BuildConfig
import com.zppko.pdfplus.nor.ads.tool.SpUtil
import com.zppko.pdfplus.nor.ads.tool.UserReferrer
import org.json.JSONObject
import java.util.Locale
import kotlin.text.isEmpty

class BiParameter {

    fun createJson(): String {
        val locale = Locale.getDefault()
        val distinctId: String = TrackSDK.getInstance().effectiveDistinctId
        val referee = SpUtil.get(UserReferrer.icnoefg,"")
        val gaid = GaidUtil.mAdInfoId

        val brand:String = Build.BRAND; // 品牌
        val model:String = Build.MODEL; // 型号
        val sdkVersion = Build.VERSION.SDK_INT;

        // TODO: xxx 包名,混淆字段
        val jsonObj = JSONObject()
        if (AppConfig.isBundle()){
            jsonObj.put("o8vi9m","com.smart.pdf.scanner") // 包名
            jsonObj.put("b41y4b", BuildConfig.VERSION_NAME) // 版本号
            jsonObj.put("y6yugq",distinctId) // Distinct ID
            jsonObj.put("lf9xt1",gaid) // GAID
            jsonObj.put("8ns9wm","${brand}_${model}") // 手机型号
            jsonObj.put("u80kcz",referee) // refer
            jsonObj.put("lrxq4e","${locale.language}_${locale.country}") // 语言
            jsonObj.put("11sbai","${sdkVersion}") // 系统版本
        }else {
            jsonObj.put("palxen","com.smartpdfscanner.test.js") // 包名
            jsonObj.put("0wc71x", BuildConfig.VERSION_NAME) // 版本号
            jsonObj.put("6duosf",distinctId) // Distinct ID
            jsonObj.put("pmbl1b",gaid) // GAID
            jsonObj.put("gngvb8","${brand}_${model}") // 手机型号
            jsonObj.put("dlofz6",referee) // refer
            jsonObj.put("uhld32","${locale.language}_${locale.country}") // 语言
            jsonObj.put("qrw1re","${sdkVersion}") // 系统版本
        }

        val jsonStr = jsonObj.toString()
        if (referee.isEmpty()||gaid.isEmpty()){}else{
            pJson = jsonStr
        }
        return jsonStr
    }

    var pJson = ""
    fun getJson(){

    }
}