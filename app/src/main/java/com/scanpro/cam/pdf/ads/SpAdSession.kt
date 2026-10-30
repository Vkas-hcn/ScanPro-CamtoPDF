package com.scanpro.cam.pdf.ads

import android.app.Application
import android.os.Bundle
import java.util.UUID

/**
 * 会话 ID 与全局应用态。Apollo 对应 `UtilsKt.getSessionId()` / `ApoApplication.isForeground` /
 * `isClickAd`。
 */
object SpAdSession {

    /** 每次预加载尝试一个新 sessionId，用来做重试计数与失败清理（Apollo `UtilsKt.getSessionId()`）。 */
    fun newId(): String = UUID.randomUUID().toString()

    /** 最近一次广告点击时间。Apollo 用 `ApoApplication.setClickAd(true)` 标记。 */
    @Volatile
    var lastAdClickAtMs: Long = 0L
        private set

    fun markAdClicked() {
        lastAdClickAtMs = System.currentTimeMillis()
    }

    /** 广告点击的一次性标记：取走即清零。Apollo `ApoApplication.isClickAd`（读一次就 `setClickAd(false)`）。 */
    fun consumeAdClick(): Boolean {
        val clicked = lastAdClickAtMs > 0L
        lastAdClickAtMs = 0L
        return clicked
    }

    /** 应用是否在前台。Apollo `ApoApplication.isForeground()`。 */
    @Volatile
    var isForeground: Boolean = false
        private set

    /** 最近一次 resume 的页面类名，给 [SpHotStartGate] 判断「当前是不是不该打断的页面」。 */
    @Volatile
    var resumedActivityClass: String? = null
        private set

    private val startedActivities = java.util.concurrent.atomic.AtomicInteger(0)

    /** 进程级生命周期注册，只调一次。 */
    fun attachLifecycle(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                val wasForeground = isForeground
                isForeground = startedActivities.incrementAndGet() > 0
                // 后台 → 前台：热启动在这里起跳（是否真的要弹由 SpHotStartGate 判）
                if (!wasForeground && isForeground) SpHotStartGate.onAppForegrounded(activity)
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                val wasForeground = isForeground
                isForeground = startedActivities.decrementAndGet() > 0
                if (wasForeground && !isForeground) {
                    SpHotStartGate.onAppBackgrounded()
                    // 退后台预加载下一次开屏：同进程再回来直接命中。
                    // 这里已经有页面/用户会话，属于“有展示计划”的后台加载；
                    // Application 启动阶段和配置下发阶段不会触发。
                    preloadNextLaunchAd()
                }
            }

            override fun onActivityCreated(a: android.app.Activity, b: Bundle?) = Unit

            override fun onActivityResumed(a: android.app.Activity) {
                resumedActivityClass = a.javaClass.name
            }

            override fun onActivityPaused(a: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: android.app.Activity) = Unit
        })
    }

    private fun preloadNextLaunchAd() {
        val hub = SpAdsHub.getOrNull() ?: return
        if (!hub.showConfig().preloadOnBackground) return
        runCatching { hub.preloadLaunchAd("background") }
    }
}
