package com.scanpro.cam.pdf.notice

import android.content.Context
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import java.time.LocalTime

/**
 * 通知频控。逐分支照抄 ApolloPDF `notice/NotifyFilter.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/notice/NotifyFilter.java`
 *
 * 两套判据并存，按场景分流（这是 Apollo 的历史包袱，不是笔误）：
 *
 * | 场景 | 判据 | 配额来源 |
 * |---|---|---|
 * | `scheduled` / `unlock` / `alarm_clock` | [oldSceneFilter] | `acpop_config`（`ac_t/ac_u/ac_a` + `*_limit`） |
 * | `home_click` / `recent_click` / `no_page_exist` / `ad_click` | [newSceneFilter] | `new_config`（`ac_home/ac_recent/ac_exit/ac_adclick` + `*_limit`） |
 *
 * 两套都是「距上次 ≥ 冷却分钟 且 当日次数 < 上限」；`acpop_config` 那套额外看免打扰时段
 * （`nopop_start` ~ `nopop_end`）和前后台。
 */
internal object SpNotifyFilter {

    private const val PREFS = "sp_notify_state"

    private const val KEY_SCHEDULED_LAST = "sp_scheduled_last_time"
    private const val KEY_SCHEDULED_COUNT = "sp_scheduled_send_count"
    private const val KEY_UNLOCK_LAST = "sp_unlock_last_time"
    private const val KEY_UNLOCK_COUNT = "sp_unlock_send_count"
    private const val KEY_CLOCK_LAST = "sp_alarm_clock_last_time"
    private const val KEY_CLOCK_COUNT = "sp_alarm_clock_send_count"
    private const val KEY_HOME_LAST = "sp_home_last_time"
    private const val KEY_HOME_COUNT = "sp_home_send_count"
    private const val KEY_RECENT_LAST = "sp_recent_last_time"
    private const val KEY_RECENT_COUNT = "sp_recent_send_count"
    private const val KEY_NO_PAGE_LAST = "sp_no_page_last_time"
    private const val KEY_NO_PAGE_COUNT = "sp_no_page_send_count"
    private const val KEY_AD_LAST = "sp_ad_last_time"
    private const val KEY_AD_COUNT = "sp_ad_send_count"

    private const val MINUTE_MS = 60_000L

    // region 对外判据

    /** Apollo `sendNotificationFilter(scene)`。 */
    fun sendNotificationFilter(context: Context, scene: SpNotifyScene): Boolean = when (scene) {
        SpNotifyScene.SCHEDULED, SpNotifyScene.UNLOCK, SpNotifyScene.ALARM_CLOCK ->
            oldSceneFilter(context, scene)

        SpNotifyScene.HOME_CLICK, SpNotifyScene.RECENT_CLICK,
        SpNotifyScene.NO_PAGE_EXIST, SpNotifyScene.AD_CLICK,
        -> newSceneFilter(context, scene)
    }

    /** Apollo `canSendSysNotification(context, scene)`：有通知权限 + 总闸放行。**不含频控**。 */
    fun canSendSysNotification(context: Context, scene: SpNotifyScene): Boolean {
        val allowed = SpNotifyPermit.isHaveNotifyPermission(context) &&
            SpBlockingLogic.get(context).canSendSystemNotification()
        SpNotifyUtils.log("canSendSysNotification scene=$scene result=$allowed")
        return allowed
    }

    /** Apollo `canSendMediaNotification(scene)`：总闸放行 **且** 频控通过。 */
    fun canSendMediaNotification(context: Context, scene: SpNotifyScene): Boolean {
        val allowed = SpBlockingLogic.get(context).canSendMediaNotification() &&
            sendNotificationFilter(context, scene)
        SpNotifyUtils.log("canSendMediaNotification scene=$scene result=$allowed")
        return allowed
    }

    /** Apollo `updateSendCountAndLastTime(scene)`：发成功才 +1 并记时间。 */
    fun updateSendCountAndLastTime(context: Context, scene: SpNotifyScene) {
        val prefs = prefs(context)
        val (countKey, lastKey) = keys(scene) ?: return
        prefs.edit()
            .putInt(countKey, prefs.getInt(countKey, 0) + 1)
            .putLong(lastKey, System.currentTimeMillis())
            .apply()
    }
    // endregion

