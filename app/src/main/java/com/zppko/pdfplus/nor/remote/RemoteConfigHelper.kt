package com.zppko.pdfplus.nor.remote

import com.bockcock.adjust.ConfigBean
import com.bockcock.adjust.FbHelp
import com.scanpro.cam.pdf.CamPdfApp
import com.buncead.common.util.Util
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.mymy.mymy.rece.HomeRecentsHelp
import com.zppko.pdfplus.nor.ads.tool.SpUtil
import com.zppko.pdfplus.nor.ads.tool.UserParms
import com.zppko.pdfplus.nor.horus.HintExt05
import com.zppko.pdfplus.nor.horus.UserPop
import org.json.JSONObject

object RemoteConfigHelper {
    val TAG = "Firebase_RemoteConfigHelper"

    val KEY_ADS_CONFIG = "pps_ad_data"
    val KEY_REFERRER_CONFIG = "pps_refer_data"
    val KEY_POP_CONFIG_All = "comm_pop_data"
    val KEY_POP_CONFIG_SAMSUNG = "sams_pop_data"
////    val KEY_TAICHI_CONFIG="toppercent"
////    val KEY_MEDIA_REPEATE="mediapop_refresh_off"
//    private val keyList = arrayOf(
//        KEY_ADS_CONFIG,
//        KEY_REFERRER_CONFIG,
//        KEY_POP_CONFIG_All,
//        KEY_POP_CONFIG_SAMSUNG,
////        KEY_TAICHI_CONFIG,
////        KEY_MEDIA_REPEATE
//    )


    fun startServer() {
        ConfigBean.parse1()
        runCatching {
//            val configSettings = remoteConfigSettings {
//                minimumFetchIntervalInSeconds = 3600
//            }
//            val remoteConfig = Firebase.remoteConfig
//            remoteConfig.setConfigSettingsAsync(configSettings)
//
//            remoteConfig.fetchAndActivate().addOnCompleteListener {
//                keyList.forEach { key ->
//                    Util.iii { "$TAG remoteConfig init: "+remoteConfig.getString(key)+":::"+remoteConfig.getLong(key) }
////                    val data = remoteConfig.getString(key)
////                    SpUtil.put(key, data)
//                }
//
//                FbHelp.startFB() //   包含 解析 vpn限制 开关 NetUtil.fklasd()
//
//                HintExt05.markLing()
//
//                HomeRecentsHelp().registerHomeRecentsReceiver(CamPdfApp.app)
//
//            }
            ConfigHelper.isEmulatorBySystem()
        }.onFailure { it.printStackTrace() }
    }

    fun getRecentData(key: String): String {
        val data = SpUtil.get(key,"")
        if (data == "") {
            return when (key) {
                RemoteConfigHelper.KEY_ADS_CONFIG -> DefaultConfigHelper.getDefAds()
                RemoteConfigHelper.KEY_REFERRER_CONFIG -> DefaultConfigHelper.dfReferrer
//                KEY_POP_CONFIG_All -> DefaultConfigHelper.getPopConfig()
//                KEY_POP_CONFIG_SAMSUNG -> DefaultConfigHelper.getPopConfigSamsung()
//                KEY_TAICHI_CONFIG -> DefaultConfigHelper.tcDf
                else -> ""
            }
        } else {
            return data
        }
    }

//    fun zdgetTaiChi():List<Pair<String,Double>>{
//        val config= zdconverTaichi(getRecentData(KEY_TAICHI_CONFIG))
//        return config ?: zdconverTaichi(DefaultConfigHelper.tcDf)!!
//    }

    fun getReceiveConfig(): UserPop {
        runCatching {
            val key = if (UserParms.isKo2Samsung()) RemoteConfigHelper.KEY_POP_CONFIG_SAMSUNG else RemoteConfigHelper.KEY_POP_CONFIG_All
            val localSave= SpUtil.get(key,"")
            Util.iii { "$TAG getReceiveConfig()111 remote data=${localSave}" }
            return fksld22(localSave)
        }
        val json = if (UserParms.isKo2Samsung()) DefaultConfigHelper.getPopConfigSamsung() else DefaultConfigHelper.getPopConfig()
        Util.iii { "$TAG getReceiveConfig()222 default data=${json}" }
        val config = fksld22(json)
        return config
    }

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