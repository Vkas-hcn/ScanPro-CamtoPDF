package com.scanpro.cam.pdf.guide

import android.content.Context

/**
 * 首次打开标记。对应 Apollo `Constants.IS_FIRST_OPEN` /
 * `SPHelper.getBoolean(..., true)`。
 *
 * 只有 [SpGuideInfoActivity] 走完才写成 false。卸载入口会跳过引导，
 * 但不改这个标记，下次冷启仍进语言页。
 */
object SpFirstLaunch {

    private const val PREFS = "sp_first_launch"
    private const val KEY_IS_FIRST_OPEN = "is_first_open"

    fun isFirstOpen(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_IS_FIRST_OPEN, true)
    }

    fun markOpened(context: Context) {
        prefs(context).edit().putBoolean(KEY_IS_FIRST_OPEN, false).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
