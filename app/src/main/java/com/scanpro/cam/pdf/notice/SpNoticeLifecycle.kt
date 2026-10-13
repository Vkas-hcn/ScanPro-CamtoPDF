package com.scanpro.cam.pdf.notice

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.scanpro.cam.pdf.ads.SpAdSession

/**
 * 应用级生命周期 → 两个通知场景。照抄 ApolloPDF `ApoApplication.registerActivityLifecycleCallbacks`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/app/ApoApplication.java`
 * 及 `ApoApplication$registerActivityLifecycleCallbacks$1$onActivityStopped$1.java`、
 * `$onActivityDestroyed$1$1.java`
 *
 * Apollo 在 Application 里自己数了两件事，这里原样保留：
 *
 * - **Activity 集合**（`LinkedHashSet<Activity>`）：`onActivityDestroyed` 移掉自己，**空了**就发
 *   `no_page_exist`（用户把页面都退了，等于退到桌面）。
 * - **前台 Activity 计数**：`onActivityStopped` 减到 `<= 0` 时，如果这期间点过广告，
 *   发一次 `ad_click`（Apollo 用 `ApoApplication.isClickAd` 这个一次性布尔；我们用
 *   [SpAdSession.consumeAdClick] 的一次性语义，看完就清零，不会重复发）。
 *
 * Apollo 还有两件事这里**不做**（与通知无关，属于它自己的启动判据）：`toReStartApp` 触发的
 * 「重新拉起 SplashActivity」、以及全部页面销毁时置 `isColdStart`。
 */
internal object SpNoticeLifecycle {

    private const val TAG = "SpNoticeLifecycle"

    /** Apollo `ApoApplication.activities`。 */
    private val activities = LinkedHashSet<Activity>()

    /** Apollo `ApoApplication.mActivityCount`。 */
    private var startedCount = 0

    private var registered = false

    /** 进程级注册，只调一次。在 [com.scanpro.cam.pdf.CamPdfApp] 里调。 */
    fun register(app: Application) {
        if (registered) return
        registered = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                synchronized(activities) { activities.add(activity) }
            }

            override fun onActivityDestroyed(activity: Activity) {
                val empty = synchronized(activities) {
                    activities.remove(activity)
                    activities.isEmpty()
                }
                if (!empty) return
                Log.i(TAG, "no page left, send no_page_exist")
                runCatching { SpTriggerManager.sendNoPageNotification(activity.applicationContext) }
                    .onFailure { Log.w(TAG, "sendNoPageNotification failed", it) }
            }

            override fun onActivityStarted(activity: Activity) {
                startedCount++
            }

            override fun onActivityStopped(activity: Activity) {
                startedCount--
                if (startedCount > 0) return
                // 回到后台：点过广告才发，且只发一次（consume 会清零）
                if (!SpAdSession.consumeAdClick()) return
                Log.i(TAG, "just clicked an ad, send ad_click")
                runCatching { SpTriggerManager.sendAdClickNotification(activity.applicationContext) }
                    .onFailure { Log.w(TAG, "sendAdClickNotification failed", it) }
            }

            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }
}
