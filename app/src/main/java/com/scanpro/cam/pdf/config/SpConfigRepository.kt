package com.scanpro.cam.pdf.config

import android.content.Context
import android.util.Log
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
 */
class SpConfigRepository(
    context: Context,
    private val remote: SpConfigRemoteSource,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun load(request: SpConfigRequest, wire: SpDispatchWire): SpConfigLoadResult {
        return when (val remoteResult = fetchWithRetry(request, wire)) {
            is SpApiResult.Success -> {
                val payload = parsePayload(remoteResult.data)
                    ?: return loadCache() ?: SpConfigLoadResult.Unavailable("配置字段无效")
                writeCache(payload)
                SpConfigLoadResult.Available(payload, SpConfigOrigin.REMOTE)
            }
            is SpApiResult.Failure -> {
                Log.w(TAG, "remote failed code=${remoteResult.code} msg=${remoteResult.message}")
                loadCache() ?: SpConfigLoadResult.Unavailable(remoteResult.message)
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
    ): SpApiResult {
        var lastFailure: SpApiResult.Failure? = null
        repeat(CONFIG_REQUEST_ATTEMPTS) { attempt ->
            val result = withTimeoutOrNull(CONFIG_REQUEST_TIMEOUT) {
                remote.fetch(request, wire)
            } ?: SpApiResult.Failure(message = "config request timeout")
            if (result is SpApiResult.Success) return result
            lastFailure = result as SpApiResult.Failure
            Log.w(TAG, "config attempt ${attempt + 1}/$CONFIG_REQUEST_ATTEMPTS ${lastFailure?.message}")
        }
        return requireNotNull(lastFailure)
    }

    private fun writeCache(payload: JSONObject) {
        runCatching {
            prefs.edit()
                .putString(KEY_PAYLOAD, payload.toString())
                .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                .apply()
        }.onFailure { Log.w(TAG, "cache write failed", it) }
    }

    private fun parsePayload(raw: Any): JSONObject? = runCatching {
        when (raw) {
            is JSONObject -> raw
            is String -> JSONObject(raw)
            else -> null
        }?.takeUnless { it.length() == 0 && raw is String && raw.isBlank() }
    }.onFailure {
        Log.w(TAG, "payload parse failed type=${raw.javaClass.simpleName}", it)
    }.getOrNull()

    private fun clearInvalidCache() {
        Log.i(TAG, "clear invalid config cache")
        runCatching { prefs.edit().remove(KEY_PAYLOAD).remove(KEY_FETCHED_AT).apply() }
            .onFailure { Log.w(TAG, "cache clear failed", it) }
    }

    companion object {
        private const val TAG = "SpConfig"
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
