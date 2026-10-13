package com.scanpro.cam.pdf.ads

import com.scanpro.cam.pdf.BuildConfig

/** Local switches for development / QA. */
object SpAdDevFlags {

    /** Set to true/false to force; set to null to follow [BuildConfig.USE_TEST_ADS]. */
    var FORCE_USE_TEST_ADS: Boolean? = null

    fun preferTestUnits(): Boolean = FORCE_USE_TEST_ADS ?: BuildConfig.USE_TEST_ADS
}
