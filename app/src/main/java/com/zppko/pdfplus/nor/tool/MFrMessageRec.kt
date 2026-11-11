package com.zppko.pdfplus.nor.tool

import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.notice.SpFcmDedupe
import com.scanpro.cam.pdf.notice.SpFcmNotification
import com.zppko.pdfplus.nor.horus.HintExt01
import com.zppko.pdfplus.nor.horus.HintExt02
import com.zppko.pdfplus.nor.ss.CCPdfToolService
import java.security.MessageDigest

/**
 * FCM 接收与本地处理。
 *
 * 行为约定（对齐本轮验收）：
 * - 每次回调先做 messageId/test_id 去重；
 * - 后台：启动/复用前台服务（常驻通知 4856），data-only 额外由应用主动发一条普通通知（70101）；
 * - notification + data 在后台由 Firebase SDK 自动展示，应用不重复发；
 * - 前台：不新增任何通知、不启动会创建常驻通知的前台服务；
 * - onNewToken 持久化 token，日志只输出长度与指纹。
 *
 * 容错约定：[onMessageReceived] 跑在 FirebaseMessagingService 的后台线程，抛出的未捕获
 * 异常会走默认 UncaughtExceptionHandler 直接杀掉进程（用户看到的就是“一收推送就闪退”）。
 * 推送是弱业务链路，因此这里全程兜底，并且**每段独立 try**：埋点、去重、起服务、发通知
 * 任一段失败都只记日志，不阻断其余步骤。
 */
class MFrMessageRec : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // 最后一道防线：任何未预料的 Throwable 都不许冒出去把进程带走。
        try {
            handleMessage(message)
        } catch (t: Throwable) {
            Log.e(TAG, "onMessageReceived failed but contained", t)
        }
    }

    private fun handleMessage(message: RemoteMessage) {
        trackReceived(message)

        // 去重：命中重复直接结束；去重自身出错时按“没收到过”继续，宁可重复也不丢消息。
        if (isDuplicate(message)) return

        val dataOnly = message.notification == null
        Log.i(
            TAG,
            "received dataOnly=" + dataOnly +
                " foreground=" + HintExt02.isForeground +
                " testId=" + mask(message.data?.get("test_id").orEmpty()),
        )

        // 前台按本轮验收口径不新增通知；启动前台服务会创建常驻通知，因此也跳过。
        if (HintExt02.isForeground) {
            Log.i(TAG, "foreground: no notification/service action")
            return
        }

        ensureForegroundService()

        if (dataOnly) {
            postNotification(message)
        } else {
            Log.i(TAG, "notification payload: SDK auto-display in background, skip app notify")
        }
    }

    /** E15 埋点。单独兜底：统计失败不能连带吞掉通知和保活。 */
    private fun trackReceived(message: RemoteMessage) {
        try {
            val posId = message.data?.get("pos_id").orEmpty()
            SpTrackEvents.notificationReceived(posId, message.messageId.orEmpty())
        } catch (t: Throwable) {
            Log.w(TAG, "track notification_received failed", t)
        }
    }

    /** true = 判为重复，本次丢弃。去重逻辑自身异常时返回 false（按新消息继续处理）。 */
    private fun isDuplicate(message: RemoteMessage): Boolean = try {
        val key = messageKey(message)
        if (SpFcmDedupe.shouldSkip(this, key)) {
            Log.i(TAG, "duplicate message ignored key=" + mask(key))
            true
        } else {
            SpFcmDedupe.markSeen(this, key)
            false
        }
    } catch (t: Throwable) {
        Log.w(TAG, "dedupe failed, treat as new message", t)
        false
    }

    private fun postNotification(message: RemoteMessage) {
        try {
            SpFcmNotification.post(this, message)
        } catch (t: Throwable) {
            Log.e(TAG, "post notification failed", t)
        }
    }

    override fun onNewToken(token: String) {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_TOKEN_AT, System.currentTimeMillis())
                .apply()
            Log.i(TAG, "token refreshed len=" + token.length + " fp=" + fingerprint(token))
        } catch (t: Throwable) {
            Log.e(TAG, "onNewToken failed but contained", t)
        }
    }

    /**
     * Android 12+ 的 launchFg(true) 只补发通知、不启动服务；
     * FCM 高优先级消息可豁免后台启动限制，这里显式请求 startForegroundService。
     */
    private fun ensureForegroundService() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, CCPdfToolService::class.java),
            )
            Log.i(TAG, "foreground service start requested")
        }.onFailure {
            Log.w(TAG, "foreground service start blocked; fallback WorkManager", it)
            runCatching { HintExt01.startJob(this) }
                .onFailure { error -> Log.e(TAG, "fallback WorkManager failed", error) }
        }
    }

    private fun messageKey(message: RemoteMessage): String {
        message.messageId?.takeIf { it.isNotBlank() }?.let { return it }
        message.data?.get("test_id")?.takeIf { it.isNotBlank() }?.let { return "test:" + it }
        return "payload:" + sha256(message.data?.toString().orEmpty())
    }

    private fun fingerprint(token: String): String = "sha256:" + sha256(token).take(12)

    private fun sha256(value: String): String = runCatching {
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    private fun mask(value: String): String =
        if (value.length <= 8) "***" else value.substring(0, 6) + "***"

    private companion object {
        private const val TAG = "MFrMessageRec"
        private const val PREFS = "sp_fcm_token"
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_AT = "token_at"
    }
}
