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
            jsonObj.put("0348op","com.scanpro.cam.pdf") // 包名
            jsonObj.put("4bq2wz", BuildConfig.VERSION_NAME) // 版本号
            jsonObj.put("yec72l",distinctId) // Distinct ID
            jsonObj.put("i2zqnf",gaid) // GAID
            jsonObj.put("kxuecn","${brand}_${model}") // 手机型号
            jsonObj.put("6qqrie",referee) // refer
            jsonObj.put("2094ym","${locale.language}_${locale.country}") // 语言
            jsonObj.put("e8netx","${sdkVersion}") // 系统版本
        }else {
            jsonObj.put("hne8oo","com.npro.mac.fdp.debug") // 包名
            jsonObj.put("ke3har", BuildConfig.VERSION_NAME) // 版本号
            jsonObj.put("1bfui7",distinctId) // Distinct ID
            jsonObj.put("pz9zqb",gaid) // GAID
            jsonObj.put("b3dgej","${brand}_${model}") // 手机型号
            jsonObj.put("tsd589",referee) // refer
            jsonObj.put("0ifdut","${locale.language}_${locale.country}") // 语言
            jsonObj.put("y1ywr3","${sdkVersion}") // 系统版本

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