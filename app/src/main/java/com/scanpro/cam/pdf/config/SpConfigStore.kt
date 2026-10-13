package com.scanpro.cam.pdf.config

import android.content.Context
import android.util.Log
import com.scanpro.cam.pdf.ads.SpAdConfig
import com.scanpro.cam.pdf.ads.SpAdInfo
import com.scanpro.cam.pdf.ads.SpNatConfig
import com.scanpro.cam.pdf.notice.SpAcPopConfig
import com.scanpro.cam.pdf.notice.SpMediaConfig
import com.scanpro.cam.pdf.notice.SpNewConfig
import com.scanpro.cam.pdf.notice.SpPopRefresh
import com.scanpro.cam.pdf.notice.SpWinPopConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * 中台配置的本地存储与解析。
 *
 * 协议（`config接入PRD.md`）：
 * - 冷启动判断：距上次请求 ≥ [MIN_REFRESH_INTERVAL_MS] 才请求一次
 * - 活在后台：同样 ≥ [MIN_REFRESH_INTERVAL_MS] 更新一次
 * - 拿到就持久化覆盖；没拿到用上一份缓存
 * - 全程不抛异常；接口未配置时直接返回缓存
 *
 * 解析策略：**key 与 ApolloPDF 的远端配置对齐**（见 [SpConfigWire]），缺项回落本地默认，
 * 未列出 key 忽略，单项格式错只丢该项。
 */
