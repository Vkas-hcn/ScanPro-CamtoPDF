package com.scanpro.cam.pdf.ads

import com.scanpro.cam.pdf.BuildConfig

/** Local switches for development / QA. */
object SpAdDevFlags {

    /** Kept for callers; debug builds always use test units and release always uses configured units. */
    @Deprecated("Ad safety follows BuildConfig.DEBUG and cannot be overridden")
    var FORCE_USE_TEST_ADS: Boolean? = null

    fun preferTestUnits(): Boolean = BuildConfig.DEBUG
}
