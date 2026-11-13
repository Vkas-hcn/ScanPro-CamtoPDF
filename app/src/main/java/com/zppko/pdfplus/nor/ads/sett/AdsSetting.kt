package com.zppko.pdfplus.nor.ads.sett


data class AdsSetting public constructor(
    val zId: String, // ad_id
    val zFirm: String, // 广告来源 // admob
    val zType: String, // 广告类型 // op , int , nat
    val zTime: Long, // 广告过期时间，admob,op:4小时，设13800秒，int:1小时，设3000秒
    val zWeight: Int, // 广告优先级，从最高的请求
    val zPlace: String // 自定义的广告位置，open_laun,inte_back_result
) {

    override fun equals(other: Any?): Boolean {
        return (other is AdsSetting
                && other.zId == zId
                && other.zType == zType
                && other.zFirm == zFirm && other.zPlace == zPlace)
    }

}