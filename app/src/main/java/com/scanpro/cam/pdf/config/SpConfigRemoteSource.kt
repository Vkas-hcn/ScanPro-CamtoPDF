package com.scanpro.cam.pdf.config

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 中台「配置下发」请求体。
 *
 * 字段名一律走 [SpConfigWire]，本类只承担语义。
 */
data class SpConfigRequest(
    val appName: String,
    val version: String,
    val distinctId: String,
    val gaid: String,
    val phoneModel: String,
    val refer: String,
    val language: String,
    val osVersion: String,
) {
    /** 混淆后的请求 JSON。字段顺序固定，便于抓包比对。 */
    fun toJson(): JSONObject = JSONObject().apply {
        put(SpConfigWire.KEY_REQ_APP_NAME, appName)
        put(SpConfigWire.KEY_REQ_VERSION, version)
        put(SpConfigWire.KEY_REQ_DISTINCT_ID, distinctId)
        put(SpConfigWire.KEY_REQ_GAID, gaid)
        put(SpConfigWire.KEY_REQ_PHONE_MODEL, phoneModel)
        put(SpConfigWire.KEY_REQ_REFER, refer)
        put(SpConfigWire.KEY_REQ_LANGUAGE, language)
        put(SpConfigWire.KEY_REQ_OS_VERSION, osVersion)
    }
}

/**
 * 配置接口的裸 HTTP 层。
 *
 * 协议按 `config接入PRD.md`：POST JSON，单项超时 10s，最多 3 次尝试；
 * 网络错误、HTTP 非 2xx、响应不是 JSON 都算失败并返回 null，**不抛异常**。
 * 调用方不许因为 null 崩溃，也不许清空已有缓存。
 */
class SpConfigRemoteSource(
    private val baseUrl: String = SpConfigWire.BASE_URL,
    private val path: String = SpConfigWire.PATH,
    private val timeoutMs: Int = TIMEOUT_MS,
    private val maxAttempts: Int = MAX_ATTEMPTS,
) {

    suspend fun fetch(request: SpConfigRequest): JSONObject? = withContext(Dispatchers.IO) {
        if (!SpConfigWire.isConfigured()) {
            Log.i(TAG, "config endpoint not configured — skip fetch")
            return@withContext null
        }
        val url = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            val json = runCatching { postOnce(url, request) }
                .onFailure { Log.w(TAG, "config attempt $attempt/$maxAttempts failed", it) }
                .getOrNull()
            if (json != null) return@withContext json
        }
        Log.w(TAG, "config fetch gave up after $maxAttempts attempts url=$url")
        null
    }

    private fun postOnce(url: String, request: SpConfigRequest): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(request.toJson().toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                // 500 之类按失败处理：不覆盖缓存。
                Log.w(TAG, "config http $code bodyLen=${body.length}")
                null
            } else {
                runCatching { JSONObject(body) }
                    .onFailure { Log.w(TAG, "config body not json bodyLen=${body.length}") }
                    .getOrNull()
            }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    companion object {
        private const val TAG = "SpConfig"
        /** 单项超时；`config接入PRD.md` 要求 10s。 */
        private const val TIMEOUT_MS = 10_000
        /** 总尝试次数；`config接入PRD.md` 要求重试 3 次。 */
        private const val MAX_ATTEMPTS = 3
    }
}
