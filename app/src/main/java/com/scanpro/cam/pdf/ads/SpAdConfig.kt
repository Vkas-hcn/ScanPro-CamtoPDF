package com.scanpro.cam.pdf.ads

/**
 * 广告配置：**位置 → 单元列表**。照抄 ApolloPDF `ad/AdConfig.java`（它是 17 个具名字段，
 * 这里用 Map 收敛，语义等价：每个 `ac_*` 位置一条 fallback 链）。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/AdConfig.java`、
 * `ad/AdConfigKt.java` 的 `getAdInfoList(position)`
 */
data class SpAdConfig(
    val byPosition: Map<String, List<SpAdInfo>> = emptyMap(),
) {
    /** 等价 Apollo `AdConfigKt.getAdInfoList(position)`：空/未知位置返回 null。 */
    fun adInfoList(position: String): List<SpAdInfo>? =
        byPosition[position]?.takeIf { it.isNotEmpty() }

    /**
     * 等价 Apollo `AdCache.getAdListByAdPosition(position)`：只认 17 个已知位置，
     * 位置的值取列表首条的 [SpAdInfo.adTimeLimit] 当 TTL。
     */
    fun ttlSecondsOf(position: String): Int? =
        adInfoList(position)?.firstOrNull()?.adTimeLimit

    /** 某位置的单元 ID 序列（按优先级）。 */
    fun unitIds(position: String): List<String> =
        adInfoList(position)?.map { it.adUnitId }?.filter { it.isNotBlank() } ?: emptyList()
}
