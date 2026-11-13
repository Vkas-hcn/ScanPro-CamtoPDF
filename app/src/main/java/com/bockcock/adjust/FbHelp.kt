package com.bockcock.adjust

import android.app.Application
import com.scanpro.cam.pdf.CamPdfApp
import com.buncead.common.util.Util
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.flux.tracksdk.core.TrackSDK
import com.zppko.pdfplus.nor.remote.RemoteConfigHelper
import org.json.JSONObject
import java.util.Currency

object FbHelp  {


    fun facebook_revenue(value: Double,currency: String){
        val context = TrackSDK.getContext()
        if (context != null) {
            try {
                AppEventsLogger.newLogger(context).logPurchase(value.toBigDecimal(), Currency.getInstance(currency))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }




    fun startFB(){
        runCatching {
            val data = RemoteConfigHelper.getRecentData(RemoteConfigHelper.KEY_REFERRER_CONFIG)

            val jsonObject = JSONObject(data)
            val fbID = jsonObject.getString("fb_id")
            val fbToken = jsonObject.getString("fb_tk")

            ConfigBean.parse2(jsonObject) // 顺便吧 vpn限制的加载了
            Util.iii { "FbHelp startFB() fbID:${fbID}, fbToken:${fbToken}" }

            begin(fbID,fbToken)
        }.onFailure { it.printStackTrace() }
    }


    // 初始化Facebook
   private fun begin(fbId: String, fbToken: String) {
        runCatching {
            if (fbId.isBlank()){
                return
            }
            if (fbToken.isBlank()){
                return
            }
            if (FacebookSdk.isInitialized()){
                return
            }
            val ctx: Application = CamPdfApp.app

//        // 1. 允许自动初始化标志（如果之前manifest禁用了，这里可以动态开启）
//        FacebookSdk.setAutoInitEnabled(true);
//
//        // 2. 开启 App 事件自动上报（根据需要）
//        FacebookSdk.setAutoLogAppEventsEnabled(true);
//
//        // 3. 执行完全初始化
//        FacebookSdk.fullyInitialize();

            FacebookSdk.setApplicationId(fbId)
            FacebookSdk.setClientToken(fbToken)

            FacebookSdk.sdkInitialize(ctx)
            FacebookSdk.fullyInitialize()
            AppEventsLogger.activateApp(ctx)
        }.onFailure { it.printStackTrace() }
    }
}