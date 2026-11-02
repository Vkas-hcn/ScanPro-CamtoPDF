package com.scanpro.cam.pdf.notice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.scanpro.cam.pdf.keepalive.SpForegroundServiceLauncher

/**
 * 装包/卸包广播召回（EfileScan EngagementPackageReceiver 同款思路）：
 * 任何第三方 App 安装/卸载都是高频系统事件，借它做一次唤醒 + 召回通知。
 *
 * 双通道接收 PACKAGE_ADDED：清单注册（进程死亡时也能醒，但 Android 14+
 * 后台会被系统拦截）+ 前台服务内动态注册（进程存活期间兜底）。两条路径
 * 同时命中时用 3 秒防抖去重。PACKAGE_FULLY_REMOVED 只走清单。
 */
class SpPackageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_FULLY_REMOVED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return
        val now = System.currentTimeMillis()
        if (now - lastDispatchTime < DEBOUNCE_MS) return
        lastDispatchTime = now
        val triggerType = if (action == Intent.ACTION_PACKAGE_ADDED) TRIGGER_PACKAGE_ADDED else TRIGGER_PACKAGE_REMOVED
        Log.i(TAG, "onReceive action=$action pkg=$pkg")
        SpForegroundServiceLauncher.markTriggerType(triggerType)
        SpForegroundServiceLauncher.startLaunchService(context.applicationContext)
        // 召回通知：复用定时池文案与频控（SCHEDULED 场景），不新增配置键
        runCatching { SpTriggerManager.sendScheduleNotification(context.applicationContext) }
            .onFailure { Log.w(TAG, "sendScheduleNotification failed", it) }
    }

    companion object {
        private const val TAG = "SpPackageReceiver"

        private const val DEBOUNCE_MS = 3000L
        private const val TRIGGER_PACKAGE_ADDED = "package_added"
        private const val TRIGGER_PACKAGE_REMOVED = "package_removed"

        @Volatile
        private var lastDispatchTime = 0L

        /** 前台服务内动态注册 PACKAGE_ADDED（Android 14+ 后台清单接收器会被系统拦截）。 */
        fun registerDynamic(context: Context) {
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    SpPackageReceiver(),
                    IntentFilter(Intent.ACTION_PACKAGE_ADDED),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }.onFailure { Log.w(TAG, "registerDynamic failed", it) }
        }
    }
}
