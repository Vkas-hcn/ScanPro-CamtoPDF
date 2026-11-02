package com.scanpro.cam.pdf.keepalive

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.notice.SpTriggerManager

/**
 * 持久周期保活 Job（EfileScan ResidentPersistJobService 同款）：
 * 15 分钟周期 + setPersisted(true) 跨开机保留，由系统自行重排，
 * 不依赖任何外部广播——进程死后最多 15 分钟必被拉起一次。
 *
 * 三星机型整段跳过：EfileScan 实测三星会削弱持久周期 Job，该群体
 * 仍由现有闹钟/锁屏/前台服务路径覆盖。
 */
class SpPersistJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.i(TAG, "onStartJob")
        SpTrackEvents.keepaliveTrigger(TRIGGER_BINDER)
        runCatching { SpResidentLaunchJob.enqueueWork(this) }
            .onFailure { Log.w(TAG, "enqueueWork failed", it) }
        runCatching { SpTriggerManager.setAlarm(this, force = true) }
            .onFailure { Log.w(TAG, "setAlarm failed", it) }
        runCatching { schedule(this) }
            .onFailure { Log.w(TAG, "schedule failed", it) }
        runCatching { jobFinished(params, false) }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val TAG = "SpPersistJob"

        /** EfileScan ResidentPersistJobService.JOB_ID。 */
        private const val JOB_ID = 54_321

        /** JobScheduler 周期下限 15 分钟。 */
        private const val PERIOD_MINUTES = 15L

        private const val TRIGGER_BINDER = "binder"

        fun schedule(context: Context) {
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                Log.i(TAG, "schedule skipped: samsung")
                return
            }
            runCatching {
                val scheduler =
                    context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                        ?: return@runCatching
                val job = JobInfo.Builder(
                    JOB_ID,
                    ComponentName(context, SpPersistJobService::class.java),
                )
                    .setPeriodic(PERIOD_MINUTES * 60_000L)
                    .setPersisted(true)
                    .build()
                scheduler.schedule(job)
                Log.i(TAG, "schedule ok: period=${PERIOD_MINUTES}min")
            }.onFailure { Log.w(TAG, "schedule failed", it) }
        }
    }
}
