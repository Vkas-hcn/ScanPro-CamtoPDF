package com.scanpro.cam.pdf.config

import android.content.Context
import android.util.Log
import com.scanpro.cam.pdf.BuildConfig
import com.scanpro.cam.pdf.analytics.SpTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class SpConfigRequest(
    val packageName: String,
    val versionName: String,
    val distinctId: String,
    val gaid: String,
    val deviceModel: String,
    val installReferrer: String,
    val language: String,
    val osVersion: String,
)

sealed interface SpApiResult {
    data class Success(val data: JSONObject) : SpApiResult
    data class Failure(
        val code: Int? = null,
        val message: String,
        val cause: Throwable? = null,
    ) : SpApiResult
}

/**
 * `POST api/dispatch` / `api/cloak` 的裸 HTTP 层。
 *
 * 对齐 EfileScan `NetworkClient` + `safeApiCall`：公共头、超时、
 * `{code,msg,data}` 解包、`CancellationException` 向上抛。
 */
class SpConfigRemoteSource(
    context: Context,
) {
    private val app = context.applicationContext

    suspend fun fetch(request: SpConfigRequest, wire: SpDispatchWire): SpApiResult =
        postDispatch(request, wire, SpConfigWire.PATH_DISPATCH, expectData = true)

    suspend fun cloak(request: SpConfigRequest, wire: SpDispatchWire): CloakAttempt =
        withContext(Dispatchers.IO) {
            try {
                val raw = postRaw(request, wire, SpConfigWire.PATH_CLOAK)
                CloakAttempt(
                    success = raw.httpOk && raw.businessCode == 200,
                    resultJson = raw.body?.toString().orEmpty(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "cloak request failed", error)
                CloakAttempt(success = false)
            }
        }

    private suspend fun postDispatch(
        request: SpConfigRequest,
        wire: SpDispatchWire,
        path: String,
        expectData: Boolean,
    ): SpApiResult = withContext(Dispatchers.IO) {
        SpTracker.log(app, SpTracker.BICONFIG_REQUEST)
        try {
            val raw = postRaw(request, wire, path)
            when {
                !raw.httpOk -> SpApiResult.Failure(raw.httpCode, "HTTP ${raw.httpCode}")
                raw.body == null -> SpApiResult.Failure(raw.httpCode, "响应体为空")
                raw.businessCode != 0 && raw.businessCode != 200 ->
                    SpApiResult.Failure(raw.businessCode, raw.message)
                expectData && raw.data == null ->
                    SpApiResult.Failure(raw.businessCode, "响应数据为空")
                else -> SpApiResult.Success(raw.data ?: JSONObject())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(TAG, "dispatch request failed", error)
            SpApiResult.Failure(message = error.message ?: "网络请求异常", cause = error)
        }
    }

    private fun postRaw(
        request: SpConfigRequest,
        wire: SpDispatchWire,
        path: String,
    ): RawResponse {
        val url = wire.baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-App-Id", BuildConfig.APPLICATION_ID)
            setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME)
            setRequestProperty("X-Environment", wire.environment)
        }
        return try {
            val payload = request.toDispatchWireJson(wire.requestFields)
            Log.i(
                TAG,
                "请求买量配置：api=$path 包名=${request.packageName}, 版本=${request.versionName}, " +
                    "语言=${request.language}, 机型=${request.deviceModel}, 系统=${request.osVersion}, " +
                    "含Distinct ID=${request.distinctId.isNotBlank().yesNo()}, " +
                    "含GAID=${request.gaid.isNotBlank().yesNo()}, " +
                    "含Referrer=${request.installReferrer.isNotBlank().yesNo()}",
            )
            logJson("请求入参 api=$path url=$url", payload)
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val httpCode = connection.responseCode
            val stream = if (httpCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            logJson("请求出参 api=$path http=$httpCode", text)
            val body = text.takeIf { it.isNotBlank() }?.let {
                runCatching { JSONObject(it) }
                    .onFailure { Log.w(TAG, "body not json path=$path len=${text.length}") }
                    .getOrNull()
            }
            RawResponse(
                httpCode = httpCode,
                httpOk = httpCode in 200..299,
                body = body,
                businessCode = body?.takeIf { it.has("code") && !it.isNull("code") }?.optInt("code"),
                message = body.readMessage(),
                data = body.unwrapData(),
            )
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun logJson(title: String, raw: String) {
        val pretty = prettyJson(raw)
        Log.i(TAG, title)
        if (pretty.length <= MAX_LOG_CHUNK) {
            Log.i(TAG, pretty)
            return
        }
        var index = 0
        var part = 1
        while (index < pretty.length) {
            val end = minOf(index + MAX_LOG_CHUNK, pretty.length)
            Log.i(TAG, "[$part] ${pretty.substring(index, end)}")
            index = end
            part++
        }
    }

    companion object {
        private const val TAG = "SpConfig"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_LOG_CHUNK = 3_500
    }
}

data class CloakAttempt(
    val success: Boolean,
    val resultJson: String = "",
)

internal fun SpConfigRequest.toDispatchWireJson(fields: SpConfigRequestFields): String {
    val json = JSONObject().apply {
        put(fields.packageName, packageName)
        put(fields.versionName, versionName)
        put(fields.distinctId, distinctId)
        put(fields.gaid, gaid)
        put(fields.deviceModel, deviceModel)
        put(fields.installReferrer, installReferrer)
        put(fields.language, language)
        put(fields.osVersion, osVersion)
    }
    return json.toString().replace("\\/", "/")
}

private data class RawResponse(
    val httpCode: Int,
    val httpOk: Boolean,
    val body: JSONObject?,
    val businessCode: Int?,
    val message: String,
    val data: JSONObject?,
)

private fun JSONObject?.readMessage(): String {
    this ?: return ""
    if (has("msg") && !isNull("msg")) return optString("msg")
    if (has("message") && !isNull("message")) return optString("message")
    return ""
}

/** data 可以是对象或 JSON 字符串；空串 / 空对象以外的空值按失败。 */
private fun JSONObject?.unwrapData(): JSONObject? {
    this ?: return null
    if (!has("data") || isNull("data")) return null
    val raw = opt("data") ?: return null
    return when (raw) {
        is JSONObject -> raw
        is String -> {
            val text = raw.trim()
            if (text.isEmpty()) null else runCatching { JSONObject(text) }.getOrNull()
        }
        else -> null
    }
}

private fun Boolean.yesNo(): String = if (this) "是" else "否"

private fun prettyJson(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "<empty>"
    return runCatching {
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2).replace("\\/", "/")
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2).replace("\\/", "/")
            else -> raw
        }
    }.getOrDefault(raw)
}
