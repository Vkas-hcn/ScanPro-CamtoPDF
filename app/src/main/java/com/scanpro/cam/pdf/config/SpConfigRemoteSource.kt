package com.scanpro.cam.pdf.config

import android.content.Context
import android.os.SystemClock
import com.scanpro.cam.pdf.BuildConfig
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

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
 *
 * 日志统一走 [AppLog.network]（tag `spLog`，前缀 `[network]`），与广告层 `[ads]`
 * 同一套过滤方式。每次请求按「开始请求接口 → 请求头 → 请求入参 → 收到响应 →
 * 响应出参 → 接口完成」打印编号、触发来源、耗时与业务码，出问题按编号串起来看。
 */
class SpConfigRemoteSource(
    context: Context,
) {
    private val app = context.applicationContext

    /** 同一进程内的请求序号，用于把并发（dispatch / cloak）与重试的日志串起来。 */
    private val callSequence = AtomicInteger(0)

    suspend fun fetch(
        request: SpConfigRequest,
        wire: SpDispatchWire,
        trigger: String,
    ): SpApiResult = postDispatch(
        request = request,
        wire = wire,
        path = SpConfigWire.PATH_DISPATCH,
        expectData = true,
        trigger = trigger,
    )

    suspend fun cloak(
        request: SpConfigRequest,
        wire: SpDispatchWire,
        trigger: String,
    ): CloakAttempt = withContext(Dispatchers.IO) {
        val callId = callSequence.incrementAndGet()
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val raw = postRaw(request, wire, SpConfigWire.PATH_CLOAK, trigger, callId)
            val success = raw.httpOk && raw.businessCode == 200
            AppLog.network.i(
                "接口完成 序号=#$callId 接口=${SpConfigWire.PATH_CLOAK} " +
                    "结果=${if (success) "成功" else "失败"} http=${raw.httpCode} " +
                    "业务码=${raw.businessCode ?: "无"} 耗时=${raw.elapsedMs}ms 触发=$trigger",
            )
            CloakAttempt(
                success = success,
                resultJson = raw.body?.toString().orEmpty(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logCallError(
                path = SpConfigWire.PATH_CLOAK,
                callId = callId,
                trigger = trigger,
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                error = error,
            )
            CloakAttempt(success = false)
        }
    }

    private suspend fun postDispatch(
        request: SpConfigRequest,
        wire: SpDispatchWire,
        path: String,
        expectData: Boolean,
        trigger: String,
    ): SpApiResult = withContext(Dispatchers.IO) {
        SpTracker.log(app, SpTracker.BICONFIG_REQUEST)
        val callId = callSequence.incrementAndGet()
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val raw = postRaw(request, wire, path, trigger, callId)
            val result = when {
                !raw.httpOk -> SpApiResult.Failure(raw.httpCode, "HTTP ${raw.httpCode}")
                raw.body == null -> SpApiResult.Failure(raw.httpCode, "响应体为空")
                raw.businessCode != 0 && raw.businessCode != 200 ->
                    SpApiResult.Failure(raw.businessCode, raw.message)
                expectData && raw.data == null ->
                    SpApiResult.Failure(raw.businessCode, "响应数据为空")
                else -> SpApiResult.Success(raw.data ?: JSONObject())
            }
            logCallResult(path, callId, trigger, raw, result)
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logCallError(
                path = path,
                callId = callId,
                trigger = trigger,
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                error = error,
            )
            SpApiResult.Failure(message = error.message ?: "网络请求异常", cause = error)
        }
    }

    private fun postRaw(
        request: SpConfigRequest,
        wire: SpDispatchWire,
        path: String,
        trigger: String,
        callId: Int,
    ): RawResponse {
        val url = wire.baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        val trace = "序号=#$callId 接口=$path"
        AppLog.network.i(
            "开始请求接口 $trace 方式=POST 地址=$url 环境=${wire.environment} 触发=$trigger " +
                "超时=连接${CONNECT_TIMEOUT_MS / 1000}秒/读取${READ_TIMEOUT_MS / 1000}秒",
        )
        AppLog.network.i(
            "请求头 $trace X-App-Id=${BuildConfig.APPLICATION_ID} " +
                "X-App-Version=${BuildConfig.VERSION_NAME} X-Environment=${wire.environment} " +
                "Accept=*/* Content-Type=application/json; charset=UTF-8",
        )
        AppLog.network.i(
            "请求字段 $trace 包名=${request.packageName} 版本=${request.versionName} " +
                "语言=${request.language} 机型=${request.deviceModel} 系统=${request.osVersion} " +
                "含DistinctID=${request.distinctId.isNotBlank().yesNo()} " +
                "含GAID=${request.gaid.isNotBlank().yesNo()} " +
                "含Referrer=${request.installReferrer.isNotBlank().yesNo()}",
        )
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
            logPayload("请求入参 $trace", payload)
            val startedAt = SystemClock.elapsedRealtime()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val httpCode = connection.responseCode
            val stream = if (httpCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            AppLog.network.i(
                "收到响应 $trace http=$httpCode 耗时=${elapsedMs}ms 长度=${text.length}",
            )
            if (text.isBlank()) {
                AppLog.network.w("响应出参为空 $trace http=$httpCode")
            } else {
                logPayload("响应出参 $trace http=$httpCode", text)
            }
            val body = text.takeIf { it.isNotBlank() }?.let {
                runCatching { JSONObject(it) }
                    .onFailure {
                        AppLog.network.w(
                            "响应不是 JSON $trace http=$httpCode 长度=${text.length}",
                            it,
                        )
                    }
                    .getOrNull()
            }
            RawResponse(
                httpCode = httpCode,
                httpOk = httpCode in 200..299,
                body = body,
                businessCode = body?.takeIf { it.has("code") && !it.isNull("code") }?.optInt("code"),
                message = body.readMessage(),
                data = body.unwrapData(),
                elapsedMs = elapsedMs,
            )
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun logCallResult(
        path: String,
        callId: Int,
        trigger: String,
        raw: RawResponse,
        result: SpApiResult,
    ) {
        val trace = "序号=#$callId 接口=$path"
        val dataFields = raw.data?.length() ?: 0
        when (result) {
            is SpApiResult.Success -> AppLog.network.i(
                "接口完成 $trace 结果=成功 http=${raw.httpCode} " +
                    "业务码=${raw.businessCode ?: "无"} data字段=$dataFields " +
                    "耗时=${raw.elapsedMs}ms 触发=$trigger",
            )
            is SpApiResult.Failure -> AppLog.network.w(
                "接口完成 $trace 结果=失败 http=${raw.httpCode} " +
                    "业务码=${raw.businessCode ?: "无"} 原因=${result.message} " +
                    "耗时=${raw.elapsedMs}ms 触发=$trigger",
            )
        }
    }

    private fun logCallError(
        path: String,
        callId: Int,
        trigger: String,
        elapsedMs: Long,
        error: Throwable,
    ) {
        AppLog.network.w(
            "接口异常 序号=#$callId 接口=$path 耗时=${elapsedMs}ms " +
                "异常=${error.javaClass.simpleName}: ${error.message} 触发=$trigger",
            error,
        )
    }

    private fun logPayload(title: String, raw: String) {
        val pretty = prettyJson(raw)
        AppLog.network.i("$title 长度=${raw.length}")
        if (pretty.length <= MAX_LOG_CHUNK) {
            AppLog.network.i(pretty)
            return
        }
        val total = (pretty.length + MAX_LOG_CHUNK - 1) / MAX_LOG_CHUNK
        var index = 0
        var part = 1
        while (index < pretty.length) {
            val end = minOf(index + MAX_LOG_CHUNK, pretty.length)
            AppLog.network.i("[$part/$total] ${pretty.substring(index, end)}")
            index = end
            part++
        }
    }

    companion object {
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
    val elapsedMs: Long,
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
