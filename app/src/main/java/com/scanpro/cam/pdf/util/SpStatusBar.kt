package com.scanpro.cam.pdf.util

import android.app.Activity
import androidx.core.view.WindowCompat

object SpStatusBar {

    /**
     * @param lightIcons true 时状态栏图标为深色（适合浅色背景）
     */
    fun setLightStatusBar(activity: Activity, lightIcons: Boolean) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = lightIcons
    }
}
