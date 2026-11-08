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
        return if (data == "") "" else data
    }

    fun getReceiveConfig(): UserPop {
        runCatching {
            return UserPopBridge.fromDispatch(dispatchPayload, UserParms.isKo2Samsung())
        }.onFailure { it.printStackTrace() }
        // Fallback to hardcoded defaults matching UserPopBridge
        val gap = if (UserParms.isKo2Samsung()) 20 else 15
        val count = if (UserParms.isKo2Samsung()) 60 else 80
        return UserPop(
            coIsSwitch = true,
            cotTime = gap,
            cotCount = count,
            couTime = 0,
            couCount = count,
            coaTime = gap,
            coaCount = count,
            freeTimeOne = 23,
            freeTimeTwo = 6,
            isVoice = 0,
            rpCount = 0,
            rpVoiceCt = 1,
            delayTm = 0
        )
    }
}
