package com.scanpro.cam.pdf.keepalive

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 把保活的两条路点起来。照抄 ApolloPDF `keep_alive/ForegroundServiceLauncher.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/keep_alive/ForegroundServiceLauncher.java`
 *
 * 两条路：
 *
 * | 方法 | 手段 | 谁调 |
 * |---|---|---|
 * | [launchForegroundService] | 直接 `startForegroundService` | [SpAssistanceLaunchJobService.onStartJob] |
 * | [startLaunchService] | 排一个零延迟 Job，借系统的 JobScheduler 把进程叫起来 | 按键/解锁/开机/闹钟 |
 *
 * 「先排 Job 再在 Job 里起前台服务」这个绕法是 Apollo 的核心：直接起前台服务在后台会被系统拒，
 * 而 JobScheduler 一定能把进程拉起来。
 */
object SpForegroundServiceLauncher {

    /** Apollo `ASSISTANCE_JOB_ID`。 */
    const val ASSISTANCE_JOB_ID = 46555

    private const val TAG = "SpFgLauncher"

    /**
     * Apollo `launchForegroundService(context)`：
     * 服务已经在跑就只补发一次常驻通知，没在跑才真的起。
     */
    fun launchForegroundService(context: Context) {
        if (!SpBlockingLogic.get(context).canStartForegroundService()) return
        Log.i(TAG, "launchForegroundService: allowed")
        runCatching {
            if (SpApoForegroundService.isRunning) {
                SpPersistentNotifyManager.sendPersistentNotification(context)
            } else {
                val intent = Intent(context, SpApoForegroundService::class.java)
                context.startForegroundService(intent)
            }
        }.onFailure { Log.w(TAG, "launchForegroundService failed", it) }
    }

    /** Apollo `startLaunchService(context)`：排一个 `minimumLatency = 0` 的 Job。 */
    fun startLaunchService(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val job = JobInfo.Builder(
                ASSISTANCE_JOB_ID,
                ComponentName(context, SpAssistanceLaunchJobService::class.java),
            ).setMinimumLatency(0L).build()
            scheduler.schedule(job)
        }.onFailure { Log.w(TAG, "startLaunchService failed", it) }
    }

    /**
     * 冷启动挂常驻通知。Apollo `ApoApplication$onCreate$8`（`onCreate` 里最后那段 IO 协程）：
     *
     * 1. `delay(5000)` —— 等启动抖动过去，也避开开屏广告
     * 2. 切主线程：`Android 12+ 或前台服务已在跑` → 只补发常驻通知；
     *    否则 → 直接 `startForegroundService`
     *
     * **12+ 只发通知、不起前台服务**是 Apollo 的原始分支，不是笔误：
     * 12 起后台起前台服务会被 `ForegroundServiceStartNotAllowedException` 拦，
     * 而 12+ 的系统本来就不会因为这个通知回收进程。进程真要拉起来走 [startLaunchService] 那条 Job。
     */
    fun startAppCreateTask(context: Context, scope: CoroutineScope) {
        scope.launch {
            runCatching {
                delay(APP_CREATE_DELAY_MS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || SpApoForegroundService.isRunning) {
                    SpPersistentNotifyManager.sendPersistentNotification(context)
                } else {
                    context.startForegroundService(Intent(context, SpApoForegroundService::class.java))
                }
            }.onFailure { Log.w(TAG, "app-create keep-alive failed", it) }
        }
    }

    /** Apollo `ApoApplication$onCreate$8` 里的 5000ms。 */
    private const val APP_CREATE_DELAY_MS = 5000L
}
