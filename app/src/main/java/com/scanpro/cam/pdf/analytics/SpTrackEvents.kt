package com.scanpro.cam.pdf.analytics

import android.content.Context
import android.os.Bundle
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustEvent
import com.google.firebase.analytics.FirebaseAnalytics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 通用埋点规范（§3）事件的统一出口，复用 [SpTracker] 的既有通道：
 * - 出口仅为 `bi` 的事件走 [SpTracker.logBi]（activated / biorganic / 保活 / 频控 / received / ad_blocked 等）；
 * - 出口为 `bi + firebase` 的事件走 [SpTracker.log]（通知 trigger/show/click）；
 * - 留存回传（E24）走 adjust + firebase，不经 bi，见 [retention] / [checkRetention]。
 *
 * 说明：通知类事件（E14/E16/E17）的 Adjust 出口需各事件的 Adjust token，本文件未写死真实值，
 * 待运营提供后接入（§1.2.4 占位，见 [retentionAdjustToken] 同款占位处理）。
 */
object SpTrackEvents {

    // region 归因标记（只出 bi，规范 E23 / E25）

    fun activated() = SpTracker.logBi(SpTracker.ACTIVATED)

    fun biorganic() = SpTracker.logBi(SpTracker.BIORGANIC)

    // endregion

    // region 保活（只出 bi，规范 E20 / E21 / E22）

    fun keepaliveServiceStart(serviceName: String, triggerSource: String) =
        SpTracker.logBi(
            SpTracker.KEEPALIVE_SERVICE_START,
            Bundle().apply {
                putString(SpTracker.PARAM_SERVICE_NAME, serviceName)
                putString(SpTracker.PARAM_TRIGGER_SOURCE, triggerSource)
            },
        )

    fun keepaliveTrigger(triggerType: String) =
        SpTracker.logBi(
            SpTracker.KEEPALIVE_TRIGGER,
            Bundle().apply { putString(SpTracker.PARAM_TRIGGER_TYPE, triggerType) },
        )

    fun keepaliveServiceStop(serviceName: String, aliveMs: Long) =
        SpTracker.logBi(
            SpTracker.KEEPALIVE_SERVICE_STOP,
            Bundle().apply {
                putString(SpTracker.PARAM_SERVICE_NAME, serviceName)
                putLong(SpTracker.PARAM_ALIVE_MS, aliveMs)
            },
        )

    // endregion

    // region 通知（E14/E16/E17 出 bi+adjust+firebase；E15/E18 只出 bi）

    fun notificationTrigger(context: Context, posId: String, triggerSource: String) {
        SpTracker.log(context, SpTracker.NOTIFICATION_TRIGGER, notifyBase(posId, triggerSource))
        AdTrackingHelper.adjustPoint(SpTracker.NOTIFICATION_TRIGGER)
    }

    fun notificationShow(context: Context, posId: String, triggerSource: String) {
        SpTracker.log(context, SpTracker.NOTIFICATION_SHOW, notifyBase(posId, triggerSource))
        AdTrackingHelper.adjustPoint(SpTracker.NOTIFICATION_SHOW)
    }

    fun notificationClick(context: Context, posId: String, triggerSource: String) {
        SpTracker.log(context, SpTracker.NOTIFICATION_CLICK, notifyBase(posId, triggerSource))
        AdTrackingHelper.adjustPoint(SpTracker.NOTIFICATION_CLICK)
    }

    fun notificationReceived(posId: String, msgId: String) =
        SpTracker.logBi(
            SpTracker.NOTIFICATION_RECEIVED,
            Bundle().apply {
                if (posId.isNotBlank()) putString(SpTracker.PARAM_POS_ID, posId)
                if (msgId.isNotBlank()) putString(SpTracker.PARAM_MSG_ID, msgId)
            },
        )

    fun notificationFrequencyLimited(posId: String, limitReason: String) =
        SpTracker.logBi(
            SpTracker.NOTIFICATION_FREQUENCY_LIMITED,
            Bundle().apply {
                putString(SpTracker.PARAM_POS_ID, posId)
                putString(SpTracker.PARAM_LIMIT_REASON, limitReason)
            },
        )

