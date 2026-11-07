package com.zppko.pdfplus.nor.remote

import com.scanpro.cam.pdf.BuildConfig

object DefaultConfigHelper {
    //    op_min_wt 开屏最小等待时间（单位毫秒毫秒）
    //    op_max_wt 开屏最小等待时间（单位毫秒毫秒）
    //    op_day_sh 开屏天显示上限
    //    op_day_cli 开屏天点击上限
    //    op_gap 开屏广告间隔（单位毫秒毫秒）

    //    int_min_wt 插屏最小等待时间（单位毫秒毫秒）
    //    int_max_wt 插屏最小等待时间（单位毫秒毫秒）
    //    int_day_sh 插屏天显示上限
    //    int_day_cli 插屏天显示上限
    //    int_gap 插屏广告间隔（单位毫秒毫秒）

    //    nat_day_sh 原生天显示上限
    //    nat_day_cli 原生天点击上限
    //    nat_gap 原生广告间隔（单位毫秒毫秒）

    // int_op_c: 打开几次pdf 显示一次插屏广告
    // fb_id:fb id
    // fb_tk:fb token
    // bslcl:默认 0， 0开启限制，1关闭限制，开启限制，在vpn网络下“不加载和显示广告”
    // op_just_usr: 是否开启adjust的买量判断，// 0 开启(默认开启)，1关闭
    // 开启 判断refer和adjust共同是否为买量用户，否则只判断 refer 是否为买量用户

    // *** bi下发用
    // volxo: 0非买量用户，1买量用户（默认0）
    // *** firebase下发用
    // ffsws: 为0显示广告。为1，包含字符串情况才显示（0不判断referee直接为买量,1判断referee字符是否为买量）
    // pcjcs: install referee 判断买量用户的 字符
    // 默认referee字符串 utm_source=google-play&utm_medium=organic
    // ["organic"],测可以这样配置，为买量用户
    // ["xxx","bbb"],这个就是自然量用户,
    val dfReferrer="""
        {
          "op_min_wt":200,
          "op_max_wt":15000,
          "op_day_sh":60,
          "op_day_cli":60,
          "op_gap":1000,
          
          "int_min_wt":200,
          "int_max_wt":8000,
          "int_day_sh":60,
          "int_day_cli":60,
          "int_gap":1000,
          
          "nat_day_sh":110,
          "nat_day_cli":60,
          "nat_gap":1000,
          
           "int_op_c": 3,
           "fb_id":"",
           "fb_tk":"",
           "bslcl": 0,
           "op_just_usr": 0,
           "volxo":"0"
        }
    """.trimIndent()

//    ,
//    "ffsws": 1,
//    "pcjcs":[
//    "adjust",
//    "gclid",
//    "gbraid",
//    "dclid",
//    "facebook",
//    "instagram",
//    "meta",
//    "meta_ads",
//    "ig",
//    "fb",
//    "tiktok",
//    "bytedance",
//    "mtg",
//    "mintegral"
//    ]

    fun getDefAds(): String {
        // A AppConfig.isBundle replaced by B build type; A's real TradPlus IDs removed.
        // This legacy shape is not wired into B's AdMob parser/SDK.
        if (!BuildConfig.DEBUG){
            val dfAds="""
{
  "cuu_launch": [
    {
      "boxaxj": "",
      "opxmlf": "tradplus",
      "ujhscs": "op",
      "vksnx": 13800,
      "gkqcx": 3
    }
  ],
  "co_scan_int":[
    {
      "boxaxj": "",
      "opxmlf": "tradplus",
      "ujhscs": "int",
      "vksnx": 3000,
      "gkqcx": 3
    }
  ],
  "co_done_int":[
    {
      "boxaxj": "",
      "opxmlf": "tradplus",
      "ujhscs": "int",
      "vksnx": 3000,
      "gkqcx": 3
    }
  ],
  "co_result_int": [
    {
      "boxaxj": "",
      "opxmlf": "tradplus",
      "ujhscs": "int",
      "vksnx": 3000,
      "gkqcx": 3
    }
  ],
  "co_main_nat":[
    {
      "boxaxj": "",
      "opxmlf": "tradplus",
      "ujhscs": "nat",
      "vksnx": 3000,
      "gkqcx": 3
    }
  ]
}
    """.trimIndent()
            return dfAds
        }else {
            val dfAds="""
{
  "cuu_launch": [
    {
      "boxaxj": "",
      "opxmlf": "tradplus",
      "ujhscs": "op",
      "vksnx": 13800,
      "gkqcx": 3
    }
  ],
  "co_scan_int":[
    {
      "boxaxj": "",
      "opxmlf": "tradplus",
      "ujhscs": "int",
      "vksnx": 3000,
      "gkqcx": 3
    }
  ],
  "co_done_int":[
    {
      "boxaxj": "",
      "opxmlf": "tradplus",
      "ujhscs": "int",
      "vksnx": 3000,
      "gkqcx": 3
    }
  ],
  "co_result_int": [
    {
      "boxaxj": "",
      "opxmlf": "tradplus",
      "ujhscs": "int",
      "vksnx": 3000,
      "gkqcx": 3
    }
  ],
  "co_main_nat":[
    {
      "boxaxj": "",
      "opxmlf": "tradplus",
      "ujhscs": "nat",
      "vksnx": 3000,
      "gkqcx": 3
    }
  ]
}
    """.trimIndent()
            return dfAds
        }
    }

//    no_ntf_begin 禁止通知开始时间
//    no_ntf_end 禁止通知结束时间
//    bbc_open: 1开启通知，0禁止通知
//
//    bbc_tb_gap: 定时类，通知间隔(单位:分钟)
//    bbc_tb_max: 定时类，通知天上限(单位:次数)
//    bbc_pu_gap: 解锁类，通知间隔(单位:分钟)
//    bbc_pu_max: 解锁类，通知天上限(单位:次数)
//    bbc_sa_gap: 闹钟类，通知间隔(单位:分钟)
//    bbc_sa_max: 闹钟类，通知天上限(单位:次数)
//    is_voice: 普通通知是否有声音，0有声音，1没有声音，2，第一次有声音。默认值0
//    rp_count: 通知重复次数 ,0不重复发送（只发默认的那一次），1重复发1次，2重复发2次
//    rp_voice_ct: 通知重复次数的声音次数，0,没有声音，1第一次有声音，2前两次有声音，3前三次有声音...以此内推
//    delay_tm:解锁通知延时 0，解锁后不延时，1解锁后延时1分钟
    fun getPopConfig(): String {
        val popConfig = """
{
  "no_ntf_begin":23,
  "no_ntf_end":6,
  "bbc_open": 1,
  "bbc_tb_gap": 15,
  "bbc_tb_max": 80,
  "bbc_pu_gap": 0,
  "bbc_pu_max":80,
  "bbc_sa_gap": 15,
  "bbc_sa_max":80,
  "is_voice":0,
  "rp_count":0,
  "rp_voice_ct":1,
  "delay_tm":0
}
        """.trimIndent()
        return popConfig
    }

    fun getPopConfigSamsung(): String {
        val popConfigSamsung = """
{
  "no_ntf_begin":23,
  "no_ntf_end":6,
  "bbc_open": 1,
  "bbc_tb_gap": 20,
  "bbc_tb_max": 60,
  "bbc_pu_gap": 0,
  "bbc_pu_max":60,
  "bbc_sa_gap": 20,
  "bbc_sa_max":60,
  "is_voice":0,
  "rp_count":0,
  "rp_voice_ct":1,
  "delay_tm":0
}
    """.trimIndent()
        return popConfigSamsung
    }
}
