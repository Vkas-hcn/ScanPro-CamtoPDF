package com.scanpro.cam.pdf.notice

/**
 * 本工程通知 ID 的唯一来源。
 *
 * 常驻（4856）与媒体（8562）的常量随通知策略层一起回滚，仍就地写在 HintExt01 / HintMt 里。
 */
object SpNotifyIds {

    /** 系统通知槽位 A（HintSup / FCM data-only）。 */
    const val SYS = 70101

    /** 系统通知槽位 B。与 [SYS] 轮流使用，保证普通通知最多同时显示 2 条。 */
    const val SYS_2 = 70102

    private var sysSlot = 0

    /**
     * 取下一个系统通知槽位：70101 → 70102 → 70101 …
     *
     * 语义：第 1 条占 A，第 2 条占 B（两条并存），第 3 条回到 A 把**较旧的那条顶掉**，
     * 因此通知栏里普通通知永远不超过 2 条，且新通知替换的是最老的一条。
     *
     * 注意：调用方必须**一次事件只取一次**，然后在刷新循环里复用同一个 id ——
     * 这样刷新是原地更新同一条，不会两条来回跳。
     */
    @Synchronized
    fun nextSys(): Int {
        val id = if (sysSlot == 0) SYS else SYS_2
        sysSlot = 1 - sysSlot
        return id
    }
}
