package com.zppko.pdfplus.nor.ads.helper

import com.scanpro.cam.pdf.ads.SpAdPosition

object CodeKeyAndEvent {
    // 迁移适配：位常量换成 B 的 17 个广告位（PDF26 原值 cuu_launch 等仅保留注释对照）
    val zx_PLACE_OPEN = SpAdPosition.AC_LAUNCH
    //    val zx_PLACE_OPEN = "cuu_launch"
    val zx_PLACE_INTER_ClEAN = SpAdPosition.AC_CHECK_INT
    val zx_PLACE_INTER_SCAN = SpAdPosition.AC_FILE_INT
    val zx_PLACE_INTER_DONE = SpAdPosition.AC_MAIN_BACK_INT
    val zx_PLACE_NATIVE_MAIN = SpAdPosition.AC_MAIN_NAT


    val bb_ACTION_LAUNCH_COLD = SpAdPosition.AC_LAUNCH
    val bb_ACTION_NAV_MAIN = SpAdPosition.AC_MAIN_NAT
    val bb_ACTION_INTER_CLEAN = SpAdPosition.AC_CHECK_INT


    val xxdx_TYPE_OPEN = "op"
    val xxdx_TYPE_INTER = "int"
    val xxdx_TYPE_NATIVE = "nat"
    val ddd_FIRM_ADMOB = "admob"
    val ddd_FIRM_TOPON = "topon"
    val ddd_FIRM_TRADPLUS = "tradplus"
}
