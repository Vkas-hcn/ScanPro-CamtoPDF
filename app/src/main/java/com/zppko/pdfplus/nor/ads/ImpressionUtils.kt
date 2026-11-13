package com.zppko.pdfplus.nor.ads


import android.util.Log
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mymy.mymy.ads.poi.ADBlockReason
import com.mymy.mymy.ads.poi.TrackAd
import com.mymy.mymy.ads.poi.TrackBean
import com.buncead.common.ui.BaseActivity
import com.tp.compareprice.ComparePriceUtil.recursiveComparePrice

import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent
import com.zppko.pdfplus.nor.ads.mob.IntAdsLoad
import com.zppko.pdfplus.nor.ads.mob.NatAdsLoad
import com.zppko.pdfplus.nor.ads.mob.OpenAdsLoad
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad
import com.zppko.pdfplus.nor.remote.DefaultConfigHelper
import com.zppko.pdfplus.nor.remote.RemoteConfigHelper

import org.json.JSONObject

object ImpressionUtils {

    private val myPoolzzdd = mutableMapOf<String, BaseAdsLoad?>()
    private fun convertBaseAds(setting: AdsSetting): BaseAdsLoad? {
        return when (setting.zType) {
            CodeKeyAndEvent.xxdx_TYPE_OPEN -> OpenAdsLoad(setting)
            CodeKeyAndEvent.xxdx_TYPE_INTER -> IntAdsLoad(setting)
            CodeKeyAndEvent.xxdx_TYPE_NATIVE -> NatAdsLoad(setting)
            else -> null
        }
    }

    private fun loadAdsSettingList(data: String): MutableList<AdsSetting> {
        val gameTags = arrayOf(
            CodeKeyAndEvent.zx_PLACE_OPEN,
            CodeKeyAndEvent.zx_PLACE_INTER_ClEAN,
            CodeKeyAndEvent.zx_PLACE_INTER_SCAN,
            CodeKeyAndEvent.zx_PLACE_INTER_DONE,
            CodeKeyAndEvent.zx_PLACE_NATIVE_MAIN,
//            CodeKeyAndEvent.zx_PLACE_NATIVE_SCAN,
//            CodeKeyAndEvent.zx_PLACE_NATIVE_RESULT
        )
        val gameList = mutableListOf<AdsSetting>()
        runCatching {
            val jsonObject = JSONObject(data)
            gameTags.forEach {
                val jsonArray = jsonObject.getJSONArray(it)
                if (jsonArray.length() > 0) {
                    for (index in (0 until jsonArray.length())) {
                        val gameJson = jsonArray.getJSONObject(index)
                        val adsSetting = AdsSetting(
                            gameJson.getString("boxaxj"),
                            gameJson.getString("opxmlf"),
                            gameJson.getString("ujhscs"),
                            gameJson.getLong("vksnx"),
                            gameJson.optInt("gkqcx", 0),
                            it
                        )
                        gameList.add(adsSetting)
                    }
                }
            }
        }
        return gameList
    }


    fun loadSuccess(place: String): Boolean {
        return (myPoolzzdd[place] != null && myPoolzzdd[place]?.zIsShowAds() == true)
    }

    fun getEcpm(place: String): Double {
        val unit = myPoolzzdd[place]
        val bool = unit != null && unit.zIsShowAds() == true
        if (bool){
           return parseEcpm(unit.getEcpm())
        }
        return 0.0
    }

    /**
     * TradPlus 比价 SDK 价格：取某位就绪广告对应 placement 的真实 eCPM（USD，两位小数）。
     * 底层走 com.tp.compareprice.ComparePriceUtil.recursiveComparePrice(placementId)；
     * 广告未就绪 / placement 为空 / 探测失败时返回 0.0（调用方应回退到 [getEcpm]）。
     */
    fun getComparePrice(place: String): Double {
        val unit = myPoolzzdd[place] ?: return 0.0
        if (!unit.zIsShowAds()) return 0.0
        val placementId = unit.setting.zId
        if (placementId.isEmpty()) return 0.0
        return try {
            recursiveComparePrice(placementId)
        } catch (t: Throwable) {
            0.0
        }
    }