    private fun notifyBase(posId: String, triggerSource: String) = Bundle().apply {
        putString(SpTracker.PARAM_POS_ID, posId)
        putString(SpTracker.PARAM_TRIGGER_SOURCE, triggerSource)
    }

    // endregion

    // region 广告（只出 bi，规范 E12 / E13）

    fun adBlocked(adType: String, posId: String, blockReason: String) =
        SpTracker.logBi(
            SpTracker.AD_BLOCKED,
            Bundle().apply {
                putString(SpTracker.PARAM_AD_TYPE, adType)
                putString(SpTracker.PARAM_POS_ID, posId)
                putString(SpTracker.PARAM_BLOCK_REASON, blockReason)
            },
        )

    fun adShowTimeout(adType: String, posId: String, waitMs: Long) =
        SpTracker.logBi(
            SpTracker.AD_SHOW_TIMEOUT,
            Bundle().apply {
                putString(SpTracker.PARAM_AD_TYPE, adType)
                putString(SpTracker.PARAM_POS_ID, posId)
                putLong(SpTracker.PARAM_WAIT_MS, waitMs)
            },
        )

    // endregion

    // region 拉活（只出 bi，规范 E19）

    fun reactivationLanding(source: String, landingPage: String, posId: String) =
        SpTracker.logBi(
            SpTracker.REACTIVATION_LANDING,
            Bundle().apply {
                putString(SpTracker.PARAM_SOURCE, source)
                putString(SpTracker.PARAM_LANDING_PAGE, landingPage)
                if (posId.isNotBlank()) putString(SpTracker.PARAM_POS_ID, posId)
            },
        )

    // endregion

    // region 留存回传（E24：adjust + firebase，不经 bi）

    /** 冷启动时检查一次：首次安装后第 1/7/14/30 天回访各上报一次（按自然日）。 */
    fun checkRetention(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_RETENTION, Context.MODE_PRIVATE)
        val installMillis = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(0L)
        if (installMillis <= 0L) return
        val installDay = Instant.ofEpochMilli(installMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val days = ChronoUnit.DAYS.between(installDay, LocalDate.now()).toInt()
        val node = when (days) {
            1 -> 1
            7 -> 7
            14 -> 14
            30 -> 30
            else -> return
        }
        val key = "retention_d${node}_reported"
        if (prefs.getBoolean(key, false)) return
        retention(context, node)
        prefs.edit().putBoolean(key, true).apply()
    }

    fun retention(context: Context, day: Int) {
        val event = when (day) {
            1 -> SpTracker.RETENTION_D1
            7 -> SpTracker.RETENTION_D7
            14 -> SpTracker.RETENTION_D14
            30 -> SpTracker.RETENTION_D30
            else -> return
        }
        // Firebase
        runCatching {
            FirebaseAnalytics.getInstance(context.applicationContext).logEvent(event, null)
        }
        // Adjust（token 由产品提供，§6；占位未填时跳过，不硬编码猜测）
        val token = retentionAdjustToken(event) ?: return
        if (token.isBlank() || token.startsWith(PENDING_PREFIX)) return
        runCatching { Adjust.trackEvent(AdjustEvent(token)) }
    }

    private fun retentionAdjustToken(event: String): String? = when (event) {
        SpTracker.RETENTION_D1 -> RETENTION_D1_TOKEN
        SpTracker.RETENTION_D7 -> RETENTION_D7_TOKEN
        SpTracker.RETENTION_D14 -> RETENTION_D14_TOKEN
        SpTracker.RETENTION_D30 -> RETENTION_D30_TOKEN
        else -> null
    }

    // §6：留存回传 Adjust token（由产品提供）。
    private const val PENDING_PREFIX = "【待填】"
    private const val RETENTION_D1_TOKEN = "xkihl3"
    private const val RETENTION_D7_TOKEN = "3wyazn"
    private const val RETENTION_D14_TOKEN = "e1p3ue"
    private const val RETENTION_D30_TOKEN = "bs6t8k"
    private const val PREFS_RETENTION = "sp_retention"

    // endregion
}
