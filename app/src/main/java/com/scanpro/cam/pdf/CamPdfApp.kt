package com.scanpro.cam.pdf

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.LogLevel
import com.flux.tracksdk.core.TrackConfig
import com.flux.tracksdk.core.TrackSDK
import com.flux.tracksdk.model.TrackPolicy
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.analytics.SpTracker
import com.mymy.mymy.ads.count.ADManagerMy
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.keepalive.LegacyBackgroundCleanup
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.remote.ConfigHelper
import com.zppko.pdfplus.nor.horus.HintExt01
import com.scanpro.cam.pdf.util.SpLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CamPdfApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var backgroundInitialized = false
    private var backgroundInitializationAttempted = false
    private var notificationPermissionReadyPending = false

    override fun onCreate() {
        super.onCreate()
        camPdfApp = this
        // Attribution callbacks may arrive before background cleanup completes.
        Zppkoo.ii = this
        // PDF26 initializes SpUtil in Application.onCreate; UserParms.isOm() (UMP 欧盟判断)
        // may run on the splash before the deferred Zppkoo.onCreate(), so initialize it here.
        com.zppko.pdfplus.nor.ads.tool.SpUtil.init(this)
        SpLocale.resetCompatLocalesIfNeeded(this)
        ensureWorkManager()
        // 广告频控（A 家族 AdCount*）：恢复当日计数/跨天重置，随启动执行。
        runCatching { ADManagerMy.start(this) }.onFailure { Log.w(TAG, "ad count init failed", it) }
        initSDK(this)
        SpTrackEvents.checkRetention(this)
        SpTracker.log(this, SpTracker.APP_COLD_BOOT)
        SpAdsHub.get(this, applicationScope).initialize()
        startReplacementAfterCleanup()
    }

    private fun startReplacementAfterCleanup() {
        // Register lifecycle observers before the first Activity starts. Network topic cleanup
        // is independent: an offline device must still initialize its local background work.
        fun cleanLocalAndStart(): LegacyBackgroundCleanup.Result? {
            val result = runCatching { LegacyBackgroundCleanup.runBeforeNewWork(this) }
                .onFailure { Log.w(TAG, "legacy cleanup failed", it) }
                .getOrNull()
            if (result?.localComplete == true) startReplacementBackground()
            return result
        }
        val initial = cleanLocalAndStart()
        if (backgroundInitialized && initial?.topicComplete == true) return
        applicationScope.launch {
            repeat(12) { attempt ->
                delay(5_000L)
                val result = cleanLocalAndStart()
                if (backgroundInitialized && result?.topicComplete == true) return@launch
                Log.w(TAG, "legacy cleanup pending (attempt ${attempt + 1}): ${result?.errors}")
            }
            Log.w(TAG, "legacy topic cleanup continues in Firebase and retries on next process start")
        }
    }

    private fun startReplacementBackground(): Boolean {
        if (backgroundInitialized) return true
        if (backgroundInitializationAttempted) return false
        backgroundInitializationAttempted = true
        return runCatching {
            Zppkoo().onCreate(this)
            backgroundInitialized = true
            if (notificationPermissionReadyPending) {
                notificationPermissionReadyPending = false
                startForegroundAfterPermission()
            }
            true
        }.onFailure { Log.e(TAG, "A background initialization incomplete", it) }
            .getOrDefault(false)
    }

    fun onNotificationPermissionReady() {
        if (!backgroundInitialized) {
            notificationPermissionReadyPending = true
            return
        }
        startForegroundAfterPermission()
    }

    private fun startForegroundAfterPermission() {
        runCatching { HintExt01.launchFg() }
            .onFailure { Log.w(TAG, "foreground notification startup failed", it) }
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

    fun initSDK(app: Application) {
        initTrackSdk()
        initAdJust(app)
    }

    fun initTrackSdk() {
        val trackConfig = TrackConfig.Builder(this)
            .baseUrl(getString(R.string.sp_track_base_url).trim())
            .debug(BuildConfig.DEBUG)
            .appId(packageName)
            .channel("google_play")
            .batchSize(20)
            .defaultUploadPolicy(TrackPolicy.BATCH)
            .uploadThreadCount(2)
            .maxRetryCount(5)
            .connectTimeout(10_000)
            .readTimeout(10_000)
            .enablePiggyback(true)
            .enableAppList(false)
            .build()
        TrackSDK.init(trackConfig)
    }

    @SuppressLint("HardwareIds")
    private fun initAdJust(application: Application) {
        val deviceId = TrackSDK.getInstance().effectiveDistinctId
        val appToken = getString(R.string.sp_adjust_app_token).trim()
        val environment: String = if (BuildConfig.DEBUG) {
            AdjustConfig.ENVIRONMENT_SANDBOX
        } else {
            AdjustConfig.ENVIRONMENT_PRODUCTION
        }
        val config = AdjustConfig(application, appToken, environment)
        config.setLogLevel(LogLevel.WARN)
        config.setExternalDeviceId(deviceId)
        config.enableFirstSessionDelay()
        config.enableSendingInBackground()
        ConfigHelper.userTypeListener(config)
        Adjust.initSdk(config)
        Adjust.endFirstSessionDelay()
    }

    companion object {
        private const val TAG = "CamPdfApp"
        @Volatile
        private var camPdfApp: CamPdfApp? = null
        fun get(): CamPdfApp =
            camPdfApp ?: error("Application not initialized")
    }
}
