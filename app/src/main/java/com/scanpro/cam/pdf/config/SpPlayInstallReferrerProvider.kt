package com.scanpro.cam.pdf.config

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 对齐 EfileScan `PlayInstallReferrerProvider`：连一次 Play Install Referrer，
 * 5 秒超时；失败/超时返回 null，由调用方发空串，不在这里重试。
 */
class SpPlayInstallReferrerProvider(context: Context) {

    private val appContext = context.applicationContext

    suspend fun getInstallReferrer(): String? =
        withTimeoutOrNull(CONNECTION_TIMEOUT_MILLIS) {
            awaitInstallReferrer()
        }.also { value ->
            if (value == null) Log.w(TAG, "Install Referrer 不可用或获取超时")
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
                                    .onFailure { Log.w(TAG, "读取 Install Referrer 失败", it) }
                                    .getOrNull()
                            } else {
                                Log.w(TAG, "Install Referrer 返回异常：响应码=$responseCode")
                                null
                            }
                        runCatching { client.endConnection() }
                        if (continuation.isActive) continuation.resume(value)
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        runCatching { client.endConnection() }
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            } catch (error: Throwable) {
                runCatching { client.endConnection() }
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private companion object {
        const val TAG = "SpConfig"
        const val CONNECTION_TIMEOUT_MILLIS = 5_000L
    }
}
