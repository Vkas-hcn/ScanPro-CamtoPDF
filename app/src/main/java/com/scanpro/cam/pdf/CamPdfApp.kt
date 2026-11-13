package com.scanpro.cam.pdf

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.bockcock.adjust.AdJustHelp
import com.bockcock.adjust.BiHelp
import com.mymy.mymy.GaidUtil
import com.mymy.mymy.ads.count.ADManagerMy
import com.zppko.pdfplus.nor.Zppkoo
import com.zppko.pdfplus.nor.horus.HintExt01
import com.scanpro.cam.pdf.util.SpLocale

class CamPdfApp : Application() {


    override fun onCreate() {
        super.onCreate()
        app = this
        // PDF26 在 Application.onCreate 初始化 SpUtil；UserParms.isOm()（UMP 欧盟判断）
        // 可能在开屏页提前用到，所以这里先初始化。
        com.zppko.pdfplus.nor.ads.tool.SpUtil.init(this)

        SpLocale.resetCompatLocalesIfNeeded(this)
        ensureWorkManager()

        ADManagerMy.start(this)

        BiHelp().initBI(this)// 先初始化BI,adjust需要 distinctId，

        AdJustHelp.initAdjust(this)

        GaidUtil.init(this)

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




    companion object {
        private const val TAG = "CamPdfApp"
        lateinit var app: Application
    }
}
