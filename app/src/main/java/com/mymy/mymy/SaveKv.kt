package com.mymy.mymy

import android.content.Context
import android.content.SharedPreferences
import com.scanpro.cam.pdf.CamPdfApp
import kotlin.text.toDouble

object SaveKv  {

    fun getApp(): Context {
        return CamPdfApp.app
    }

    @Volatile
    private var mPrefs: SharedPreferences? =null
    fun getPrefs(): SharedPreferences {
        // 第一次检查（不加锁，提升性能）
        var prefs = mPrefs
        if (prefs==null){
            synchronized(this) {
                // 第二次检查（加锁，确保线程安全）
                prefs = mPrefs
                if (prefs == null) {
                    prefs = getApp().getSharedPreferences("app_blocker_prefs", Context.MODE_PRIVATE)
                    mPrefs = prefs
                    return prefs
                } else {
                    return prefs
                }
            }
        }else {
            return prefs
        }
    }

    fun getString(key: String,defValue: String = ""): String {
        return getPrefs().getString(key,defValue)?:""
    }
    fun setString(key: String, data: String) {
        getPrefs().edit().putString(key, data).apply()
    }
    fun getInt(key: String, defaultValue: Int = 0): Int {
        return   getPrefs().getInt(key, defaultValue)
    }
    fun setInt(key: String, value: Int) {
        getPrefs().edit().putInt(key, value).apply()
    }

    fun getLong(key: String): Long {
        return getPrefs().getLong(key, 0L)
    }
    fun setLong(key: String, data: Long) {
        getPrefs().edit().putLong(key, data).apply()
    }

    fun getBoolean(key: String): Boolean {
        return getPrefs().getBoolean(key, false)
    }
    fun setBoolean(key: String, data: Boolean) {
        getPrefs().edit().putBoolean(key, data).apply()
    }

    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        val data =   getPrefs().getString(key, "${defaultValue}")
        if (data==null||data.length == 0){
            return 0.0
        }
        return data.toDouble()
    }
    fun setDouble(key: String, value: Double) {
        getPrefs().edit().putString(key,"${value}").apply()
    }

    val KEY_IS_ALLOW_FLOAT = "nofd_gudi"
    fun isAlrEnterFloatPage(): Boolean {
        return getPrefs().getBoolean(KEY_IS_ALLOW_FLOAT, false)
    }
    fun setAlrEnterFloatPage(){
        getPrefs().edit().putBoolean(KEY_IS_ALLOW_FLOAT,true).apply()
    }




    val KEY_IS_M_USER = "bkdf_fjds"
    fun isAlrMUser(): Boolean {
        return getPrefs().getBoolean(KEY_IS_M_USER, false)
    }
    fun setAlrMUser(){
        getPrefs().edit().putBoolean(KEY_IS_M_USER,true).apply()
    }


}