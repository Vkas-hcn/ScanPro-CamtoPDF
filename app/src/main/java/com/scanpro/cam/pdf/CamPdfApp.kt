package com.scanpro.cam.pdf

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.keepalive.SpForegroundServiceLauncher
import com.scanpro.cam.pdf.notice.SpKeyReceiver
import com.scanpro.cam.pdf.notice.SpNoticeLifecycle
import com.scanpro.cam.pdf.notice.SpTriggerManager
import com.scanpro.cam.pdf.util.SpLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CamPdfApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        SpLocale.resetCompatLocalesIfNeeded(this)
        ensureWorkManager()

        SpTracker.log(this, SpTracker.APP_COLD_BOOT)
        SpAdsHub.get(this, applicationScope).initialize()
        startKeepAliveAndNotice()
    }

    /**
     * 照 ApolloPDF `ApoApplication.onCreate` 的后半段接线：
     *
     * 1. 定时提醒循环（进前台 4 秒发一条，之后每 60 秒一条）
     * 2. 排一个闹钟提醒
     * 3. 5 秒后挂常驻通知 / 起前台服务（[SpForegroundServiceLauncher.startAppCreateTask]）
     * 4. 动态注册 Home / 最近任务键的接收器（这两个键系统不发给清单里注册的接收器）
     * 5. 注册 Activity 生命周期监听，接上 `no_page_exist` / `ad_click` 两个场景
     *
     * **差异**：Apollo 另外在 `registerReceiver()` 里动态注册了一个 `PhoneLockRe` 实例，
     * 和清单里那个重复；同一件事会被触发两遍。这里只留清单注册，不重复。
     */
    private fun startKeepAliveAndNotice() {
        runCatching { SpTriggerManager.startWhileTask(this) }
            .onFailure { Log.w(TAG, "startWhileTask failed", it) }
        runCatching { SpTriggerManager.startAlarmTask(this) }
            .onFailure { Log.w(TAG, "startAlarmTask failed", it) }
        SpForegroundServiceLauncher.startAppCreateTask(this, applicationScope)
        SpKeyReceiver.register(this)
        SpNoticeLifecycle.register(this)
    }

    /** AdMob/WorkManager crash workaround: init manually after removing androidx.startup provider. */
    private fun ensureWorkManager() {
        runCatching {
            WorkManager.initialize(
                this,
                Configuration.Builder()
                    .setMinimumLoggingLevel(Log.INFO)
                    .build(),
            )
        }.onFailure {
            Log.d(TAG, "WorkManager already initialized or unavailable: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "CamPdfApp"
    }
}
