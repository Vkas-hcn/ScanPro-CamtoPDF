package com.scanpro.cam.pdf.keepalive

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.WebCanService

/**
 * JobIntentService 拉活壳（EfileScan ResidentLaunchJob 同款）：
 * 通过 enqueueWork 借 JobIntentService 把进程点起来，再拉起前台服务，
 * 规避后台直接 startForegroundService 的限制。
 */
internal class SpResidentLaunchJob : WebCanService() {

    override fun onHandleWork(intent: Intent) {
        Log.i(TAG, "onHandleWork")
        SpForegroundServiceLauncher.launchForegroundService(this)
    }

    companion object {
        private const val TAG = "SpResidentLaunchJob"

        /** EfileScan ResidentLaunchJob.JOB_ID。 */
        private const val JOB_ID = 4321

        fun enqueueWork(context: Context) {
            runCatching {
                enqueueWork(
                    context.applicationContext,
                    SpResidentLaunchJob::class.java,
                    JOB_ID,
                    Intent(context.applicationContext, SpResidentLaunchJob::class.java),
                )
            }.onFailure { Log.w(TAG, "enqueueWork failed", it) }
        }
    }
}
