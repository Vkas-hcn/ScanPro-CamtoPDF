package com.zppko.pdfplus.nor.horus

// cotTime 定时
// couTime 解锁
// coaTime 闹钟
data class UserPop public constructor(
    val coIsSwitch: Boolean,
    val cotTime: Int,
    val cotCount: Int,
    val couTime: Int,
    val couCount: Int,
    val coaTime: Int,
    val coaCount: Int,
    val freeTimeOne: Int,
    val freeTimeTwo: Int,
    val isVoice:Int,
    val rpCount:Int,
    val rpVoiceCt:Int,
    val delayTm:Int
)
