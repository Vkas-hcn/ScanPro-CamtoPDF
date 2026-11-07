package com.zppko.pdfplus.nor.tool

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zppko.pdfplus.nor.Zppkoo

object MKV {
    private const val SP_NAME = "b4s56a6c"

    private fun getSharedPreferences():SharedPreferences =
        Zppkoo.Companion.ii.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun putString(key: String, value: String) {
        getSharedPreferences().edit { putString(key, value) }
    }

    fun getString( key: String, default: String = ""): String {
        return getSharedPreferences().getString(key, default) ?: default
    }

    fun putInt( key: String, value: Int) {
        getSharedPreferences().edit { putInt(key, value) }
    }

    fun getInt( key: String, default: Int = 0): Int {
        return getSharedPreferences().getInt(key, default)
    }

    fun putLong( key: String, value: Long) {
        getSharedPreferences().edit { putLong(key, value) }
    }

    fun getLong( key: String, default: Long = 0L): Long {
        return getSharedPreferences().getLong(key, default)
    }

    fun putBoolean(key: String, value: Boolean) {
        getSharedPreferences().edit { putBoolean(key, value) }
    }

    fun have(key:String):Boolean{
        return getSharedPreferences().contains(key)
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return getSharedPreferences().getBoolean(key, default)
    }

    fun remove(key: String) {
        getSharedPreferences().edit { remove(key) }
    }

    fun clear() {
        getSharedPreferences().edit { clear() }
    }
}
