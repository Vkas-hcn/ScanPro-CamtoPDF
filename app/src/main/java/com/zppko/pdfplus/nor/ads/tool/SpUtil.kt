package com.zppko.pdfplus.nor.ads.tool

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object SpUtil {

    private const val PREF_NAME = "hfg6s6fd5cvcvv"
    private lateinit var sharedPreferences: SharedPreferences

    // 初始化 SharedPreferences
    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // 保存数据
    fun put(key: String, value: Any) {
        sharedPreferences.edit{
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                else -> throw IllegalArgumentException("This type can't be saved into SharedPreferences")
            }
        }
    }

    // 获取数据
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, defaultValue: T): T {
        return when (defaultValue) {
            is String -> sharedPreferences.getString(key, defaultValue) as T
            is Int -> sharedPreferences.getInt(key, defaultValue) as T
            is Boolean -> sharedPreferences.getBoolean(key, defaultValue) as T
            is Long -> sharedPreferences.getLong(key, defaultValue) as T
            is Float -> sharedPreferences.getFloat(key, defaultValue) as T
            else -> throw IllegalArgumentException("This type can't be retrieved from SharedPreferences")
        }
    }

    fun putStringList(key: String,data:List<String>){
        sharedPreferences.edit {
            putStringSet(key,data.toSet())
        }
    }

    fun getStringList(key: String):List<String>{
        return sharedPreferences.getStringSet(key, null)?.toList()?: emptyList()
    }

    // 删除某个数据
    fun remove(key: String) {
        with(sharedPreferences.edit()) {
            remove(key)
            apply()
        }
    }

    // 清除所有数据
    fun clear() {
        with(sharedPreferences.edit()) {
            clear()
            apply()
        }
    }

    // 判断是否存在某个 key
    fun contains(key: String): Boolean {
        return sharedPreferences.contains(key)
    }
}
