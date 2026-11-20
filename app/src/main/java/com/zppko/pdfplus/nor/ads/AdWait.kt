package com.zppko.pdfplus.nor.ads

import com.mymy.mymy.ads.AdLog
import com.mymy.mymy.ads.AdsConf
import com.zppko.pdfplus.nor.tool.Tool
import kotlinx.coroutines.delay

/**
 * 广告等待时间唯一出口。
 *
 * 数据来源：`pps_refer_data` 下发的 `op_min_wt` / `op_max_wt` / `int_min_wt` / `int_max_wt`
 * （以及 `op_cmp_wt`）；由 [com.bockcock.adjust.ConfigBean] 解析进 [AdsConf]。
 *
 * 语义：
 * - **min**：页面最短停留。广告就绪后若还没等够 min，就等够再继续，避免页面"闪一下"。
 * - **max**：等广告加载的上限。到时还没就绪就不展示，直接放行后续流程。
 *
 * 所有取值都做异常保护：负数按 0、超过 [MAX_LIMIT_MS] 按上限、`max < min` 时按 `min`。
 * 这样即使运营下发写错，也不会让启动页/插屏被卡死。
 */
object AdWait {

    /** 单个等待时间的安全上限（60s）：下发异常时兜底，避免页面长时间卡住。 */
    const val MAX_LIMIT_MS = 60_000L

    /** 开屏两个候选的比价等待上限（文档口径：填更大按 5000 处理）。 */
    const val MAX_CMP_MS = 5_000L

    /** 开屏最短停留（ms），默认 1000。 */
    fun openMinMs(): Long = sanitize(AdsConf.open_min_wait)

    /** 开屏等广告加载的上限（ms），默认 16000；不会小于 [openMinMs]。 */
    fun openMaxMs(): Long = atLeastMin(openMinMs(), sanitize(AdsConf.open_max_wait))

    /** 插屏最短停留（ms），默认 1000。 */
    fun interMinMs(): Long = sanitize(AdsConf.inter_min_wait)

    /** 插屏等广告加载的上限（ms），默认 16000；不会小于 [interMinMs]。 */
    fun interMaxMs(): Long = atLeastMin(interMinMs(), sanitize(AdsConf.inter_max_wait))

    /** 开屏比价等待（ms）：0=不等第二个候选；上限 [MAX_CMP_MS]。 */
    fun cmpMs(): Long = sanitize(AdsConf.open_cmp_wait).coerceAtMost(MAX_CMP_MS)

    /**
     * 等插屏就绪：最多等 [interMaxMs]，一就绪立刻返回；随后补足 [interMinMs] 的最短停留。
     *
     * 是否真的展示仍由 [ImpressionUtils.show] 决定（可能因无额度/未就绪被拦），
     * 这里的返回值只用于日志排查。
     */
    suspend fun awaitInterstitial(place: String): Boolean {
        val startedAt = System.currentTimeMillis()
        val maxMs = interMaxMs()
        val ready = Tool.waitForCondition<Boolean>(maxMs) { ImpressionUtils.loadSuccess(place) }
        val remainMs = interMinMs() - (System.currentTimeMillis() - startedAt)
        if (remainMs > 0) {
            delay(remainMs)
        }
        AdLog.log(
            "WAIT_INTER",
            place,
            null,
            "ready=$ready min=${interMinMs()} max=$maxMs cost=${System.currentTimeMillis() - startedAt}ms"
        )
        return ready
    }

    private fun sanitize(rawMs: Int): Long = rawMs.toLong().coerceIn(0L, MAX_LIMIT_MS)

    private fun atLeastMin(minMs: Long, maxMs: Long): Long = if (maxMs < minMs) minMs else maxMs
}
