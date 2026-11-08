package com.bockcock.adjust

import android.content.Context
import android.content.SharedPreferences
import com.scanpro.cam.pdf.CamPdfApp


object KvUtil {

    private const val PREFS_NAME = "pdf_ad_save"

    private lateinit var prefs: SharedPreferences


    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(): SharedPreferences {
        if (!::prefs.isInitialized) {
            // 迁移适配：PdfApp.app → CamPdfApp.get()
            prefs = CamPdfApp.get().applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefs
    }

    fun getInt(key: String): Int {
        return getPrefs().getInt(key, 0)
    }

    fun setInt(key: String, data: Int) {
        getPrefs().edit().putInt(key, data).apply()
    }

    fun getLong(key: String): Long {
        return getPrefs().getLong(key, 0L)
    }

    fun setLong(key: String, data: Long) {
        getPrefs().edit().putLong(key, data).apply()
    }


    fun getString(key: String,defValue: String = ""): String {
        return getPrefs().getString(key,defValue)?:""
    }

    fun setString(key: String, data: String) {
        getPrefs().edit().putString(key, data).apply()
    }

    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        val ss = getPrefs().getString(key, "${defaultValue}")
        if (ss==null){
            return 0.0
        }
        return ss.toDouble()
    }
    fun putDouble(key: String, value: Double) {
        prefs.edit().putString(key,"${value}").apply()
    }
}
