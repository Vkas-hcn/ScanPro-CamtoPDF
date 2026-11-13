package com.bockcock.adjust

import android.content.Context
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.AdjustEvent
import com.adjust.sdk.LogLevel
import com.buncead.common.util.Util
import com.flux.tracksdk.core.TrackSDK

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AdJustHelp {

    val TAG = "AdJustHelper"


    fun initAdjust(context: Context) {

        CoroutineScope(Dispatchers.IO).launch {
            val distinctId: String = TrackSDK.getInstance().effectiveDistinctId
            Util.iii { "$TAG CoroutineScope() distinctId=${distinctId}" }
            val deviceID: String=  distinctId
//            val deviceID: String=  Util.getDeviceID()

            // TODO: xxx adjust token
            val appToken: String = "5v49m4h5uhz4"
            val environment: String = AppConfig.getAdJustEnv()

            // 5.x 新构造（可选第四个参数：是否关闭日志）
            // 是否禁用所有日志记录。设为 true 可禁用日志记录；设为 false 可启用日志记录。
            val config = AdjustConfig(context, appToken, environment, false)
            // 日志级别（调试建议开）
            config.setLogLevel(LogLevel.ERROR)

            // 外部用户ID（替代你之前的 customer_user_id）
//            val deviceID: String = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)

            config.setExternalDeviceId(deviceID)

//        // 开启 cost 数据（5.x 新方法）
//        config.enableCostDataInAttribution()
//        // 可选：后台发送（看你业务）
//        config.enableSendingInBackground()
//        // 归因回调
//        config.setOnAttributionChangedListener { attribution ->
//            attribution?.let {
//                // 这里处理归因数据
//                // it.network / it.campaign / it.adgroup 等
//            }
//        }

            AdJustAttribute.userTypeListener(config)

            // 代替 adJustConfig.setDelayStart(5.5)
            config.enableFirstSessionDelay()
            Adjust.initSdk(config)
            CoroutineScope(Dispatchers.IO).launch {
                delay(5500L)
                Adjust.endFirstSessionDelay();
            }

        }
    }

//    fun common(eventName: String): AdjustEvent {
//        val adjustEvent = AdjustEvent(eventName)
//        adjustEvent.addCallbackParameter("id","fsdfsd")
//        adjustEvent.addCallbackParameter("app_end","page1")
//        return adjustEvent
//    }
//
//    fun event(name: String,key: String,value: String){
//        val adjustEvent = common(name)
//        adjustEvent.addCallbackParameter(key,value)
//        Adjust.trackEvent(adjustEvent)
//    }


    fun event(eventName: String, dataMap: MutableMap<String, String>?=null){
        val map: Map<String, String> = eventNameMap
        val eventKey: String? = map.get(eventName)

        if (eventKey.isNullOrEmpty()){ }else {
            Util.iii { "$TAG event() eventName=${eventName} --> $eventKey , dataMap=${dataMap}" }
            val adjustEvent = AdjustEvent(eventKey)

            if (dataMap.isNullOrEmpty()){ } else {
                for (entry:Map.Entry<String, String> in map) {
                    adjustEvent.addCallbackParameter(entry.key,entry.value)
                }
            }
            Adjust.trackEvent(adjustEvent)

        }
    }


    fun sendRevenue(usd: Double,currency: String){
        Util.iii { "$TAG sendRevenue() usd=$usd, currency=${currency}" }
        val adRevenue = AdjustAdRevenue("admob_sdk")
        adRevenue.setRevenue(usd,currency)
        // 来源,渠道名
        adRevenue.setAdRevenueNetwork("Admob")
//        // 广告展示数量。
//        adRevenue.setAdImpressionsCount(responseInfo.mediationAdapterClassName)
//        // 可选：广告单元 ID（强烈建议加）
//        adRevenue.setAdRevenueUnit(adValue.adUnitId)
        // 可选：placement / 场景 广告位
//        adRevenue.setAdRevenuePlacement("your_placement_name")
        Adjust.trackAdRevenue(adRevenue)
    }




    // TODO: xxx adjust eventName
    val eventNameMap = mapOf<String, String>(
//        Pin.ad_impression to "4mdg5r",
        Pin.ad_impression_revenue to "82ahw3",
        Pin.total_ads_revenue_001 to "hbsfq9",
    )

}