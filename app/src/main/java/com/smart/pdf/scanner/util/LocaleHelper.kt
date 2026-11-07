package com.smart.pdf.scanner.util

import com.scanpro.cam.pdf.CamPdfApp
import com.scanpro.cam.pdf.util.SpLocale

object LocaleHelper {
    fun getLocalString(id: Int): String = SpLocale.wrap(CamPdfApp.get()).getString(id)
}
