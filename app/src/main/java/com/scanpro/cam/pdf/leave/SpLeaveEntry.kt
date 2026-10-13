package com.scanpro.cam.pdf.leave

import android.content.Intent

/**
 * 卸载挽留入口判定。链路：桌面「卸载」快捷方式 → [SpLeaveEntryActivity] 中转 →
 * 开屏 [com.scanpro.cam.pdf.boot.SpBootActivity] → 主界面 → [SpUnInstall01Activity] → [SpUnInstall02Activity]。
 *
 * | 环节 | 判据 | 出处 |
 * |---|---|---|
 * | 桌面快捷方式 | `Intent.action == [ACTION_DELETE_ENTRY]`（经中转页补 `NEW_TASK｜CLEAR_TASK`） | ApolloPDF `ui/delete/DeleteActivity` + `res/xml/shortcuts.xml` |
 * | 开屏 → 主界面 | 开屏判完再挂 `sp_open_leave=true`（[markOpenLeave]） | 本工程拆两层，所以多传一次 |
 *
 * 站内要用这套流程不必再走开屏：直接 `SpUnInstall01Activity.start(context)`（主界面就是这么跳的）。
 *
 * [ENABLED] 是代码侧的开关（Black 2026-10-10 定：打开）。要关掉整条流程，把它置 `false`，
 * 并把 debug、release 两份 `res/xml/app_shortcuts.xml` 里的 `delete_action` 删掉——桌面那条快捷方式
 * 由 XML 决定，Android 不支持用一个开关同时管两头。
 */
object SpLeaveEntry {

    /** 卸载挽留流程总开关。置 `false` 前先把两份 `app_shortcuts.xml` 里的 `delete_action` 删掉。 */
    const val ENABLED = true

    /** Apollo `res/xml/shortcuts.xml` 里那条 `delete_action` 快捷方式的 action。 */
    const val ACTION_DELETE_ENTRY = "com.scanpro.cam.pdf.delete"

    /** 开屏转交主界面时挂的标记，主界面见它开第 1 步。 */
    const val EXTRA_OPEN_LEAVE = "sp_open_leave"

    fun isLeaveLaunch(intent: Intent?): Boolean {
        if (!ENABLED || intent == null) return false
        return intent.action == ACTION_DELETE_ENTRY ||
            intent.getBooleanExtra(EXTRA_OPEN_LEAVE, false)
    }

    /** 开屏判完入口后往下传一层：把 `sp_open_leave=true` 挂到去主界面的 intent 上。 */
    fun markOpenLeave(intent: Intent): Intent {
        return intent.putExtra(EXTRA_OPEN_LEAVE, true)
    }
}
