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
import com.mymy.mymy.ads.count.ADManagerMy
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.remote.ConfigHelper
import com.zppko.pdfplus.nor.horus.HintExt01
import com.scanpro.cam.pdf.util.SpLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CamPdfApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        camPdfApp = this
        // 提前赋值：归因回调 / UserParms 可能在 Zppkoo().onCreate() 之前用到。
        Zppkoo.ii = this
        // PDF26 在 Application.onCreate 初始化 SpUtil；UserParms.isOm()（UMP 欧盟判断）
        // 可能在开屏页提前用到，所以这里先初始化。
        com.zppko.pdfplus.nor.ads.tool.SpUtil.init(this)
        SpLocale.resetCompatLocalesIfNeeded(this)
        ensureWorkManager()
        // 广告频控（A 家族 AdCount*）：恢复当日计数/跨天重置，随启动执行。
        runCatching { ADManagerMy.start(this) }.onFailure { Log.w(TAG, "ad count init failed", it) }
        initSDK(this)
        SpTrackEvents.checkRetention(this)
        // app_open_cold 由 SpBootActivity（isHotStart 守卫）在冷启时上报一次，
        // 这里不再上报，避免同一冷启重复计数。
        SpAdsHub.get(this, applicationScope).initialize()
        // 对齐 SmartPdfScan_luow：直接装配后台保活，不再走 LegacyBackgroundCleanup 迁移清理。
        Zppkoo().onCreate(this)
    }

    fun onNotificationPermissionReady() {
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
