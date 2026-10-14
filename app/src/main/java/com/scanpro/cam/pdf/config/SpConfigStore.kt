package com.scanpro.cam.pdf.config

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
 * 中台 `data` 的按块解析。网络、缓存、节流不在这里。
 *
 * 缺项回落本地默认；未列出 key 忽略；单项格式错只丢该项。
 * 值既可以是嵌套对象，也可以是 JSON 字符串。
 */
class SpConfigStore {

    data class BlockingPatch(
        val acAdsLimit: Int? = null,
        val globalBlockSwitch: Int? = null,
        val googlePlayBlock: Int? = null,
        val vpnConfigSwitch: Int? = null,
        val virtualBlockSwitch: Int? = null,
    )

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
        base.byPosition.forEach { (position, list) ->
            out.putIfAbsent(position, list)
        }
        return SpAdConfig(out)
    }

    fun natConfig(payload: JSONObject?): SpNatConfig {
        val json = valueAsObject(payload, SpConfigWire.KEY_NAT_CONFIG) ?: return SpNatConfig()
        val base = SpNatConfig()
        return base.copy(
            switchOpen = json.optIntOrNull(SpConfigWire.KEY_NAT_SWITCH_OPEN) ?: base.switchOpen,
        )
    }

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
                ?: json.optIntOrNull(SpConfigWire.KEY_AC_U_LIMIT_LEGACY)
                ?: base.acULimit,
            acA = json.optIntOrNull(SpConfigWire.KEY_AC_A) ?: base.acA,
            acALimit = json.optIntOrNull(SpConfigWire.KEY_AC_A_LIMIT) ?: base.acALimit,
        )
    }

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

    fun mediaConfig(payload: JSONObject?): SpMediaConfig {
        val json = valueAsObject(payload, SpConfigWire.KEY_MEDIA_CONFIG) ?: return SpMediaConfig()
        val base = SpMediaConfig()
        return base.copy(
            switch = json.optIntOrNull(SpConfigWire.KEY_MEDIA_SWITCH) ?: base.switch,
            intervalTime = json.optIntOrNull(SpConfigWire.KEY_MEDIA_INTERVAL_TIME)
                ?.coerceAtLeast(0) ?: base.intervalTime,
        )
    }

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
     * 5 个总闸 key。缺失保持当前值（返回 null）；解析异常按 Apollo 回落：
     * ads_limit=30、global=1、google_play=0、vpn=1、virtual=1。
     */
    fun blockingConfig(payload: JSONObject?): BlockingPatch {
        if (payload == null) return BlockingPatch()
        return BlockingPatch(
            acAdsLimit = parseSwitch(
                payload,
                SpConfigWire.KEY_ADS_LIMIT,
                SpConfigWire.KEY_AC_ADS_LIMIT,
                onError = 30,
            ),
            globalBlockSwitch = parseSwitch(
                payload,
                SpConfigWire.KEY_GLOBAL_BLOCK,
                SpConfigWire.KEY_GLOBAL_BLOCK_SWITCH,
                onError = 1,
            ),
            googlePlayBlock = parseSwitch(
                payload,
                SpConfigWire.KEY_GOOGLE_PLAY_BLOCK,
                SpConfigWire.KEY_GOOGLE_PLAY_BLOCK,
                onError = 0,
            ),
            vpnConfigSwitch = parseSwitch(
                payload,
                SpConfigWire.KEY_VPN_CONFIG_SWITCH,
                SpConfigWire.KEY_VPN_CONFIG_SWITCH,
                onError = 1,
            ),
            virtualBlockSwitch = parseSwitch(
                payload,
                SpConfigWire.KEY_VIRTUAL_BLOCK_SWITCH,
                SpConfigWire.KEY_VIRTUAL_BLOCK_SWITCH,
                onError = 1,
            ),
        )
    }

    /**
     * `data` 最外层、与广告配置平级：`facebook_app_id` / `client_token` / `isAcquiredUser`。
     * 缺字段按空串和 false，不抛。
     */
    fun dispatchMeta(payload: JSONObject?): DispatchMeta {
        if (payload == null) return DispatchMeta()
        return DispatchMeta(
            facebookAppId = payload.optString(SpConfigWire.KEY_FACEBOOK_APP_ID).trim(),
            clientToken = payload.optString(SpConfigWire.KEY_FACEBOOK_CLIENT_TOKEN).trim(),
            isAcquiredUser = payload.optAcquired(),
        )
    }

    fun hasPosition(config: SpAdConfig, position: String): Boolean =
        config.adInfoList(position)?.isNotEmpty() == true

    private fun parseSwitch(
        payload: JSONObject,
        containerKey: String,
        fieldKey: String,
        onError: Int,
    ): Int? {
        if (!payload.has(containerKey) || payload.isNull(containerKey)) return null
        return try {
            val obj = valueAsObject(payload, containerKey) ?: error("not object")
            obj.optIntOrNull(fieldKey)
        } catch (error: Throwable) {
            Log.w(TAG, "blocking key $containerKey parse failed, fallback=$onError", error)
            onError
        }
    }

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
                adTimeLimit = json.optInt(SpConfigWire.KEY_AD_TIME_LIMIT, 0),
            )
        }
        return out
    }

    data class DispatchMeta(
        val facebookAppId: String = "",
        val clientToken: String = "",
        val isAcquiredUser: Boolean = false,
    )

    companion object {
        private const val TAG = "SpConfig"
    }
}

private fun JSONObject.optAcquired(): Boolean {
    if (!has(SpConfigWire.KEY_IS_ACQUIRED_USER) || isNull(SpConfigWire.KEY_IS_ACQUIRED_USER)) {
        return false
    }
    return when (val raw = opt(SpConfigWire.KEY_IS_ACQUIRED_USER)) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> raw.equals("true", ignoreCase = true) || raw == "1"
        else -> false
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null
