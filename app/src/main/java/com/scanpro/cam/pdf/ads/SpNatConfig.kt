package com.scanpro.cam.pdf.ads

/**
 * 原生广告的关闭键行为配置。照抄 ApolloPDF `ad/NatConfig.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/NatConfig.java`
 *
 * 远端 JSON（`ac_nat_config`）：`{"switch_open":1,"jump_percent":50}`
 *
 * - [switchOpen] == 1 才显示关闭按钮；否则关闭按钮整体隐藏（用户找不到关闭入口）。
 * - 点关闭时掷 [1,100]：点数 < [jumpPercent] 就替用户点一次广告 CTA（误触拉收），
 *   否则才真的把广告视图隐藏。
 */
data class SpNatConfig(
    val switchOpen: Int = 1,
    val jumpPercent: Int = 50,
)