    /** Apollo `newNotifySceneFilter`。 */
    private fun newSceneFilter(context: Context, scene: SpNotifyScene): Boolean {
        if (!SpNotifyEnv.hasRemoteConfig) return false
        val config = SpNotifyEnv.newConfig
        if (config.acOn == 0) return false

        val prefs = prefs(context)
        val quota = when (scene) {
            SpNotifyScene.NO_PAGE_EXIST -> Quota(
                cooldownMinutes = config.acExit,
                limit = config.acExitLimit,
                countKey = KEY_NO_PAGE_COUNT,
                lastKey = KEY_NO_PAGE_LAST,
            )

            SpNotifyScene.HOME_CLICK -> Quota(
                cooldownMinutes = config.acHome,
                limit = config.acHomeLimit,
                countKey = KEY_HOME_COUNT,
                lastKey = KEY_HOME_LAST,
            )

            SpNotifyScene.RECENT_CLICK -> Quota(
                cooldownMinutes = config.acRecent,
                limit = config.acRecentLimit,
                countKey = KEY_RECENT_COUNT,
                lastKey = KEY_RECENT_LAST,
            )

            SpNotifyScene.AD_CLICK -> Quota(
                cooldownMinutes = config.acAdClick,
                limit = config.acAdClickLimit,
                countKey = KEY_AD_COUNT,
                lastKey = KEY_AD_LAST,
            )

            else -> return false
        }

        val last = prefs.getLong(quota.lastKey, 0L)
        if (System.currentTimeMillis() <= last + quota.cooldownMinutes * MINUTE_MS) return false
        if (!SpNotifyUtils.isToday(last)) resetNewCounters(prefs)
        return quota.limit > prefs.getInt(quota.countKey, 0)
    }

    /** Apollo `oldNotifySceneFilter`。 */
    private fun oldSceneFilter(context: Context, scene: SpNotifyScene): Boolean {
        val config = SpNotifyEnv.acPopConfig

        val hour = LocalTime.now().hour
        val from = minOf(config.noPopStart, config.noPopEnd)
        val to = maxOf(config.noPopStart, config.noPopEnd)
        // 只在 [from, to) 里发；范围外（默认 24/6 → 凌晨 0~6 点）静音
        if (hour < from || hour >= to) return false
        if (config.acOn == 0 || SpNotifyEnv.isForeground()) return false

        val prefs = prefs(context)
        val quota = when (scene) {
            SpNotifyScene.SCHEDULED -> Quota(
                cooldownMinutes = config.acT,
                limit = config.acTLimit,
                countKey = KEY_SCHEDULED_COUNT,
                lastKey = KEY_SCHEDULED_LAST,
            )

            SpNotifyScene.UNLOCK -> Quota(
                cooldownMinutes = config.acU,
                limit = config.acULimit,
                countKey = KEY_UNLOCK_COUNT,
                lastKey = KEY_UNLOCK_LAST,
            )

            SpNotifyScene.ALARM_CLOCK -> Quota(
                cooldownMinutes = config.acA,
                limit = config.acALimit,
                countKey = KEY_CLOCK_COUNT,
                lastKey = KEY_CLOCK_LAST,
            )

            else -> return false
        }

        val last = prefs.getLong(quota.lastKey, 0L)
        if (System.currentTimeMillis() <= last + quota.cooldownMinutes * MINUTE_MS) return false
        if (!SpNotifyUtils.isToday(last)) {
            prefs.edit()
                .putInt(KEY_SCHEDULED_COUNT, 0)
                .putInt(KEY_UNLOCK_COUNT, 0)
                .putInt(KEY_CLOCK_COUNT, 0)
                .apply()
        }
        return quota.limit > prefs.getInt(quota.countKey, 0)
    }

    /** Apollo：跨天时把四个 `new_config` 计数一起清零。 */
    private fun resetNewCounters(prefs: android.content.SharedPreferences) {
        prefs.edit()
            .putInt(KEY_HOME_COUNT, 0)
            .putInt(KEY_RECENT_COUNT, 0)
            .putInt(KEY_AD_COUNT, 0)
            .putInt(KEY_NO_PAGE_COUNT, 0)
            .apply()
    }

    private fun keys(scene: SpNotifyScene): Pair<String, String>? = when (scene) {
        SpNotifyScene.SCHEDULED -> KEY_SCHEDULED_COUNT to KEY_SCHEDULED_LAST
        SpNotifyScene.UNLOCK -> KEY_UNLOCK_COUNT to KEY_UNLOCK_LAST
        SpNotifyScene.ALARM_CLOCK -> KEY_CLOCK_COUNT to KEY_CLOCK_LAST
        SpNotifyScene.HOME_CLICK -> KEY_HOME_COUNT to KEY_HOME_LAST
        SpNotifyScene.RECENT_CLICK -> KEY_RECENT_COUNT to KEY_RECENT_LAST
        SpNotifyScene.NO_PAGE_EXIST -> KEY_NO_PAGE_COUNT to KEY_NO_PAGE_LAST
        SpNotifyScene.AD_CLICK -> KEY_AD_COUNT to KEY_AD_LAST
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private data class Quota(
        val cooldownMinutes: Int,
        val limit: Int,
        val countKey: String,
        val lastKey: String,
    )
}
