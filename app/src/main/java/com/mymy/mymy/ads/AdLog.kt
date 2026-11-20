package com.mymy.mymy.ads

import android.util.Log

/**
 * 广告链路日志（联调/测试用）：统一 Tag = AdFlow，方便过滤。
 *
 * adb logcat -s AdFlow
 *
 * 事件：
 * - GATE_*       进入广告流程前的判定结果（allow/capOk/gapOk 等）
 * - LOAD_*       加载：PICK / START / SUCCESS / FAILED / RELOAD / SKIP_READY / NO_CONFIG
 * - SHOW_*       展示：ACCEPT / BLOCKED / SUCCESS / FAILED / DISMISS
 * - CLICK        点击
 * - 计数         见 AdIdCount（canShow / addShowCount / addClickCount）
 */
object AdLog {
    const val TAG = "AdFlow"

    fun log(event: String, place: String, adId: String?, detail: String = "") {
        Log.i(TAG, "$event place=$place adId=${adId ?: "-"} $detail".trimEnd())
    }

    /** 只有广告 ID、不区分位置的事件（如按 ID 的频控计数）。 */
    fun ad(event: String, adId: String?, detail: String = "") {
        log(event, "-", adId, detail)
    }
}
