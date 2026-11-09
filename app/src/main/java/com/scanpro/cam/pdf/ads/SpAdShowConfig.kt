package com.scanpro.cam.pdf.ads

/**
 * 开屏展示时序配置（远端 `ad_show_config`）。缺省值即推荐值，缺项按默认走。
 *
 * - [launchMinMs]：保留字段，**当前不参与开屏等待**（PDF26 无前置停留）。
 * - [launchWaitMs]：等待开屏广告就绪的上限，默认 15s，超时即放行进主页（并报 `ad_show_timeout`）。
 * - [catchupMs]：保留字段，首页补展示已移除。
 * - [preloadOnBackground]：退到后台、已有用户会话时预加载下一次开屏。
 * - [catchupEnabled]：保留字段，首页补展示已移除。
 *
 * [launchWaitMs] 不设上限，远端填多少就按多少执行（负数按 0 处理）。
 */
data class SpAdShowConfig(
    val launchMinMs: Long = 0L,
    val launchWaitMs: Long = 15_000L,
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
