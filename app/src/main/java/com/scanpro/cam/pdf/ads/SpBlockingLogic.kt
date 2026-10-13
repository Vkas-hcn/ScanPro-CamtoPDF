package com.scanpro.cam.pdf.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 广告与通知的「总闸」。照抄 ApolloPDF `filter/BlockingLogic.java` 的判据，
 * **去掉买量/设备/用户维度**（`ReferFilter` / `UserFilter` / `DeviceFilter` / `AdFilterKt`），
 * 这些按要求不迁。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/filter/BlockingLogic.java`
 *
 * 去掉维度后的等价语义（原文里每个方法都是 `switch == 0 放行 → 特殊用户拦 → 兜底放行`，
 * 掐掉中间那段后只剩首尾）：
 */
class SpBlockingLogic private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** `global_block_switch`，默认 1（原 Apollo 静态默认值）。 */
    var globalBlockSwitch: Int = 1
    /** `google_play_block`，默认 0。 */
    var googlePlayBlock: Int = 0
    /** `virtual_block_switch`，默认 1。 */
    var virtualBlockSwitch: Int = 1
    /** `ac_ads_limit`：当日广告展示上限，默认 50。 */
    var acAdsLimit: Int = 50
    /** `vpn_config_switch`：VPN 拦截开关。 */
    var vpnConfigSwitch: Int = 0

    // region 广告

    /** 当日展示数 < [acAdsLimit]。跨天先归零。 */
    fun canShowAdByShowCountLimit(): Boolean {
        if (isToday(prefs.getLong(KEY_AD_SHOW_TIME, 0L))) {
            return prefs.getInt(KEY_AD_SHOW_COUNT, 0) < acAdsLimit
        }
        prefs.edit().putInt(KEY_AD_SHOW_COUNT, 0).apply()
        return true
    }

    /** 展示成功后 +1，并记时间。 */
    fun updateAdShowCount() {
        if (isToday(prefs.getLong(KEY_AD_SHOW_TIME, 0L))) {
            prefs.edit().putInt(KEY_AD_SHOW_COUNT, prefs.getInt(KEY_AD_SHOW_COUNT, 0) + 1).apply()
        } else {
            prefs.edit().putInt(KEY_AD_SHOW_COUNT, 1).apply()
        }
        prefs.edit().putLong(KEY_AD_SHOW_TIME, System.currentTimeMillis()).apply()
    }

    /** 关总闸时：放行；`google_play_block != 1` 时：放行。 */
    fun canShowAllAd(): Boolean = globalBlockSwitch == 0 || googlePlayBlock != 1

    /**
     * 只在「保量位」展示。原逻辑是「位置在 [PART_SHOW_POSITIONS] 里 **且** 用户是特殊/自然量」。
     * 用户维度不迁，故恒为 false（等于所有位置都按正常投放）。
     */
    fun isShowPartialAdsOnly(position: String): Boolean = false

    /** 关闭键被点时的沉浸模式（虚拟键隐藏）。 */
    fun isHideVirtualButtons(): Boolean = virtualBlockSwitch != 0

    /** 原生关闭按钮是否显示。去掉用户维度后只剩总闸。 */
    fun isShowNativeAdCloseButton(): Boolean = globalBlockSwitch == 0

    /** 评分弹窗是否显示。 */
    fun isShowRatingDialog(): Boolean = globalBlockSwitch == 0 || googlePlayBlock != 1

    /** VPN 探测 + 开关打开时拦截广告。 */
    fun isVPNBlock(): Boolean = vpnConfigSwitch == 1 && isVpnActive()

    private fun isVpnActive(): Boolean = runCatching {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }.getOrDefault(false)
    // endregion

    // region 通知（去掉用户维度后全部等价）

    fun canRefreshMediaNotification(): Boolean = canSendSystemNotification()

    fun canRefreshSystemNotification(): Boolean = canSendSystemNotification()

    fun canRequestNotificationPermission(): Boolean = canSendSystemNotification()

    fun canRequestOverlayPermission(): Boolean = canSendSystemNotification()

    fun canSendFloatingWindowNotify(): Boolean = canRequestOverlayPermission()

    fun canSendMediaNotification(): Boolean = canSendSystemNotification()

    /** 关总闸放行；否则 `google_play_block != 1` 放行。 */
    fun canSendSystemNotification(): Boolean = globalBlockSwitch == 0 || googlePlayBlock != 1

    /** 前台服务能不能起。判据同 [canSendSystemNotification]。 */
    fun canStartForegroundService(): Boolean = globalBlockSwitch == 0 || googlePlayBlock != 1
    // endregion

    private fun isToday(ms: Long): Boolean {
        if (ms <= 0L) return false
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        return fmt.format(Date(ms)) == fmt.format(Date())
    }

    companion object {
        private const val PREFS = "sp_blocking_logic"
        private const val KEY_AD_SHOW_COUNT = "ad_show_count"
        private const val KEY_AD_SHOW_TIME = "ad_show_time"

        /**
         * Apollo 的「保量位」清单，原样保留以备将来接回用户维度用。
         * 出处：`BlockingLogic.partShowAd`。
         */
        val PART_SHOW_POSITIONS: List<String> = listOf(
            SpAdPosition.AC_LANGUAGE_INT,
            SpAdPosition.AC_LANGUAGE_NAT,
            SpAdPosition.AC_GUIDE_NAT,
            SpAdPosition.AC_GUIDE_INT,
            SpAdPosition.AC_MAIN_NAT,
            SpAdPosition.AC_TOP_INT,
            SpAdPosition.AC_FILE_INT,
            SpAdPosition.AC_CHECK_INT,
            SpAdPosition.AC_MAIN_BACK_INT,
            SpAdPosition.AC_RECENT_NAT,
            SpAdPosition.AC_RECENT_BANNER,
            SpAdPosition.AC_SETTING_NAT,
            SpAdPosition.AC_UNINSTALL1_INT,
            SpAdPosition.AC_UNINSTALL1_NAT,
            SpAdPosition.AC_UNINSTALL2_INT,
            SpAdPosition.AC_UNINSTALL2_NAT,
        )

        @Volatile
        private var instance: SpBlockingLogic? = null

        fun get(context: Context): SpBlockingLogic =
            instance ?: synchronized(this) {
                instance ?: SpBlockingLogic(context).also { instance = it }
            }

        fun getOrNull(): SpBlockingLogic? = instance
    }
}
