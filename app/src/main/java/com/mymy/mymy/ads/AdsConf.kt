package com.mymy.mymy.ads

object AdsConf {
    // 等待时间（ms）：pps_refer_data 的 op_min_wt / op_max_wt / int_min_wt / int_max_wt 解析到这里，
    // 统一由 AdWait（com.zppko.pdfplus.nor.ads.AdWait）读取，不要直接读这两个字段。
    // min = 页面最短停留，max = 等广告加载的上限；默认 1s / 16s。
    var open_min_wait = 1000
    var open_max_wait = 16000
    // day_* 仅作为广告条目未配置 day_sh/day_cli 时的内置兜底值；
    // 类型级上限不再从 pps_refer_data 读取，老字段只为兼容老版本客户端保留。
    var open_day_show = 50
    var open_day_click = 50
    var open_gap = 1000
    // 开屏两个候选的比价等待：第一个就绪后等第二个多久（ms），0=不等直接展示第一个；上限 5000
    var open_cmp_wait = 1500

    var inter_min_wait = 1000
    var inter_max_wait = 16000
    var inter_day_show = 50
    var inter_day_click = 50
    var inter_gap = 1000

    var native_day_show = 100
    var native_day_click = 50
    var native_gap = 1000
}