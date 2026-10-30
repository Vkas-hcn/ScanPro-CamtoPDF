package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.os.SystemClock
import com.scanpro.cam.pdf.analytics.SpSceneCode
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.delay

/**
 * 开屏补展示。
 *
 * 真实分布：`ac_launch` 填充耗时 p50 约 3.7 秒、p90 二十秒以上，而开屏页不可能一直等。
 * 所以超时放行后不立刻放弃，而是在首页继续等一小段时间（默认 8 秒）：
 * 广告到货时用户如果还在首页、没有切 tab、应用在前台，就补一次展示。
 *
 * 展示时机受远端 `ad_show_config.catchup_enabled` / `catchup_ms` 控制，出问题可一键关。
 */
object SpLaunchAdCatchup {

    private const val POLL_MS = 100L

    @Volatile
    private var deadlineElapsedMs = 0L

    @Volatile
    private var consumed = true

    /** 开屏超时放行时调用，开一个补展示窗口。 */
    fun arm(windowMs: Long) {
        if (windowMs <= 0L) {
            cancel()
            return
        }
        deadlineElapsedMs = SystemClock.elapsedRealtime() + windowMs
        consumed = false
        AppLog.ads.i("开屏补展示窗口已开启 windowMs=$windowMs")
    }

    /** 用户已经切走 / 已展示过 / 主动关闭时调用。 */
    fun cancel() {
        deadlineElapsedMs = 0L
        consumed = true
    }

    fun isArmed(): Boolean = !consumed && deadlineElapsedMs > SystemClock.elapsedRealtime()

    /**
     * 在首页等广告到货并补展示。调用方应放在首页的 lifecycleScope 里，页面销毁即取消。
     *
     * @param canShow 当前是否还满足补展示条件（例如仍停留首页、页面未销毁）
     * @return true 表示已经发起补展示
     */
    suspend fun awaitAndShow(
        activity: Activity,
        canShow: () -> Boolean,
        onDone: () -> Unit = {},
    ): Boolean {
        if (consumed) return false
        val hub = SpAdsHub.getOrNull() ?: run {
            cancel()
            return false
        }
        var waitedMs = 0L
        while (isArmed()) {
            if (activity.isFinishing || activity.isDestroyed || !canShow()) {
                AppLog.ads.i("开屏补展示取消 waitedMs=$waitedMs")
                cancel()
                return false
            }
            if (hub.hasOpenAd()) {
                consumed = true
                deadlineElapsedMs = 0L
                AppLog.ads.i("开屏补展示命中 waitedMs=$waitedMs")
                hub.presentLaunchAd(activity, SpSceneCode.AC_LAUNCH_OPEN, onDone)
                return true
            }
            delay(POLL_MS)
            waitedMs += POLL_MS
        }
        AppLog.ads.i("开屏补展示窗口结束未命中 waitedMs=$waitedMs")
        return false
    }
}
