package com.zppko.pdfplus.nor.ads


import android.util.Log
import android.widget.FrameLayout
import com.mymy.mymy.ads.AdLog
import com.mymy.mymy.ads.count.AdCountInter
import com.mymy.mymy.ads.count.AdCountNati
import com.mymy.mymy.ads.count.AdCountOpen
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
import com.zppko.pdfplus.nor.ads.tool.RunOnResumed
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
                            it,
                            gameJson.optInt("day_sh", -1),
                            gameJson.optInt("day_cli", -1)
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

    fun show(
        act: BaseActivity?,
        place: String,
        point: String,
        container: FrameLayout,
        whenOnVisible: Boolean = true,
        onShown: (() -> Unit)? = null,
        finish: () -> Unit
    ) {
        if (act == null) {
            AdLog.log("SHOW_BLOCKED", place, myPoolzzdd[place]?.setting?.zId, "reason=activity_null point=$point")
            finish.invoke()
            return
        }
        val unit = myPoolzzdd[place]
        val capOk = capAvailable(unit?.setting)
        if (loadSuccess(place) && capOk) {
//            myPoolzzdd[place] = null
//            load(place)
            unit?.zOnShown = onShown
            unit?.zShoweee(act, point,container,whenOnVisible, finish)
        } else if (!capOk) {
            // 已达该广告位当天上限：直接放行流程，不再等待下一次 onResume
            unit?.zOnShown = null
            AdLog.log("SHOW_BLOCKED", place, unit?.setting?.zId, "reason=daily_cap point=$point")
            unit?.setting?.let { setting ->
                TrackAd.blocked(
                    TrackBean.getTradBean(setting.zType,setting.zPlace, CodeKeyAndEvent.ddd_FIRM_TRADPLUS,setting.zId),
                    ADBlockReason.daily_cap
                )
            }
            RunOnResumed.run(act, false) { finish.invoke() }
        } else {
            unit?.zOnShown = null
            AdLog.log("SHOW_BLOCKED", place, unit?.setting?.zId, "reason=ad_not_ready point=$point")
            unit?.setting?.let { setting ->
                TrackAd.blocked(TrackBean.getTradBean(setting.zType,setting.zPlace, CodeKeyAndEvent.ddd_FIRM_TRADPLUS,setting.zId),ADBlockReason.disabled)
            }

            // 已 RESUMED 立即执行，否则等下一次 RESUMED；避免 observer 永远等不到
            RunOnResumed.run(act, whenOnVisible) { finish.invoke() }
        }
    }

    /** 该位置当前/将展示广告的 per-ID 上限是否还有额度（供开屏 eCPM 选位、show 兜底使用）。 */
    fun canShowAd(place: String): Boolean = capAvailable(currentSetting(place))

    private fun capAvailable(setting: AdsSetting?): Boolean {
        if (setting == null || setting.zId.isEmpty()) return true
        return when (setting.zType) {
            CodeKeyAndEvent.xxdx_TYPE_OPEN -> AdCountOpen.open_idCountCheck(setting)
            CodeKeyAndEvent.xxdx_TYPE_INTER -> AdCountInter.inter_idCountCheck(setting)
            CodeKeyAndEvent.xxdx_TYPE_NATIVE -> AdCountNati.nati_idCountCheck(setting)
            else -> true
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

    /** 读取最新广告位配置；解析失败/为空时回退本地默认配置。 */
    private fun adList(): MutableList<AdsSetting> {
        val data = RemoteConfigHelper.getRecentData(RemoteConfigHelper.KEY_ADS_CONFIG)
        var list = loadAdsSettingList(data)
        if (list.isEmpty()) {
            list = loadAdsSettingList(DefaultConfigHelper.getDefAds())
        }
        return list
    }

    /** 与 [load] 一致的选位规则：同位置取 gkqcx 最大且 boxaxj 非空的条目。 */
    fun pickConfig(place: String): AdsSetting? {
        return adList()
            .sortedByDescending { it.zWeight }
            .filter { it.zId.isNotEmpty() }
            .find { it.zPlace == place }
    }

    /** 已加载对象优先，未加载时取将要加载的对象；频控上限判定使用它的 zId 与 day_sh/day_cli。 */
    fun currentSetting(place: String): AdsSetting? {
        return myPoolzzdd[place]?.setting ?: pickConfig(place)
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
        // 每日上限已达：该广告位当天不再加载（需求：各自上限达到就不加载、不展示）。
        // 展示侧 [show] 还会用 capAvailable 再兜底一次，这里只是不再发无意义的请求。
        val current = currentSetting(place)
        if (!capAvailable(current)) {
            AdLog.log(
                "LOAD_SKIP_CAP",
                place,
                current?.zId,
                "reason=daily_cap caps=${current?.zDayShow}/${current?.zDayClick}"
            )
            return
        }

        val unit = myPoolzzdd[place]

        if (unit != null ) {
//        if (unit != null && !unit.canReloadAds()) {
            if (unit.zIsShowAds()){
                Log.e("hhhh广告", "${place}: 有加载好的，不需要加载")
                AdLog.log("LOAD_SKIP_READY", place, unit.setting.zId, "type=${unit.setting.zType}")
            }else{
                Log.e("hhhh广告", "${place}: 没有广告，需要加载")
                AdLog.log("LOAD_RELOAD", place, unit.setting.zId, "type=${unit.setting.zType}")
                unit.zLoadeee()
            }
            return
        }

        val config = pickConfig(place)
        Log.e("hhhh广告", "获取最新广告配置，选中：${config}")
        if (config != null) {
            AdLog.log(
                "LOAD_PICK",
                place,
                config.zId,
                "type=${config.zType} weight=${config.zWeight} caps=${config.zDayShow}/${config.zDayClick}"
            )
            myPoolzzdd[place] = convertBaseAds(config).apply { this?.zLoadeee() }
        } else {
            Log.e("hhhh广告", "找不到该广告位配置：${place}")
            AdLog.log("LOAD_NO_ID", place, null, "no non-empty boxaxj")
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