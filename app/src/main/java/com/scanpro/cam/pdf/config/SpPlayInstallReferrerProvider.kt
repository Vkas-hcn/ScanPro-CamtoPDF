package com.scanpro.cam.pdf.config

import android.content.Context
import android.os.SystemClock
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 对齐 EfileScan `PlayInstallReferrerProvider`：连一次 Play Install Referrer，
 * 5 秒超时；失败/超时返回 null，由调用方发空串，不在这里重试。
 *
 * 日志走 [AppLog.network]：成功/失败、响应码、耗时都打出来，
 * 用来判断请求字段里的 Referrer 为什么是空。
 */
class SpPlayInstallReferrerProvider(context: Context) {

    private val appContext = context.applicationContext

    suspend fun getInstallReferrer(): String? {
        val startedAt = SystemClock.elapsedRealtime()
        val value = withTimeoutOrNull(CONNECTION_TIMEOUT_MILLIS) {
            awaitInstallReferrer()
        }
        val cost = SystemClock.elapsedRealtime() - startedAt
        if (value == null) {
            AppLog.network.w(
                "Install Referrer 获取失败 原因=不可用或超时 " +
                    "超时=${CONNECTION_TIMEOUT_MILLIS / 1000}秒 耗时=${cost}ms",
            )
        } else {
            AppLog.network.i("Install Referrer 获取成功 长度=${value.length} 耗时=${cost}ms")
        }
        return value
    }

    private suspend fun awaitInstallReferrer(): String? =
        suspendCancellableCoroutine { continuation ->
            val client = InstallReferrerClient.newBuilder(appContext).build()
            continuation.invokeOnCancellation { runCatching { client.endConnection() } }
            try {
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        val value =
                            if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                                runCatching { client.installReferrer.installReferrer }
                                    .onFailure {
                                        AppLog.network.w("Install Referrer 读取失败", it)
                                    }
                                    .getOrNull()
                            } else {
                                AppLog.network.w("Install Referrer 返回异常 响应码=$responseCode")
                                null
                            }
                        runCatching { client.endConnection() }
                        if (continuation.isActive) continuation.resume(value)
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        AppLog.network.w("Install Referrer 服务断开")
                        runCatching { client.endConnection() }
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            } catch (error: Throwable) {
                AppLog.network.w("Install Referrer 连接异常", error)
                runCatching { client.endConnection() }
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 5_000L
    }
}
