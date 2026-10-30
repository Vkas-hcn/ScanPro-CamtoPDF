package com.scanpro.cam.pdf.ads

import android.os.Handler
import android.os.Looper

/**
 * 全屏广告展示互斥。旧实现把 `isAdTryShowing` 放在 `SpFullScreenAdManager` 的实例字段上，
 * 而每次 `SpAdsHub.fullScreen()` 都会 new 一个 manager，等于完全没有互斥能力。
 *
 * 这里改成进程级闸门，并带 15 秒看门狗：SDK 如果某个回调不来，闸门不会永久卡死。
 */
object SpAdShowGate {

    /** 兜底释放时间。正常路径在关闭/失败回调里释放，这里只防「回调不来」。 */
    private const val WATCHDOG_MS = 60_000L

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var inFlight = false

    private val reset = Runnable {
        synchronized(this) { inFlight = false }
    }

    /** 拿到展示权返回 true；已有全屏广告在展示就返回 false。 */
    @Synchronized
    fun tryEnter(): Boolean {
        if (inFlight) return false
        inFlight = true
        handler.removeCallbacks(reset)
        handler.postDelayed(reset, WATCHDOG_MS)
        return true
    }

    /** 展示结束（关闭/失败/放弃）后释放。 */
    @Synchronized
    fun exit() {
        inFlight = false
        handler.removeCallbacks(reset)
    }

    fun isShowing(): Boolean = inFlight
}
