package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.scanpro.cam.pdf.boot.SpBootActivity
import com.scanpro.cam.pdf.util.AppLog

/**
 * 热启动（后台 → 前台）拉起启动页看开屏。
 *
 * 需求：只要 App 从后台回到前台，就要重新走一遍启动页、看开屏广告。
 *
 * 实现要点：
 * - **不清任务栈**：用 `FLAG_ACTIVITY_NEW_TASK` 把 [SpBootActivity] 叠到当前任务栈顶，
 *   启动页自己不跳首页、直接 `finish()`，用户回到原来的页面（见 [SpBootActivity] 的 `isHotStart` 分叉）。
 * - **触发点**在 [SpAdSession] 的进程级生命周期回调里（前台计数 0→1），不另注册一套回调。
 * - **阈值 0**：每次回前台都算（[MIN_BACKGROUND_MS]）。
 *
 * 防叠层是这里最要紧的事：启动页 `finish()` 后原页面会重新 `onStart`，前台计数同样 0→1，
 * 不再拦一次就会无限叠下去。三道闸：[splashCount]、[COOLDOWN_MS]、[pending]。
 */
internal object SpHotStartGate {

    /** 后台停留多久才算一次热启动。0 = 每次回前台都算。 */
    private const val MIN_BACKGROUND_MS = 0L

    /** 启动页关掉后多久内不再触发（冷启进首页同样受这条保护）。 */
    private const val COOLDOWN_MS = 2_000L

    /** 主动跳外部 UI（分享 / 商店 / 系统设置）之后，这次返回不弹。 */
    private const val SUPPRESS_MS = 30_000L

    /**
     * 点过广告之后多久内不弹。
     *
     * 这条不是体验取舍，是 GMA 政策要求：不能在用户刚点击一条广告、
     * 从浏览器/商店返回时立刻再盖一条 App Open。
     */
    private const val AD_CLICK_QUIET_MS = 60_000L

    /** 回到前台后延后一帧再拉启动页，避开 Activity starting 阶段。 */
    private const val START_DELAY_MS = 120L

    /** 这几类页面不打断（挽留流程 / 首次引导 / 已经在启动页）。 */
    private val BLOCKED_SURFACES = listOf(
        "com.scanpro.cam.pdf.boot.SpBootActivity",
        "com.scanpro.cam.pdf.leave.SpLeaveEntryActivity",
        "com.scanpro.cam.pdf.leave.SpUnInstall",
        "com.scanpro.cam.pdf.guide.SpGuide",
    )

    private val handler = Handler(Looper.getMainLooper())

    /** 全部 Activity 停掉的时间，用来算「后台停留了多久」。 */
    @Volatile
    private var backgroundAt = 0L

    /** 主动跳外部 UI 的抑制截止时间。 */
    @Volatile
    private var suppressUntil = 0L

    /** 启动页是否在栈上（可能有不止一层：连点图标会叠两份，计数比布尔安全）。 */
    @Volatile
    private var splashCount = 0

    /** 上一次启动页全部关掉的时间。 */
    @Volatile
    private var splashClosedAt = 0L

    /** 已经排了一次拉起，等 handler 到点。 */
    @Volatile
    private var pending = false

    // region 由 SpAdSession 的进程级生命周期回调驱动

    fun onAppBackgrounded() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    /**
     * @param activity 刚启动的那个 Activity。**必须用它来 `startActivity`**：
     *   Application context 只能带 `FLAG_ACTIVITY_NEW_TASK`，而 NEW_TASK 指向的又正好是
     *   本任务的根 Activity（`SpBootActivity`），系统会把它当成「把任务切到前台」处理，
     *   启动页根本不会压上来（Android 15 实测）。从 Activity 拉起才会真的叠到栈顶。
     */
    fun onAppForegrounded(activity: Activity) {
        if (!shouldLaunch()) return
        pending = true
        handler.postDelayed({ launchNow(activity) }, START_DELAY_MS)
    }
    // endregion

    // region 由 SpBootActivity 驱动

    fun onSplashCreated() {
        splashCount++
        pending = false
    }

    fun onSplashDestroyed() {
        splashCount = (splashCount - 1).coerceAtLeast(0)
        if (splashCount == 0) splashClosedAt = SystemClock.elapsedRealtime()
    }
    // endregion

    /**
     * 主动跳外部 UI 之前调用，标识「这次回前台是用户自己走开的」，不弹开屏。
     *
     * 只在 [MIN_BACKGROUND_MS] = 0（每次回前台都算热启动）时才需要，否则这类往返
     * 本来就会被后台时长阈值挡掉。
     */
    fun suppressNext() {
        suppressUntil = SystemClock.elapsedRealtime() + SUPPRESS_MS
    }

    private fun shouldLaunch(): Boolean {
        if (pending || splashCount > 0) return false
        // handler 到点前又切回后台了（比如用户按 Home 后马上又走了），别在后台拉 Activity
        if (!SpAdSession.isForeground) return false
        val now = SystemClock.elapsedRealtime()
        if (now < suppressUntil) return false
        if (splashClosedAt != 0L && now - splashClosedAt < COOLDOWN_MS) return false
        if (now - backgroundAt < MIN_BACKGROUND_MS) return false
        if (SpAdShowGate.isShowing()) return false
        if (adClickedRecently()) return false
        if (isProtectedSurface()) return false
        return true
    }

    private fun launchNow(activity: Activity) {
        pending = false
        // handler 到点期间可能已经被别的路径拉起来了，再判一次
        if (!shouldLaunch()) {
            AppLog.ads.i("热启动跳过 原因=条件已不满足")
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            AppLog.ads.i("热启动跳过 原因=宿主页面已销毁")
            return
        }
        val intent = Intent(activity, SpBootActivity::class.java).apply {
            putExtra(SpBootActivity.EXTRA_HOT_START, true)
        }
        AppLog.ads.i("热启动拉起启动页")
        runCatching { activity.startActivity(intent) }
            .onFailure { AppLog.ads.w("热启动拉起启动页失败", it) }
    }

    private fun adClickedRecently(): Boolean {
        val clickedAt = SpAdSession.lastAdClickAtMs
        if (clickedAt <= 0L) return false
        return System.currentTimeMillis() - clickedAt < AD_CLICK_QUIET_MS
    }

    /**
     * 当前栈顶是不是「不该再叠一层启动页」的页面（启动页本身 / 挽留 / 首次引导）。
     *
     * [SpBootActivity] 在「不是 task root」时也用它：那种情况多半是桌面图标把启动页压进了
     * 现有任务栈，正常就地转成热启动；但如果底下本来就是一整层流程（比如冷启的开屏还没走完），
     * 就直接退出，别叠第二层。
     */
    fun isProtectedSurface(): Boolean {
        val resumed = SpAdSession.resumedActivityClass ?: return false
        return BLOCKED_SURFACES.any { resumed.startsWith(it) }
    }
}