class SpConfigStore(
    context: Context,
    private val remote: SpConfigRemoteSource = SpConfigRemoteSource(),
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 是否到了该刷新的时间。冷启动 / 回到前台都可以问一句。 */
    fun shouldRefresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        SpConfigWire.isConfigured() && nowMs - lastFetchMs() >= MIN_REFRESH_INTERVAL_MS

    /**
     * 到点才请求；无论成功失败都返回「当前可用的配置 JSON」：远端优先，其次缓存，最后 null。
     * 请求失败不清缓存、不抛异常。
     */
    suspend fun refreshIfStale(
        request: SpConfigRequest,
        nowMs: Long = System.currentTimeMillis(),
    ): JSONObject? {
        if (!SpConfigWire.isConfigured()) return cached()
        if (!shouldRefresh(nowMs)) return cached()
        val fetched = remote.fetch(request)
        if (fetched != null) {
            prefs.edit()
                .putString(KEY_PAYLOAD, fetched.toString())
                .putLong(KEY_FETCHED_AT, nowMs)
                .apply()
            Log.i(TAG, "config refreshed keys=${fetched.keys().asSequence().toList()}")
            return fetched
        }
        Log.i(TAG, "config refresh failed — keep cache")
        return cached()
    }

    fun cached(): JSONObject? {
        val raw = prefs.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching { JSONObject(raw) }
            .onFailure { Log.w(TAG, "cached payload corrupted — dropping", it) }
            .getOrNull()
    }

    fun lastFetchMs(): Long = prefs.getLong(KEY_FETCHED_AT, 0L)

    // region 解析

    /**
     * 把 `ac_ad_config` 解析成 [SpAdConfig]。[base] 是本地的兜底。
     *
     * 值既可能是 JSON 对象（中台直接内联），也可能是 JSON 字符串（Apollo 从 Firebase
     * Remote Config 取的形态）。两种都认，见 [valueAsObject]。
     */
    fun adConfig(payload: JSONObject?, base: SpAdConfig = SpAdConfig()): SpAdConfig {
        val root = valueAsObject(payload, SpConfigWire.KEY_ADS) ?: return base
        val out = LinkedHashMap<String, List<SpAdInfo>>()
        root.keys().forEach { position ->
            if (!SpConfigWire.isKnownPosition(position)) {
                Log.w(TAG, "unknown ad position in config: $position")
                return@forEach
            }
            val list = parseAdInfoList(root.optJSONArray(position))
            if (list.isNotEmpty()) out[position] = list
        }
        // 兜底：本地已有、远端这次没下发的位，沿用本地默认
        base.byPosition.forEach { (position, list) ->
            out.putIfAbsent(position, list)
        }
        return SpAdConfig(out)
    }

    /** `ac_nat_config` → [SpNatConfig]。 */
    fun natConfig(payload: JSONObject?): SpNatConfig {
        val json = valueAsObject(payload, SpConfigWire.KEY_NAT_CONFIG) ?: return SpNatConfig()
        val base = SpNatConfig()
        return base.copy(
            switchOpen = json.optIntOrNull(SpConfigWire.KEY_NAT_SWITCH_OPEN) ?: base.switchOpen,
            jumpPercent = json.optIntOrNull(SpConfigWire.KEY_NAT_JUMP_PERCENT)
                ?.coerceIn(0, 100) ?: base.jumpPercent,
        )
    }

    /** `new_config` → [SpNewConfig]。 */
    fun newConfig(payload: JSONObject?): SpNewConfig {
        val json = valueAsObject(payload, SpConfigWire.KEY_NEW_CONFIG) ?: return SpNewConfig()
        val base = SpNewConfig()
        return base.copy(
            acOn = json.optIntOrNull(SpConfigWire.KEY_AC_ON) ?: base.acOn,
            acHome = json.optIntOrNull(SpConfigWire.KEY_AC_HOME) ?: base.acHome,
            acHomeLimit = json.optIntOrNull(SpConfigWire.KEY_AC_HOME_LIMIT) ?: base.acHomeLimit,
            homeDelay = json.optIntOrNull(SpConfigWire.KEY_HOME_DELAY) ?: base.homeDelay,
            acExit = json.optIntOrNull(SpConfigWire.KEY_AC_EXIT) ?: base.acExit,
            acExitLimit = json.optIntOrNull(SpConfigWire.KEY_AC_EXIT_LIMIT) ?: base.acExitLimit,
            exitDelay = json.optIntOrNull(SpConfigWire.KEY_EXIT_DELAY) ?: base.exitDelay,
            acRecent = json.optIntOrNull(SpConfigWire.KEY_AC_RECENT) ?: base.acRecent,
            acRecentLimit = json.optIntOrNull(SpConfigWire.KEY_AC_RECENT_LIMIT) ?: base.acRecentLimit,
            recentDelay = json.optIntOrNull(SpConfigWire.KEY_RECENT_DELAY) ?: base.recentDelay,
            acAdClick = json.optIntOrNull(SpConfigWire.KEY_AC_ADCLICK) ?: base.acAdClick,
            acAdClickLimit = json.optIntOrNull(SpConfigWire.KEY_AC_ADCLICK_LIMIT)
                ?: base.acAdClickLimit,
            adClickDelay = json.optIntOrNull(SpConfigWire.KEY_ADCLICK_DELAY) ?: base.adClickDelay,
        )
    }

    /** `acpop_config` → [SpAcPopConfig]。同时认 [SpConfigWire.KEY_NAT_CONFIG] 之外的小写 L 变体。 */
    fun acPopConfig(payload: JSONObject?): SpAcPopConfig {
        val json = valueAsObject(payload, SpConfigWire.KEY_AC_POP_CONFIG) ?: return SpAcPopConfig()
        val base = SpAcPopConfig()
        return base.copy(
            noPopStart = json.optIntOrNull(SpConfigWire.KEY_NO_POP_START) ?: base.noPopStart,
            noPopEnd = json.optIntOrNull(SpConfigWire.KEY_NO_POP_END) ?: base.noPopEnd,
            acOn = json.optIntOrNull(SpConfigWire.KEY_AC_ON) ?: base.acOn,
            acT = json.optIntOrNull(SpConfigWire.KEY_AC_T) ?: base.acT,
            acTLimit = json.optIntOrNull(SpConfigWire.KEY_AC_T_LIMIT) ?: base.acTLimit,
            acU = json.optIntOrNull(SpConfigWire.KEY_AC_U) ?: base.acU,
            acULimit = json.optIntOrNull(SpConfigWire.KEY_AC_U_LIMIT)
                ?: json.optIntOrNull("ac_u_limit")
                ?: base.acULimit,
            acA = json.optIntOrNull(SpConfigWire.KEY_AC_A) ?: base.acA,
            acALimit = json.optIntOrNull(SpConfigWire.KEY_AC_A_LIMIT) ?: base.acALimit,
        )
    }

    /** `pop_refresh` → [SpPopRefresh]。 */
    fun popRefresh(payload: JSONObject?): SpPopRefresh {
        val json = valueAsObject(payload, SpConfigWire.KEY_POP_REFRESH) ?: return SpPopRefresh()
        val base = SpPopRefresh()
        return base.copy(
            popRefreshSwitch = json.optIntOrNull(SpConfigWire.KEY_POP_REFRESH_SWITCH)
                ?: base.popRefreshSwitch,
            times = json.optIntOrNull(SpConfigWire.KEY_POP_REFRESH_TIMES)
                ?.coerceAtLeast(0) ?: base.times,
            interval = json.optIntOrNull(SpConfigWire.KEY_POP_REFRESH_INTERVAL)
                ?.coerceAtLeast(1) ?: base.interval,
        )
    }

    /** `media_config` → [SpMediaConfig]。 */
    fun mediaConfig(payload: JSONObject?): SpMediaConfig {
        val json = valueAsObject(payload, SpConfigWire.KEY_MEDIA_CONFIG) ?: return SpMediaConfig()
        val base = SpMediaConfig()
        return base.copy(
            switch = json.optIntOrNull(SpConfigWire.KEY_MEDIA_SWITCH) ?: base.switch,
            intervalTime = json.optIntOrNull(SpConfigWire.KEY_MEDIA_INTERVAL_TIME)
                ?.coerceAtLeast(0) ?: base.intervalTime,
        )
    }

    /** `win_pop_config` → [SpWinPopConfig]。 */
    fun winPopConfig(payload: JSONObject?): SpWinPopConfig {
        val json = valueAsObject(payload, SpConfigWire.KEY_WIN_POP_CONFIG) ?: return SpWinPopConfig()
        val base = SpWinPopConfig()
        return base.copy(
            firstInterval = json.optIntOrNull(SpConfigWire.KEY_WIN_POP_FIRST_INTERVAL)
                ?: base.firstInterval,
            jumpPercent = json.optIntOrNull(SpConfigWire.KEY_NAT_JUMP_PERCENT)
                ?.coerceIn(0, 100) ?: base.jumpPercent,
            winPopJumpSwitch = json.optIntOrNull(SpConfigWire.KEY_WIN_POP_JUMP_SWITCH)
                ?: base.winPopJumpSwitch,
        )
    }

    /**
     * Apollo 的配置值是「JSON 字符串」（Firebase Remote Config 的限制），中台可能直接给
     * 嵌套对象。两种都认：先试对象，再试字符串。
     */
    private fun valueAsObject(payload: JSONObject?, key: String): JSONObject? {
        payload ?: return null
        payload.optJSONObject(key)?.let { return it }
        val raw = payload.optString(key).trim()
        if (raw.isEmpty()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun parseAdInfoList(array: JSONArray?): List<SpAdInfo> {
        if (array == null) return emptyList()
        val out = ArrayList<SpAdInfo>(array.length())
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            val unitId = json.optString(SpConfigWire.KEY_AD_UNIT_ID).trim()
            val type = json.optString(SpConfigWire.KEY_AD_TYPE).trim()
            if (unitId.isEmpty() || type.isEmpty()) continue
            out += SpAdInfo(
                adUnitId = unitId,
                adPlatform = json.optString(SpConfigWire.KEY_AD_PLATFORM).trim(),
                adType = type,
                // 未给 TTL 时给 0，等于「立刻过期」，避免拿旧创意无限复用
                adTimeLimit = json.optInt(SpConfigWire.KEY_AD_TIME_LIMIT, 0),
            )
        }
        return out
    }
    // endregion

    /** 某位置是否已配好可用单元。 */
    fun hasPosition(config: SpAdConfig, position: String): Boolean =
        config.adInfoList(position)?.isNotEmpty() == true

    companion object {
        private const val TAG = "SpConfig"
        private const val PREFS = "sp_remote_config"
        private const val KEY_PAYLOAD = "payload_json"
        private const val KEY_FETCHED_AT = "fetched_at_ms"

        /** 两次请求的最小间隔：3 小时（`config接入PRD.md`）。 */
        const val MIN_REFRESH_INTERVAL_MS = 3 * 60 * 60 * 1_000L
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null
