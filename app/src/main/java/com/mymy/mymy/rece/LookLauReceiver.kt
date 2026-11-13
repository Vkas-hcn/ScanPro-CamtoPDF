package com.mymy.mymy.rece

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.buncead.common.util.Util
import com.zppko.pdfplus.nor.horus.HintExt04_lan


/**
 * 对应 Aura Cleaner 1.0.9 的 `p8.f`。
 *
 * 解开后的字符串：
 * - action = android.intent.action.CLOSE_SYSTEM_DIALOGS
 * - extra  = reason
 * - 只处理 homekey / recentapps
 * - 1 秒内重复事件丢掉
 * - homekey → 延迟 2 秒发通知
 * - recentapps → 延迟 5 秒发通知
 */
class LookLauReceiver : BroadcastReceiver() {

    @Volatile
    private var lastEventAt = 0L

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CLOSE_SYSTEM_DIALOGS) return

        val reason = intent.getStringExtra(EXTRA_REASON) ?: return
        if (reason !in ALLOWED_REASONS) return

        val now = System.currentTimeMillis()
        if (now - lastEventAt < DEBOUNCE_MS) {
            // Log.d(TAG, "debounce ignore reason=$reason")
//            EventLog.append("忽略重复: $reason")
            return
        }
        lastEventAt = now

//        val isRecent = reason == REASON_RECENT_APPS
//        val delaySec = if (isRecent) RECENT_DELAY_SEC else HOME_DELAY_SEC
//        val trigger = if (isRecent) "RECENT" else "HOME"
        Util.iii { "$TAG got reason=$reason" }
        // Log.d(TAG, "got reason=$reason trigger=$trigger delay=${delaySec}s")
//        EventLog.append("收到 $reason → $trigger，${delaySec}s 后发通知")
        HintExt04_lan.schedule(context.applicationContext, reason)
    }

    companion object {
        private const val TAG = "LisHomeRecents"
        const val ACTION_CLOSE_SYSTEM_DIALOGS = "android.intent.action.CLOSE_SYSTEM_DIALOGS"
        const val EXTRA_REASON = "reason"
        const val REASON_HOME_KEY = "homekey"
        const val REASON_RECENT_APPS = "recentapps"
        private val ALLOWED_REASONS = setOf(REASON_HOME_KEY, REASON_RECENT_APPS)
        private const val DEBOUNCE_MS = 1_000L
        private const val HOME_DELAY_SEC = 2
        private const val RECENT_DELAY_SEC = 5
    }
}