package com.zppko.pdfplus.nor.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.scanpro.cam.pdf.ads.SpAdDevFlags
import com.scanpro.cam.pdf.ads.SpAdPosition
import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.zppko.pdfplus.nor.ads.tool.BaseAdsLoad
import com.zppko.pdfplus.nor.ads.mob.OpenAdsLoad
import com.zppko.pdfplus.nor.ads.mob.IntAdsLoad
import com.zppko.pdfplus.nor.ads.mob.NatAdsLoad
import com.zppko.pdfplus.nor.ads.tool.OmUtil
import com.zppko.pdfplus.nor.ads.tool.UserParms
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

    // 迁移适配：解析 B 协议 ac_ad_config（ad_unit_id/ad_paltfrom/ad_type/ad_timelimit），
    // 位表=B 的 17 位；weight 按数组顺序合成（保证 PDF26 失败降级链可用）。
    private fun loadAdsSettingList(data: String): MutableList<AdsSetting> {
        val gameTags = SpAdPosition.ALL
        val gameList = mutableListOf<AdsSetting>()
        runCatching {
            val jsonObject = JSONObject(data)
            gameTags.forEach {
                val jsonArray = jsonObject.optJSONArray(it) ?: return@forEach
                if (jsonArray.length() > 0) {
                    for (index in (0 until jsonArray.length())) {
                        val gameJson = jsonArray.optJSONObject(index) ?: continue
                        var unitId = gameJson.optString("ad_unit_id", "")
                        val zType = gameJson.optString("ad_type", "")
                        if (unitId.isEmpty() || zType.isEmpty()) continue
                        if (SpAdDevFlags.preferTestUnits()) {
                            unitId = when (zType) {
                                "op" -> "ca-app-pub-3940256099942544/9257395921"
                                "int" -> "ca-app-pub-3940256099942544/1033173712"
                                "nat" -> "ca-app-pub-3940256099942544/2247696110"
                                "banner" -> "ca-app-pub-3940256099942544/6300978111"
                                else -> ""
                            }
                        }
                        val adsSetting = AdsSetting(
                            unitId,
                            gameJson.optString("ad_paltfrom", "admob"),
                            zType,
                            gameJson.optLong("ad_timelimit", 600L).let { if (it <= 0) 600L else it },
                            jsonArray.length() - index,
                            it
                        )
                        gameList.add(adsSetting)
                    }
                }
            }
        }
        return gameList
    }

    fun init(context: Context, finish: (() ->Unit)? = null) {
        if (UserParms.isOm()) {
            Log.e("spLog", "欧盟用户UMP")
            OmUtil.start(context as Activity) {
                finish?.invoke()
            }
        } else {
            Log.e("spLog", "不是欧盟用户跳过UMP")
            finish?.invoke()
        }
    }


    fun loadSuccess(place: String): Boolean {
        return (myPoolzzdd[place] != null && myPoolzzdd[place]?.zIsShowAds() == true)
    }

    fun show(act: Activity?, place: String, point:String, whenOnVisible:Boolean=true, finish: () -> Unit) {
        if (act == null) {
            finish.invoke()
            return
        }
        val unit = myPoolzzdd[place]
        if (loadSuccess(place)) {
            myPoolzzdd[place] = null
            load(place)
            unit?.zShoweee(act, point,whenOnVisible, finish)
        } else {
            // 迁移适配：android.app.Activity 无 lifecycle 属性，宿主为 LifecycleOwner 时才走
            // onResume 延迟回调；否则立即 finish。
            val host = act as? LifecycleOwner
            if (whenOnVisible && host != null){
                val observer = object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        host.lifecycle.removeObserver(this)
                        finish.invoke()
                    }
                }
                host.lifecycle.addObserver(observer)
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

    // 回补（SmartPdfScan 原方法）：PDF26 无原生，B 原生渲染层靠它取已就绪的 NatAdsLoad。
    fun fireAnative(place: String): NatAdsLoad? {
        if (myPoolzzdd[place] != null && myPoolzzdd[place]?.zIsShowAds() == true) {
            val unit = myPoolzzdd[place]
            return if (unit is NatAdsLoad) unit else null
        }
        return null
    }


    fun load(place: String) {
        val unit = myPoolzzdd[place]

        if (unit != null && !unit.canReloadAds()) {
            Log.e("hhhh广告", "${place}: 有加载好的，不需要加载")
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

        val config = list.sortedByDescending { it.zWeight }.find { it.zPlace == place }
        if (config != null) {
            myPoolzzdd[place] = convertBaseAds(config).apply { this?.zLoadeee() }
        } else {
            Log.e("hhhh广告", "找不到该广告位配置：${place}")
        }
    }





}
