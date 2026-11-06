package com.scanpro.cam.pdf.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scanpro.cam.pdf.notice.SpRetentionRecall
import com.scanpro.cam.pdf.notice.SpTriggerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 开机 / 覆盖安装 / 电源接通 / 电源断开。照抄 ApolloPDF keep_alive/PhoneBootRv.java，
 * 电源两路照 EfileScan ResidentBootReceiver 追加。
 *
 * 收到广播后并行三件事：
 * 1. 延迟后（开机 1500ms / 电源 500ms）排零延迟 Job 把进程和前台服务点起来；
 * 2. 无条件重排闹钟（setAlarm force=true）；
 * 3. 重挂持久周期 Job 与 D+1 定点召回（两者都不跨重启）。
 * 电源事件额外触发一条定时池召回通知。
 */
class SpPhoneBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action ?: return
        Log.i(TAG, "onReceive action=$action")
        val app = context.applicationContext
        val isPower = action == Intent.ACTION_POWER_CONNECTED || action == Intent.ACTION_POWER_DISCONNECTED

        SpForegroundServiceLauncher.markTrigger(SpForegroundServiceLauncher.TRIGGER_BOOT)
        SpForegroundServiceLauncher.markTriggerType(if (isPower) TRIGGER_POWER else TRIGGER_BOOT)

        val keepAliveDelay = if (isPower) POWER_KEEP_ALIVE_DELAY_MS else BOOT_KEEP_ALIVE_DELAY_MS
        scope.launch {
            runCatching {
                delay(keepAliveDelay)
                // BOOT_COMPLETED / MY_PACKAGE_REPLACED 是 Android 12+ 允许后台起前台服务的系统豁免；
                // 前台服务已改成 specialUse 类型（mediaPlayback 在 Android 15 起不能从开机启动）。
                // 所以这里直接拉起，Job 只作兜底。
                SpForegroundServiceLauncher.launchForegroundService(app)
                SpForegroundServiceLauncher.startLaunchService(app)
            }.onFailure { Log.w(TAG, "boot keep-alive failed", it) }
        }
        scope.launch {
            runCatching { SpTriggerManager.setAlarm(app, force = true) }
                .onFailure { Log.w(TAG, "boot setAlarm failed", it) }
        }
        scope.launch {
            runCatching { SpPersistJobService.schedule(app) }
                .onFailure { Log.w(TAG, "persist job schedule failed", it) }
            runCatching { SpRetentionRecall.schedule(app) }
                .onFailure { Log.w(TAG, "retention recall schedule failed", it) }
        }
        if (isPower) {
            scope.launch {
                delay(POWER_KEEP_ALIVE_DELAY_MS)
                runCatching { SpTriggerManager.sendScheduleNotification(app) }
                    .onFailure { Log.w(TAG, "power notification failed", it) }
            }
        }
    }

    companion object {
        private const val TAG = "SpPhoneBootReceiver"

        /** Apollo PhoneBootRv 里的 1500ms。 */
        private const val BOOT_KEEP_ALIVE_DELAY_MS = 1500L

        /** 电源事件系统不忙，500ms 足够（照 EfileScan ResidentBootReceiver）。 */
        private const val POWER_KEEP_ALIVE_DELAY_MS = 500L

        private const val TRIGGER_BOOT = "boot"
        private const val TRIGGER_POWER = "power"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
