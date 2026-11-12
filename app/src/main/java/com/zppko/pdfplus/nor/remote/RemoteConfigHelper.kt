package com.zppko.pdfplus.nor.remote

import com.mymy.mymy.rece.HomeRecentsHelp
import com.scanpro.cam.pdf.config.SpConfigRemoteSource
import com.scanpro.cam.pdf.config.SpConfigRepository
import com.scanpro.cam.pdf.config.SpConfigStore
import com.scanpro.cam.pdf.config.SpDefaultConfig
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

    /** 远端/缓存未到达时的本地 ac_ad_config 兜底，只解析一次。 */
    private val localAdsConfig: String by lazy {
        runCatching {
            SpDefaultConfig.load(Zppkoo.ii)?.opt("ac_ad_config")?.toString().orEmpty()
        }.getOrDefault("")
    }

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
    }

    fun getRecentData(key: String): String {
        if (key == KEY_ADS_CONFIG) {
            val applied = dispatchPayload?.opt("ac_ad_config")?.toString().orEmpty()
            // 远端/缓存已应用就用它；否则立即用本地 default_config.json 兜底，
            // 让广告在接口返回前就能发起；接口成功后再切到最新远端配置。
            return applied.ifEmpty { localAdsConfig }
        }
        if (key == KEY_REFERRER_CONFIG) {
            val raw = dispatchPayload?.opt(key) ?: SpUtil.get(key, "")
            val json = when (raw) {
                is JSONObject -> JSONObject(raw.toString())
                is String -> runCatching { JSONObject(raw) }.getOrNull()
                else -> null
            } ?: JSONObject()
            // Only bridge the documented B acquired-user field. Do not infer Adjust state.
            json.put("volxo", if (SpConfigStore().dispatchMeta(dispatchPayload).isAcquiredUser) 1 else 0)
            return json.toString()
        }
        val data = SpUtil.get(key, "")
        return if (data == "") "" else data
    }

    fun getReceiveConfig(): UserPop =
        UserPopBridge.fromDispatch(dispatchPayload, UserParms.isKo2Samsung())
}
