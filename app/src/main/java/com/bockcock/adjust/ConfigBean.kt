package com.bockcock.adjust



import com.buncead.common.util.Util
import com.mymy.mymy.ads.AdsConf
import com.mymy.mymy.rece.HomeRecentsHelp
import com.scanpro.cam.pdf.CamPdfApp
import com.zppko.pdfplus.nor.horus.HintExt05

import com.zppko.pdfplus.nor.remote.RemoteConfigHelper
import org.json.JSONObject

object ConfigBean {

    var op_vpn_limit = 0
    var op_just_usr = 0
    var volxo = 0

    var inter_op_count_interval = 3




//    fun myText(): String {
//        return "ConfigBean -> op_vpn_limit=$op_vpn_limit, op_just_usr=$op_just_usr, inter_op_count_interval=$inter_op_count_interval"
//    }



    fun parse1(){

        runCatching {
            val data = RemoteConfigHelper.getRecentData(RemoteConfigHelper.KEY_REFERRER_CONFIG)
            val jsonObject = JSONObject(data)

            AdsConf.open_min_wait = jsonObject.getInt("op_min_wt")
            AdsConf.open_max_wait = jsonObject.getInt("op_max_wt")
            AdsConf.open_day_show = jsonObject.getInt("op_day_sh")
            AdsConf.open_day_click = jsonObject.getInt("op_day_cli")
            AdsConf.open_gap = jsonObject.getInt("op_gap")

            AdsConf.inter_min_wait = jsonObject.getInt("int_min_wt")
            AdsConf.inter_max_wait = jsonObject.getInt("int_max_wt")
            AdsConf.inter_day_show = jsonObject.getInt("int_day_sh")
            AdsConf.inter_day_click = jsonObject.getInt("int_day_cli")
            AdsConf.inter_gap = jsonObject.getInt("int_gap")

            AdsConf.native_day_show = jsonObject.getInt("nat_day_sh")
            AdsConf.native_day_click = jsonObject.getInt("nat_day_cli")
            AdsConf.native_gap = jsonObject.getInt("nat_gap")

            ConfigBean.op_vpn_limit = jsonObject.optInt("bslcl",0)
            ConfigBean.op_just_usr = jsonObject.optInt("op_just_usr",0)
            ConfigBean.volxo = jsonObject.optInt("volxo",0)
            ConfigBean.inter_op_count_interval = jsonObject.optInt("int_op_c",3)

            Util.iii { "NetUtil update()111 op_vpn_limit=${ConfigBean.op_vpn_limit}" }

        }.onFailure { it.printStackTrace() }

    }

    fun parse2(jsonObject: JSONObject){
        runCatching {
            AdsConf.open_min_wait = jsonObject.getInt("op_min_wt")
            AdsConf.open_max_wait = jsonObject.getInt("op_max_wt")
            AdsConf.open_day_show = jsonObject.getInt("op_day_sh")
            AdsConf.open_day_click = jsonObject.getInt("op_day_cli")
            AdsConf.open_gap = jsonObject.getInt("op_gap")

            AdsConf.inter_min_wait = jsonObject.getInt("int_min_wt")
            AdsConf.inter_max_wait = jsonObject.getInt("int_max_wt")
            AdsConf.inter_day_show = jsonObject.getInt("int_day_sh")
            AdsConf.inter_day_click = jsonObject.getInt("int_day_cli")
            AdsConf.inter_gap = jsonObject.getInt("int_gap")

            AdsConf.native_day_show = jsonObject.getInt("nat_day_sh")
            AdsConf.native_day_click = jsonObject.getInt("nat_day_cli")
            AdsConf.native_gap = jsonObject.getInt("nat_gap")

            ConfigBean.op_vpn_limit = jsonObject.optInt("bslcl",0)
            ConfigBean.op_just_usr = jsonObject.optInt("op_just_usr",0)
            ConfigBean.volxo = jsonObject.optInt("volxo",0)
            ConfigBean.inter_op_count_interval = jsonObject.optInt("int_op_c",3)

            Util.iii { "NetUtil update()222 op_vpn_limit=${ConfigBean.op_vpn_limit}" }
        }.onFailure { it.printStackTrace() }

    }



    fun refer2adjust_isMUser(){
        HintExt05.markLing()

        HomeRecentsHelp().registerHomeRecentsReceiver(CamPdfApp.app)

        Pin.anaMUSer2()
    }
}