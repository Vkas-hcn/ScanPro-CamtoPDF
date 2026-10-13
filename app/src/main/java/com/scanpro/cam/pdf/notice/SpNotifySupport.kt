package com.scanpro.cam.pdf.notice

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.scanpro.cam.pdf.ads.SpAdsHub
import kotlin.random.Random

/**
 * 通知子系统用的三件小事：读配置、查权限/占用、点亮屏幕。
 *
 * 出处（ApolloPDF）：
 * - `remote/RemoteConfig.java` → 这里对应中台下发的 `SpConfigStore`（经 [SpAdsHub] 暴露）
 * - `utils/UtilsKt.java` 的 `isHaveNotifyPermission` / `isNotificationShowing` / `canInteractive` /
 *   `getRandomCode` / `isToday`
 * - `notice/launcher_scene/SendManager.java` 的 `wakeScreen`
 */
internal object SpNotifyEnv {

    private const val TAG = "SpNotifyEnv"

    val newConfig: SpNewConfig
        get() = SpAdsHub.getOrNull()?.newConfigOf() ?: SpNewConfig()

    val acPopConfig: SpAcPopConfig
        get() = SpAdsHub.getOrNull()?.acPopConfigOf() ?: SpAcPopConfig()

    val popRefresh: SpPopRefresh
        get() = SpAdsHub.getOrNull()?.popRefreshOf() ?: SpPopRefresh()

    val mediaConfig: SpMediaConfig
        get() = SpAdsHub.getOrNull()?.mediaConfigOf() ?: SpMediaConfig()

    /**
     * Apollo 每个触发器开头都有 `if (remoteConfig.newConfig == null) return`：
     * 远端配置没回来之前不发通知。这里用 [SpAdsHub.hasRemotePayload] 表达同一件事。
     */
    val hasRemoteConfig: Boolean
        get() = SpAdsHub.getOrNull()?.hasRemotePayload() == true

    fun isForeground(): Boolean = SpAdsHub.getOrNull()?.isForeground() == true
}

/** 通知权限与「这条通知还在不在状态栏」。 */
internal object SpNotifyPermit {

    private const val PREFS = "sp_notify_permit"
    private const val KEY_ALLOW_REPORTED = "allow_reported"

    /**
     * Apollo `UtilsKt.isHaveNotifyPermission`：查 `areNotificationsEnabled()`；首次为真时
     * 补报一次 `system_notification_allow`。
     *
     * 出处偏差：Apollo 只在 13 以下的那个分支里补报（13+ 直接 return，漏了），这里两边都报。
     * 事件名和「只报一次」的口径不变。
     */
    fun isHaveNotifyPermission(context: Context): Boolean {
        val granted = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)
        if (granted) reportAllowOnce(context)
        return granted
    }

    /** 授权那一刻补报一次。 */
    fun reportAllowOnce(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ALLOW_REPORTED, false)) return
        prefs.edit().putBoolean(KEY_ALLOW_REPORTED, true).apply()
        com.scanpro.cam.pdf.analytics.SpTracker.log(
            context,
            com.scanpro.cam.pdf.analytics.SpTracker.SYSTEM_NOTIFICATION_ALLOW,
        )
    }

    /** Apollo `UtilsKt.isNotificationShowing`。 */
    fun isNotificationShowing(context: Context, id: Int): Boolean = runCatching {
        NotificationManagerCompat.from(context).activeNotifications.any { it.id == id }
    }.getOrDefault(false)
}

internal object SpNotifyUtils {

    private const val TAG = "SpNotifyUtils"

    /** Apollo `UtilsKt.getRandomCode()`：`[111111, Int.MAX_VALUE]`。 */
    fun randomCode(): Int = Random.nextInt(111111, Int.MAX_VALUE)

    /** Apollo `UtilsKt.isToday(ms)`：同一个自然日。 */
    fun isToday(ms: Long): Boolean {
        if (ms <= 0L) return false
        val now = java.util.Calendar.getInstance()
        val then = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /** Apollo `UtilsKt.canInteractive` / `isScreenOn`。 */
    fun isInteractive(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isInteractive
    }.getOrDefault(false)

    /**
     * Apollo `SendManager.wakeScreen`：屏幕黑着的时候点一把屏，让通知能被看到。
     * 用 3 秒超时的亮屏 wakelock（`acquire(3000)` 到点自动释放）。
     *
     * Apollo 这一步走反射 + AES 解出来的类名字符串，纯粹是反分析；行为一致，写法不照抄。
     */
    fun wakeScreen(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val lock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "sp_notify:screen",
            )
            lock.acquire(3000L)
        }.onFailure { Log.w(TAG, "wakeScreen failed", it) }
    }

    fun log(message: String) {
        Log.i(TAG, message)
    }
}
