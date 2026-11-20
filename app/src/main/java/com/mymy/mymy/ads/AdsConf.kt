package com.mymy.mymy.ads

object AdsConf {
    var open_min_wait = 200
    var open_max_wait = 15000
    // day_* 仅作为广告条目未配置 day_sh/day_cli 时的内置兜底值；
    // 类型级上限不再从 pps_refer_data 读取，老字段只为兼容老版本客户端保留。
    var open_day_show = 50
    var open_day_click = 50
    var open_gap = 1000

    var inter_min_wait = 200
    var inter_max_wait = 8000
    var inter_day_show = 50
    var inter_day_click = 50
    var inter_gap = 1000

    var native_day_show = 100
    var native_day_click = 50
    var native_gap = 1000
}