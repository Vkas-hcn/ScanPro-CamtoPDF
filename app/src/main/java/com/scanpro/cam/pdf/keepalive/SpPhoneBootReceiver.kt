package com.scanpro.cam.pdf.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scanpro.cam.pdf.notice.SpTriggerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 开机 / 覆盖安装。照抄 ApolloPDF `keep_alive/PhoneBootRv.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/keep_alive/PhoneBootRv.java`
 * 及 `PhoneBootRv$onReceive$1$1.java`、`PhoneBootRv$onReceive$1$2.java`
 *
 * 收到广播后并行两件事：
 *
 * 1. 等 **1500ms**，排一个零延迟 Job 把进程和前台服务点起来（[SpForegroundServiceLauncher.startLaunchService]）
 * 2. 立刻无条件重排闹钟（[SpTriggerManager.setAlarm]，`force = true`）
 *
 * 为什么要等 1500ms：开机瞬间 `BOOT_COMPLETED` 附近系统正忙，JobScheduler 直接排会被丢；
 * 覆盖安装（`MY_PACKAGE_REPLACED`）同理，包刚换完包内组件还没就绪。
 */
class SpPhoneBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.i(TAG, "onReceive action=${intent?.action}")
        val app = context.applicationContext

        SpForegroundServiceLauncher.markTrigger(SpForegroundServiceLauncher.TRIGGER_BOOT)
        SpForegroundServiceLauncher.markTriggerType("boot")

        scope.launch {
            runCatching {
                delay(BOOT_KEEP_ALIVE_DELAY_MS)
                SpForegroundServiceLauncher.startLaunchService(app)
            }.onFailure { Log.w(TAG, "boot keep-alive failed", it) }
        }
        scope.launch {
            runCatching { SpTriggerManager.setAlarm(app, force = true) }
                .onFailure { Log.w(TAG, "boot setAlarm failed", it) }
        }
    }

    companion object {
        private const val TAG = "SpPhoneBootReceiver"

        /** Apollo `PhoneBootRv$onReceive$1$1` 里的 1500ms。 */
        private const val BOOT_KEEP_ALIVE_DELAY_MS = 1500L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
