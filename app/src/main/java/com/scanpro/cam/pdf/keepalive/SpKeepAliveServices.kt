package com.scanpro.cam.pdf.keepalive

import android.app.Notification
import android.app.Service
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.notice.SpNotifyPermit

/**
 * 保活的三个壳。照抄 ApolloPDF `keep_alive/`：
 *
 * - [SpApoForegroundService] ← `ApoForegroundService.java`
 * - [SpAssistanceLaunchJobService] ← `AssistanceLaunchJobService.java`
 * - [SpYoutubeService] ← `YoutubeService.java`
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/keep_alive/`
 */

/**
 * 前台服务。存在的意义只有一个：让进程有个「前台」身份，系统不轻易回收。
 *
 * Apollo `ApoForegroundService`：
 * - `onCreate` 里就把自己挂成前台（`startForegroundInternal`）
 * - `onStartCommand` 每次回来重挂一遍通知（`updateNotice`），并返回 `START_STICKY`(1)
 * - `isRunning` 是个静态标记，给 [SpForegroundServiceLauncher] 判重
 *
 * 前台服务类型用 `mediaPlayback`（Apollo 传的也是 2）。这一支在部分机型上会被系统拒，
 * 拒了就走兜底通知、再不行 `stopSelf`——是 Apollo 的原样行为，不是我们的补丁。
 */
class SpApoForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    private var startedAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startedAtMs = System.currentTimeMillis()
        SpTrackEvents.keepaliveServiceStart(SERVICE_NAME, SpForegroundServiceLauncher.lastTriggerSource)
        startForegroundInternal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        updateNotice()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val aliveMs = if (startedAtMs > 0L) System.currentTimeMillis() - startedAtMs else 0L
        SpTrackEvents.keepaliveServiceStop(SERVICE_NAME, aliveMs)
        startedAtMs = 0L
        isRunning = false
    }

    /** Apollo `startForegroundInternal`：先试自定义版式，炸了再试兜底版，再炸就退出。 */
    private fun startForegroundInternal() {
        runCatching {
            startForegroundWithNotification(SpPersistentNotifyManager.createPersistentNotification(this))
        }.onFailure { first ->
            Log.w(TAG, "custom persistent notification failed, fallback", first)
            runCatching {
                startForegroundWithNotification(
                    SpPersistentNotifyManager.createStandardPersistentNotification(applicationContext),
                )
            }.onFailure { second ->
                Log.e(TAG, "fallback persistent notification failed, stopSelf", second)
                runCatching { stopSelf() }
            }
        }
    }

    /** Apollo `startForegroundWithNotification`。29+ 带上类型 `mediaPlayback`。 */
    private fun startForegroundWithNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SpPersistentNotifyManager.PERSISTENT_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(SpPersistentNotifyManager.PERSISTENT_NOTIFICATION_ID, notification)
        }
    }

    /** Apollo `updateNotice`：通知被人划掉了就补报一次埋点，然后重挂。 */
    private fun updateNotice() {
        runCatching {
            if (!SpNotifyPermit.isNotificationShowing(this, SpPersistentNotifyManager.PERSISTENT_NOTIFICATION_ID) &&
                SpNotifyPermit.isHaveNotifyPermission(this)
            ) {
                SpTracker.log(this, SpTracker.ALWAYS_NOTIFICATION_TRIGGER)
            }
            startForegroundInternal()
        }.onFailure { Log.e(TAG, "updateNotice failed", it) }
    }

    companion object {
        private const val TAG = "SpApoForegroundService"

        /** 规范 E20/E22 的 service_name，固定取前台服务名。 */
        private const val SERVICE_NAME = "SpApoForegroundService"

        /** Apollo `ApoForegroundService.isRunning`。 */
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

/**
 * 一个只走 JobScheduler 的空活。系统到点把进程拉起来，`onStartJob` 里点一下前台服务。
 *
 * Apollo `AssistanceLaunchJobService`：`onStartJob` 永远返回 `false`（干完就结束，不占用调度），
 * `onStopJob` 也返回 `false`（被中断不重排，重排由 [SpForegroundServiceLauncher] 下次再叫）。
 *
 * JobId 见 [SpForegroundServiceLauncher.ASSISTANCE_JOB_ID]。
 */
class SpAssistanceLaunchJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Log.i(TAG, "onStartJob")
        runCatching {
            SpForegroundServiceLauncher.launchForegroundService(applicationContext)
        }.onFailure { Log.w(TAG, "launchForegroundService failed", it) }
        runCatching { jobFinished(params, false) }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val TAG = "SpAssistJob"
    }
}

/**
 * 越权壳：清单里声明了 `android.media.MediaRouteProviderService` 这个 action，
 * 系统会来 bind 一次，拿到 null binder 就走。
 *
 * 目的和 Apollo `YoutubeService` 一样：多挂一条系统 binder 连接，进程更难被杀。本身不干活。
 */
class SpYoutubeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null
}
