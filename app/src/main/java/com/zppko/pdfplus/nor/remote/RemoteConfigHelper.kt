package com.zppko.pdfplus.nor.remote

import com.bockcock.adjust.Pin
import com.buncead.common.util.Util
import com.mymy.mymy.rece.HomeRecentsHelp
import com.scanpro.cam.pdf.config.SpConfigRemoteSource
import com.scanpro.cam.pdf.config.SpConfigRepository
import com.scanpro.cam.pdf.config.SpConfigStore
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.ads.tool.SpUtil
import com.zppko.pdfplus.nor.ads.tool.UserParms
import com.zppko.pdfplus.nor.horus.HintExt05
import com.zppko.pdfplus.nor.horus.UserPop
import com.zppko.pdfplus.nor.horus.UserPopBridge
import org.json.JSONObject

object RemoteConfigHelper {
    val TAG = "Firebase_RemoteConfigHelper"

    val KEY_ADS_CONFIG = "pps_ad_data"
    val KEY_REFERRER_CONFIG = "pps_refer_data"
    val KEY_POP_CONFIG_All = "comm_pop_data"
    val KEY_POP_CONFIG_SAMSUNG = "sams_pop_data"

    @Volatile private var dispatchPayload: JSONObject? = null
    private val repository by lazy {
        val context = Zppkoo.ii
        SpConfigRepository(context, SpConfigRemoteSource(context))
    }

    fun startServer() {
        // A ConfigBean.parse1/Firebase fetch replaced by B's existing cache reader only.
        // loadCache() is public; constructing the source does not issue a request.
        runCatching {
            dispatchPayload = repository.loadCache()?.payload
        }.onFailure { it.printStackTrace() }
        runCatching {
            ConfigHelper.isEmulatorBySystem()
        }.onFailure { it.printStackTrace() }
    }

    fun currentPayload(): JSONObject? = dispatchPayload

    /** Call AFTER B SpAdsHub.applyPayload has applied blocking/meta, for cache AND remote. */
    fun onConfigReady(payload: JSONObject) {
        // Retain the raw B protocol; don't write renamed A data over B's repository cache.
        dispatchPayload = JSONObject(payload.toString())
        onEligibilityChanged()
    }

    /** Shared with a real Adjust callback; no SDK init, no A BI/Firebase requests. */
    fun onEligibilityChanged() {
        runCatching { HintExt05.markLing() }.onFailure { it.printStackTrace() }
        runCatching {
            HomeRecentsHelp().registerHomeRecentsReceiver(Zppkoo.ii)
        }.onFailure { it.printStackTrace() }
        runCatching { Pin.anaMUSer2() }.onFailure { it.printStackTrace() }
    }

    fun getRecentData(key: String): String {
        if (key == KEY_ADS_CONFIG) {
            // B keeps its AdMob data/IDs. Never return A's TradPlus account defaults.
            return dispatchPayload?.opt("ac_ad_config")?.toString()
                ?: DefaultConfigHelper.getDefAds()
        }
        if (key == KEY_REFERRER_CONFIG) {
            val json = runCatching { JSONObject(SpUtil.get(key, "")) }.getOrNull()
                ?: JSONObject(DefaultConfigHelper.dfReferrer)
            // Only bridge the documented B acquired-user field. Do not infer Adjust state.
            json.put("volxo", if (SpConfigStore().dispatchMeta(dispatchPayload).isAcquiredUser) 1 else 0)
            return json.toString()
        }
        val data = SpUtil.get(key, "")
        if (data == "") {
            return when (key) {
//                KEY_POP_CONFIG_All -> DefaultConfigHelper.getPopConfig()
//                KEY_POP_CONFIG_SAMSUNG -> DefaultConfigHelper.getPopConfigSamsung()
                else -> ""
            }
        } else {
            return data
        }
    }

    fun getReceiveConfig(): UserPop {
        // UserPopBridge preserves B's fields/defaults and reads only the four missing
        // sound/repeat/delay fields from a matching original A block when present.
        runCatching {
            return UserPopBridge.fromDispatch(dispatchPayload, UserParms.isKo2Samsung())
        }.onFailure { it.printStackTrace() }
        val json = if (UserParms.isKo2Samsung()) DefaultConfigHelper.getPopConfigSamsung() else DefaultConfigHelper.getPopConfig()
        Util.iii { "$TAG getReceiveConfig()222 default data=${json}" }
        val config = fksld22(json)
        return config
    }

    // Original A parser retained for A-format local/default data, not B's raw dispatch.
    fun fksld22(json: String): UserPop {
        val jsonObj=JSONObject(json)
        val kai=jsonObj.getInt("bbc_open")==1
        val clock_time=jsonObj.getInt("bbc_tb_gap")
        val clock_num=jsonObj.getInt("bbc_tb_max")
        val lk_time=jsonObj.getInt("bbc_pu_gap")
        val lk_num=jsonObj.getInt("bbc_pu_max")
        val al_time=jsonObj.getInt("bbc_sa_gap")
        val al_num=jsonObj.getInt("bbc_sa_max")
        val start_time=jsonObj.getInt("no_ntf_begin")
        val stop_time=jsonObj.getInt("no_ntf_end")
        val is_voice=jsonObj.getInt("is_voice")
        val rp_count=jsonObj.getInt("rp_count")
        val rp_voice_ct=jsonObj.getInt("rp_voice_ct")
        val delay_tm=jsonObj.getInt("delay_tm")
        return UserPop(kai, clock_time, clock_num, lk_time, lk_num, al_time, al_num, start_time, stop_time,is_voice,rp_count,rp_voice_ct,delay_tm)
    }
}