    fun show(act: BaseActivity?, place: String, point:String, container: FrameLayout, whenOnVisible:Boolean=true, finish: () -> Unit) {
        if (act == null) {
            finish.invoke()
            return
        }
        val unit = myPoolzzdd[place]
        if (loadSuccess(place)) {
//            myPoolzzdd[place] = null
//            load(place)
            unit?.zShoweee(act, point,container,whenOnVisible, finish)
        } else {
            unit?.setting?.let { setting ->
                TrackAd.blocked(TrackBean.getTradBean(setting.zType,setting.zPlace, CodeKeyAndEvent.ddd_FIRM_TRADPLUS,setting.zId),ADBlockReason.disabled)
            }

            if (whenOnVisible){
                val observer = object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        act.lifecycle.removeObserver(this)
                        finish.invoke()
                    }
                }
                act.lifecycle.addObserver(observer)
            }else{
                finish.invoke()
            }
        }
    }

    fun getNewLoadConfig(place: String, weight: Int): AdsSetting? {
        val data = RemoteConfigHelper.getRecentData(RemoteConfigHelper.KEY_ADS_CONFIG)
        var list = loadAdsSettingList(data)
        if (list.isEmpty()) {
            list = loadAdsSettingList(DefaultConfigHelper.getDefAds())
        }
        return list.filter { it.zPlace == place && it.zWeight < weight }.maxByOrNull { it.zWeight }
    }


    fun fireAnative(place: String): NatAdsLoad? {
        if (myPoolzzdd[place] != null && myPoolzzdd[place]?.zIsShowAds() == true) {
            val unit = myPoolzzdd[place]
            return if (unit is NatAdsLoad){
                unit
            }
            else
                null
        }
        return null
    }


    fun load(place: String) {
        val unit = myPoolzzdd[place]

        if (unit != null ) {
//        if (unit != null && !unit.canReloadAds()) {
            if (unit.zIsShowAds()){
                Log.e("hhhh广告", "${place}: 有加载好的，不需要加载")
            }else{
                Log.e("hhhh广告", "${place}: 没有广告，需要加载")
                unit.zLoadeee()
            }
            return
        }

        val data = RemoteConfigHelper.getRecentData(RemoteConfigHelper.KEY_ADS_CONFIG)
        var list = loadAdsSettingList(data)
        if (list.isEmpty()) {
            list = loadAdsSettingList(DefaultConfigHelper.getDefAds())
        }
        Log.e("hhhh广告", "获取最新广告配置")
        list.forEach {
            Log.e("hhhh广告", "配置：${it}")
        }

//        val config = list.sortedByDescending { it.zWeight }.find { it.zPlace == place }
        val config = list.sortedByDescending { it.zWeight }.filter { !it.zId.isNullOrEmpty() }.find { it.zPlace == place }
        if (config != null) {
            myPoolzzdd[place] = convertBaseAds(config).apply { this?.zLoadeee() }
        } else {
            Log.e("hhhh广告", "找不到该广告位配置：${place}")
        }
    }



    fun parseEcpm(rawStr: String?): Double {
        if (rawStr.isNullOrBlank()) return 0.0

        // 只保留数字 (0-9) 和小数点 (.)
        val cleanedStr = rawStr.replace(Regex("[^0-9.]"), "")

        return cleanedStr.toDoubleOrNull() ?: 0.0
    }

}


/*
fun parseEcpm(rawStr: String?): Double {
    if (rawStr.isNullOrBlank()) return 0.0

    // 只保留数字 (0-9) 和小数点 (.)
    val cleanedStr = rawStr.replace(Regex("[^0-9.]"), "")

    return cleanedStr.toDoubleOrNull() ?: 0.0
}

fun main() {
    val s1 = "¥15.50"
    val s2 = "$2.10"
    val s3 = "1,234.50元"

    println(parseEcpm(s1)) // 输出: 15.5
    println(parseEcpm(s2)) // 输出: 2.1
    println(parseEcpm(s3)) // 输出: 1234.5
}
 */