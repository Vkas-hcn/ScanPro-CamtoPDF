package com.scanpro.cam.pdf.ads

/**
 * 单条广告位配置。**照抄 ApolloPDF `ad/AdInfo.java`**，字段名与远端 JSON key 一致。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/AdInfo.java`
 *
 * 远端 JSON：`{"ad_unit_id":"…","ad_paltfrom":"admob","ad_type":"int","ad_timelimit":3000}`
 */
data class SpAdInfo(
    /** `ad_unit_id`。本地默认一律空串，只从我们自己的配置下发取。 */
    val adUnitId: String,
    /** `ad_paltfrom`。Apollo 拼错了（paltfrom），原样保留 key 名。 */
    val adPlatform: String,
    /** `ad_type`：`op` 开屏 / `int` 插屏 / `nat` 原生 / `banner` banner。 */
    val adType: String,
    /** `ad_timelimit`：缓存有效期（秒）。超时视为已失效。 */
    val adTimeLimit: Int,
) {
    val isOpen: Boolean get() = adType == TYPE_OPEN
    val isInterstitial: Boolean get() = adType == TYPE_INTERSTITIAL
    val isNative: Boolean get() = adType == TYPE_NATIVE
    val isBanner: Boolean get() = adType == TYPE_BANNER

    companion object {
        const val TYPE_OPEN = "op"
        const val TYPE_INTERSTITIAL = "int"
        const val TYPE_NATIVE = "nat"
        const val TYPE_BANNER = "banner"
    }
}
