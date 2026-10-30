package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import com.scanpro.cam.pdf.ads.manager.SpBannerAdManager
import com.scanpro.cam.pdf.ads.manager.SpNativeAdManager
import com.scanpro.cam.pdf.ads.manager.SpNativeAdStyle
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.util.AppLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 首页 / 文件 / 返回 几处插屏的共用触发。照抄 Apollo
 * `HomeActivity.checkLoadAdByTab`、`FileListFragment` 打开文件、
 * `BaseActivity.showBackHomeIntAd`。
 *
 * 出处：
 * - `ui/home/HomeActivity.java`（`lastShowAdTime` 60 秒、底栏 `ac_file_int`、类型页 `ac_top_int`）
 * - `ui/home/FileListFragment.java`（打开文件 `ac_check_int`）
 * - `ui/base/BaseActivity.java`（返回 `ac_mainback_int`）
 */
object SpPageAdTriggers {

    /** Apollo `HomeActivity`：距上次插屏展示不足 60 秒不再弹。 */
    private const val TAB_THROTTLE_MS = 60_000L

    private const val KEY_SCENE = "scene"

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
            // 规范 E12：`block_reason` 只能用规定枚举，60 秒节流归到 `interval_not_met`。
            SpTrackEvents.adBlocked(AdTrackingHelper.AdType.INTERSTITIAL, position, "interval_not_met")
            return
        }
        val blocking = SpBlockingLogic.get(activity)
        if (blocking.isShowPartialAdsOnly(position)) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=只展示部分广告")
            return
        }
        if (!blocking.canShowAdByShowCountLimit()) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=达到当日展示上限")
            return
        }
        if (blocking.isVPNBlock()) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=VPN拦截")
            return
        }
        if (!blocking.canShowAllAd()) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=总闸拦截")
            return
        }
        val hub = SpAdsHub.getOrNull() ?: run {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=广告模块未就绪")
            return
        }
        SpTracker.log(
            activity,
            SpTracker.SHARK_AD_CHANCE,
            Bundle().apply { putString(KEY_SCENE, position) },
        )
        val cached = SpAdCache.getInterstitialAd(position, hub.config(), blocking)
        val manager = hub.fullScreen(activity)
        if (cached == null) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=广告尚未就绪")
            manager.checkAndLoadAd(position, position)
            return
        }
        if (activity.isFinishing || activity.isDestroyed) return
        manager.showAd(
            ad = cached.ad,
            sessionId = cached.sessionId,
            scene = position,
            reloadPosition = position,
            onResult = { showed, _ ->
                if (showed) lastShowAdTime = System.currentTimeMillis()
            },
        )
    }

    /**
     * 扫描入口：同样走 `ac_file_int` 的 60 秒节流。
     * 有缓存就先展示，关掉（或没展示）再进扫描页，避免壳页已经跳走。
     */
    fun showFileIntThen(activity: Activity, then: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            AppLog.ads.i("跳过广告展示 位置=${SpAdPosition.AC_FILE_INT} 原因=页面已销毁")
            then()
            return
        }
        if (System.currentTimeMillis() - lastShowAdTime <= TAB_THROTTLE_MS) {
            AppLog.ads.i("跳过广告展示 位置=${SpAdPosition.AC_FILE_INT} 原因=距上次展示不足60秒")
            SpTrackEvents.adBlocked(
                AdTrackingHelper.AdType.INTERSTITIAL,
                SpAdPosition.AC_FILE_INT,
                "interval_not_met",
            )
            then()
            return
        }
        val blocking = SpBlockingLogic.get(activity)
        val position = SpAdPosition.AC_FILE_INT
        if (blocking.isShowPartialAdsOnly(position)) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=只展示部分广告")
            then()
            return
        }
        if (!blocking.canShowAdByShowCountLimit()) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=达到当日展示上限")
            then()
            return
        }
        if (blocking.isVPNBlock()) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=VPN拦截")
            then()
            return
        }
        if (!blocking.canShowAllAd()) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=总闸拦截")
            then()
            return
        }
        val hub = SpAdsHub.getOrNull()
        val manager = hub?.fullScreen(activity)
        if (hub == null || manager == null) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=广告模块未就绪")
            then()
            return
        }
        SpTracker.log(
            activity,
            SpTracker.SHARK_AD_CHANCE,
            Bundle().apply { putString(KEY_SCENE, SpAdPosition.AC_FILE_INT) },
        )
        val cached = SpAdCache.getInterstitialAd(SpAdPosition.AC_FILE_INT, hub.config(), blocking)
        if (cached == null) {
            AppLog.ads.i("跳过广告展示 位置=$position 原因=广告尚未就绪")
            manager.checkAndLoadAd(SpAdPosition.AC_FILE_INT, SpAdPosition.AC_FILE_INT)
            then()
            return
        }
        val once = AtomicBoolean(false)
        val finish = { if (once.compareAndSet(false, true)) then() }
        manager.showAd(
            ad = cached.ad,
            sessionId = cached.sessionId,
            scene = SpAdPosition.AC_FILE_INT,
            reloadPosition = SpAdPosition.AC_FILE_INT,
            onResult = { showed, dismissed ->
                if (showed) lastShowAdTime = System.currentTimeMillis()
                if (!showed || dismissed) finish()
            },
        )
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

    fun bindRecentBanner(activity: Activity, container: ViewGroup): SpBannerAdManager? {
        val hub = SpAdsHub.getOrNull() ?: return null
        return hub.banner(activity, container).also {
            it.loadBannerAd(SpAdPosition.AC_RECENT_BANNER)
        }
    }

    fun bindSettingNative(activity: Activity, container: ViewGroup): SpNativeAdManager? {
        val hub = SpAdsHub.getOrNull() ?: return null
        return hub.native(activity, container).also {
            it.checkLoadAndShowAd(SpAdPosition.AC_SETTING_NAT, SpNativeAdStyle.big)
        }
    }
}
