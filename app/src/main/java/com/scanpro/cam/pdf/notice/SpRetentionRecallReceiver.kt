package com.scanpro.cam.pdf.notice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scanpro.cam.pdf.keepalive.SpForegroundServiceLauncher

/**
 * D+1 定点召回到点。模式同 SpAlarmReceiver：
 * 发召回通知 → 点一次保活 → 推进下一档（24h/48h/72h）。
 * 只在清单注册（无 intent-filter），仅由 SpRetentionRecall 的 PendingIntent 显式拉起。
 */
class SpRetentionRecallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val app = context?.applicationContext ?: return
        Log.i(TAG, "onReceive")
        if (SpRetentionRecall.shouldRecall(app) && SpNotifyPermit.isHaveNotifyPermission(app)) {
            runCatching {
                val item = SpNotifyCatalog.scheduleCopywriting(app)
                SpSendManager.sendSysNotification(app, item, SpNotifyScene.SCHEDULED)
            }.onFailure { Log.w(TAG, "sendSysNotification failed", it) }
        }
        SpForegroundServiceLauncher.markTriggerType(TRIGGER_RETENTION_RECALL)
        runCatching { SpForegroundServiceLauncher.startLaunchService(app) }
            .onFailure { Log.w(TAG, "startLaunchService failed", it) }
        SpRetentionRecall.advanceAndSchedule(app)
    }

    companion object {
        private const val TAG = "SpRetentionRecallReceiver"
        private const val TRIGGER_RETENTION_RECALL = "retention_recall"
    }
}
