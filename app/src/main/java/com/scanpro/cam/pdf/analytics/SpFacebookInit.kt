package com.scanpro.cam.pdf.analytics

import android.app.Application
import android.content.Context
import android.util.Log
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger

/**
 * 用下发配置里的 App Id / Client Token 初始化 Facebook。
 * 缺值、占位符、SDK 抛错都直接跳过，不把进程打崩。
 */
object SpFacebookInit {

    private const val TAG = "SpFacebook"

    @Volatile
    private var started = false

    fun start(context: Context, appId: String, clientToken: String) {
        val id = appId.trim()
        val token = clientToken.trim()
        if (!usable(id) || !usable(token) || started) return
        val app = context.applicationContext
        runCatching {
            FacebookSdk.setApplicationId(id)
            FacebookSdk.setClientToken(token)
            FacebookSdk.sdkInitialize(app)
            FacebookSdk.setAutoLogAppEventsEnabled(true)
            FacebookSdk.fullyInitialize()
            if (app is Application) {
                AppEventsLogger.activateApp(app)
            }
            started = true
            Log.i(TAG, "facebook initialized=${id}")
        }.onFailure {
            Log.w(TAG, "facebook init skipped", it)
        }
    }

    private fun usable(value: String): Boolean =
        value.isNotEmpty() && !value.startsWith("YOUR_", ignoreCase = true)
}
