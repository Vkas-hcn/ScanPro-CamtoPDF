package com.scanpro.cam.pdf.config

import android.content.Context
import android.os.SystemClock
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

enum class SpConfigOrigin {
    REMOTE,
    CACHE,
}

sealed interface SpConfigLoadResult {
    data class Available(
        val payload: JSONObject,
        val origin: SpConfigOrigin,
    ) : SpConfigLoadResult

    data class Unavailable(val reason: String) : SpConfigLoadResult
}

/**
 * 远端 → 最后一份 SharedPreferences 缓存 → Unavailable。
 * 一轮最多 3 次，每次 20 秒，次数之间不等待。
 *
 * 日志走 [AppLog.network]：成功只报一条结果；失败把「第几次、原因、耗时、
 * 有没有缓存兜底」写清楚，重试与超时单独成行。
 */
class SpConfigRepository(
    context: Context,
    private val remote: SpConfigRemoteSource,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun load(
        request: SpConfigRequest,
        wire: SpDispatchWire,
        trigger: String,
    ): SpConfigLoadResult {
        val startedAt = SystemClock.elapsedRealtime()
        return when (val remoteResult = fetchWithRetry(request, wire, trigger)) {
            is SpApiResult.Success -> {
                val payload = parsePayload(remoteResult.data)
                if (payload == null) {
                    AppLog.network.w("配置字段无效 触发=$trigger")
                    loadCache() ?: SpConfigLoadResult.Unavailable("配置字段无效")
                } else {
                    writeCache(payload)
                    AppLog.network.i(
                        "配置请求成功 来源=远端 字段数=${payload.length()} " +
                            "耗时=${SystemClock.elapsedRealtime() - startedAt}ms 触发=$trigger",
                    )
                    SpConfigLoadResult.Available(payload, SpConfigOrigin.REMOTE)
                }
            }
            is SpApiResult.Failure -> {
                val cached = loadCache()
                AppLog.network.w(
                    "配置请求失败 原因=${remoteResult.message} " +
                        "回落缓存=${if (cached != null) "有" else "无"} " +
                        "耗时=${SystemClock.elapsedRealtime() - startedAt}ms 触发=$trigger",
                )
                cached ?: SpConfigLoadResult.Unavailable(remoteResult.message)
            }
        }
    }

    fun loadCache(): SpConfigLoadResult.Available? {
        val raw = prefs.getString(KEY_PAYLOAD, null) ?: return null
        val payload = parsePayload(raw)
        if (payload == null) {
            clearInvalidCache()
            return null
        }
        return SpConfigLoadResult.Available(payload, SpConfigOrigin.CACHE)
    }

    fun lastSuccessfulFetchAtMillis(): Long = prefs.getLong(KEY_FETCHED_AT, 0L)

    fun lastAttemptAtMillis(): Long = prefs.getLong(KEY_ATTEMPT_AT, 0L)

    fun markAttempt(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_ATTEMPT_AT, nowMs).apply()
    }

    fun cloakReported(): Boolean = prefs.getBoolean(KEY_CLOAK_REPORTED, false)

    fun saveCloakSuccess(resultJson: String) {
        prefs.edit()
            .putBoolean(KEY_CLOAK_REPORTED, true)
            .putString(KEY_CLOAK_RESULT, resultJson)
            .apply()
    }

    private suspend fun fetchWithRetry(
        request: SpConfigRequest,
        wire: SpDispatchWire,
        trigger: String,
    ): SpApiResult {
        var lastFailure: SpApiResult.Failure? = null
        repeat(CONFIG_REQUEST_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                AppLog.network.i(
                    "重试配置请求 次数=${attempt + 1}/$CONFIG_REQUEST_ATTEMPTS " +
                        "上次原因=${lastFailure?.message} 触发=$trigger",
                )
            }
            val result = withTimeoutOrNull(CONFIG_REQUEST_TIMEOUT) {
                remote.fetch(request, wire, trigger)
            } ?: SpApiResult.Failure(message = "config request timeout").also {
                AppLog.network.w(
                    "配置请求超时 次数=${attempt + 1}/$CONFIG_REQUEST_ATTEMPTS " +
                        "超时=$CONFIG_REQUEST_TIMEOUT 触发=$trigger",
                )
            }
            if (result is SpApiResult.Success) return result
            lastFailure = result as SpApiResult.Failure
        }
        return requireNotNull(lastFailure)
    }

    private fun writeCache(payload: JSONObject) {
        runCatching {
            prefs.edit()
                .putString(KEY_PAYLOAD, payload.toString())
                .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                .apply()
        }.onFailure {
            AppLog.network.w("配置缓存写入失败 字段数=${payload.length()}", it)
        }
    }

    private fun parsePayload(raw: Any): JSONObject? = runCatching {
        when (raw) {
            is JSONObject -> raw
            is String -> JSONObject(raw)
            else -> null
        }?.takeUnless { it.length() == 0 && raw is String && raw.isBlank() }
    }.onFailure {
        AppLog.network.w("配置解析失败 类型=${raw.javaClass.simpleName}", it)
    }.getOrNull()

    private fun clearInvalidCache() {
        AppLog.network.w("配置缓存无效，已清理")
        runCatching { prefs.edit().remove(KEY_PAYLOAD).remove(KEY_FETCHED_AT).apply() }
            .onFailure { AppLog.network.w("配置缓存清理失败", it) }
    }

    companion object {
        private const val PREFS = "sp_remote_config"
        private const val KEY_PAYLOAD = "payload_json"
        private const val KEY_FETCHED_AT = "fetched_at_ms"
        private const val KEY_ATTEMPT_AT = "config_fetch_attempt_at"
        private const val KEY_CLOAK_REPORTED = "cloak_reported"
        private const val KEY_CLOAK_RESULT = "cloak_result"
        private const val CONFIG_REQUEST_ATTEMPTS = 3
        val CONFIG_REQUEST_TIMEOUT = 20.seconds
    }
}
