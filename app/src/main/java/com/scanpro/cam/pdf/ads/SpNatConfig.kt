package com.scanpro.cam.pdf.ads

/**
 * 原生广告的关闭键行为配置。照抄 ApolloPDF `ad/NatConfig.java`（已移除「误触」拉收逻辑）。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/NatConfig.java`
 *
 * 远端 JSON（`ac_nat_config`）：`{"switch_open":1}`
 *
 * - [switchOpen] == 1 才显示关闭按钮；否则关闭按钮整体隐藏（用户找不到关闭入口）。
 * - 点击关闭键只隐藏广告，不再替用户点击 CTA。
 */
data class SpNatConfig(
    val switchOpen: Int = 1,
)
