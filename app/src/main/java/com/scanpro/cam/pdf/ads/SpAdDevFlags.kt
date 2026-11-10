package com.scanpro.cam.pdf.ads

/** Local switches for development / QA. */
object SpAdDevFlags {

    /** Kept for callers; debug builds always use test units and release always uses configured units. */
    @Deprecated("Ad safety follows BuildConfig.DEBUG and cannot be overridden")
    var FORCE_USE_TEST_ADS: Boolean? = null

    /** TradPlus 测试位走 TradPlus 后台测试模式，不再用 AdMob 测试单元覆盖，故恒返回 false。 */
    fun preferTestUnits(): Boolean = false
}
