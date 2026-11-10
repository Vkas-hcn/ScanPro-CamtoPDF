package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.view.ViewGroup
import com.scanpro.cam.pdf.ads.manager.SpNativeAdManager
import com.scanpro.cam.pdf.ads.manager.SpNativeAdStyle
import com.scanpro.cam.pdf.util.AppLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 首页 / 文件 / 返回 几处插屏的共用触发。60 秒节流照 B 原时序保留；
 * 总闸/频控/展示由 SpFullScreenAdManager 接线层负责（对齐 PDF26 后不再直接读缓存）。
 * 埋点对齐 PDF26：不再发 Shark_* / ad_blocked 事件，仅保留日志。
 */
object SpPageAdTriggers {

    /** Apollo `HomeActivity`：距上次插屏展示不足 60 秒不再弹。 */
    private const val TAB_THROTTLE_MS = 60_000L

    /** E05 `ad_request` 的预加载标记（规范 §4E05）。 */
    private const val SCENE_PRELOAD = "preload"

    @Volatile
    private var lastShowAdTime = 0L

    fun checkLoadAdByTab(activity: Activity, position: String) {
        if (activity.isFinishing || activity.isDestroyed) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=页面已销毁")
            return
        }
        if (System.currentTimeMillis() - lastShowAdTime <= TAB_THROTTLE_MS) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=距上次展示不足60秒")
            return
        }
        val manager = SpAdsHub.getOrNull()?.fullScreen(activity) ?: run {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=广告模块未就绪")
            return
        }
        if (!manager.hasReadyAd(position)) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=广告尚未就绪")
            manager.checkAndLoadAd(position, position)
            return
        }
        if (activity.isFinishing || activity.isDestroyed) return
        manager.showFullScreenAd(position, position) { showed, _ ->
            if (showed) lastShowAdTime = System.currentTimeMillis()
        }
    }

    /**
     * 扫描入口：同样走 `ac_file_int` 的 60 秒节流。
     * 有就绪广告先展示，关掉（或没展示）再进扫描页，避免壳页已经跳走。
     */
    fun showFileIntThen(activity: Activity, then: () -> Unit) {
        val position = SpAdPosition.AC_FILE_INT
        if (activity.isFinishing || activity.isDestroyed) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=页面已销毁")
            then()
            return
        }
        if (System.currentTimeMillis() - lastShowAdTime <= TAB_THROTTLE_MS) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=距上次展示不足60秒")
            then()
            return
        }
        val manager = SpAdsHub.getOrNull()?.fullScreen(activity)
        if (manager == null) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=广告模块未就绪")
            then()
            return
        }
        if (!manager.hasReadyAd(position)) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=广告尚未就绪")
            manager.checkAndLoadAd(position, position)
            then()
            return
        }
        val once = AtomicBoolean(false)
        val finish = { if (once.compareAndSet(false, true)) then() }
        manager.showFullScreenAd(position, position) { showed, dismissed ->
            if (showed) lastShowAdTime = System.currentTimeMillis()
            if (!showed || dismissed) finish()
        }
    }

    /** Apollo `FileListFragment`：`showInterstitialAdNoWait(ac_check_int)` 后再打开文件。 */
    fun showCheckIntThen(activity: Activity, then: () -> Unit) {
        val manager = SpAdsHub.getOrNull()?.fullScreen(activity)
        if (manager == null) {
            then()
            return
        }
        manager.showInterstitialAdNoWait(SpAdPosition.AC_CHECK_INT, then)
    }

    /** Apollo `BaseActivity.showBackHomeIntAd`：关掉或没缓存就 `finish`。 */
    fun showBackHomeIntThen(activity: Activity, then: () -> Unit) {
        val manager = SpAdsHub.getOrNull()?.fullScreen(activity)
        if (manager == null) {
            then()
            return
        }
        manager.showInterstitialAdNoWait(SpAdPosition.AC_MAIN_BACK_INT, then)
    }

    fun preloadMainBack(activity: Activity) {
        SpAdsHub.getOrNull()?.fullScreen(activity)?.checkAndLoadAd(
            position = SpAdPosition.AC_MAIN_BACK_INT,
            scene = SCENE_PRELOAD,
        )
    }

    /**
     * `ac_check_int` 原来没有任何预加载点：点开文件时才读缓存，几乎必然 miss。
     * 进文件列表页就预热一条，命中率才起得来。
     */
    fun preloadCheckInt(activity: Activity) {
        SpAdsHub.getOrNull()?.fullScreen(activity)?.checkAndLoadAd(
            position = SpAdPosition.AC_CHECK_INT,
            scene = SCENE_PRELOAD,
        )
    }

    fun bindMainNative(activity: Activity, container: ViewGroup): SpNativeAdManager? {
        val hub = SpAdsHub.getOrNull() ?: return null
        return hub.native(activity, container).also {
            it.checkLoadAndShowAd(SpAdPosition.AC_MAIN_NAT, SpNativeAdStyle.small)
        }
    }

    fun bindSettingNative(activity: Activity, container: ViewGroup): SpNativeAdManager? {
        val hub = SpAdsHub.getOrNull() ?: return null
        return hub.native(activity, container).also {
            it.checkLoadAndShowAd(SpAdPosition.AC_SETTING_NAT, SpNativeAdStyle.big)
        }
    }
}