package com.scanpro.cam.pdf.ads

/**
 * 开屏展示时序配置（远端 `ad_show_config`）。缺省值即推荐值，缺项按默认走。
 *
 * - [launchMinMs]：开屏页最短停留，命中广告也至少显示这么久。
 * - [launchWaitMs]：等待开屏广告就绪的上限，超时即放行进主页（并报 `ad_show_timeout`）。
 * - [catchupMs]：进主页后继续等广告的窗口，窗口内到货且仍在首页才补展示。
 * - [preloadOnBackground]：退到后台时预加载下一次开屏。
 * - [catchupEnabled]：补展示总开关，出问题可远端一键关掉。
 *
 * [launchWaitMs] 与 [catchupMs] **不设上限**，远端填多少就按多少执行（负数按 0 处理）。
 * 开屏页的兜底总时长会跟着 `launchMinMs + launchWaitMs` 自动放大，不会被本地 10 秒常量截断。
 */
data class SpAdShowConfig(
    val launchMinMs: Long = 1_500L,
    val launchWaitMs: Long = 8_000L,
    val catchupMs: Long = 20_000L,
    val preloadOnBackground: Boolean = true,
    val catchupEnabled: Boolean = true,
) {
    companion object {
        /** 最短停留仍限制上限：它只影响开屏页视觉停留，配太大没意义。 */
        const val MAX_LAUNCH_MIN_MS = 3_000L
    }

    fun sanitized(): SpAdShowConfig = copy(
        launchMinMs = launchMinMs.coerceIn(0L, MAX_LAUNCH_MIN_MS),
        // 不设上限，只挡负数。
        launchWaitMs = launchWaitMs.coerceAtLeast(0L),
        catchupMs = catchupMs.coerceAtLeast(0L),
    )
}
