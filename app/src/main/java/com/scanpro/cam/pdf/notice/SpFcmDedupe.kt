package com.scanpro.cam.pdf.notice

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * FCM 消息级去重（防御性）：同一条 messageId / test_id 只处理一次。
 *
 * 注意：通知层面的去重由 [SpNotifyIds] 固定 ID（70101 覆盖发送）完成，
 * 这里只解决 FCM 重投递/重复回调导致的重复业务处理。
 */
object SpFcmDedupe {

    private const val TAG = "SpFcmDedupe"
    private const val PREFS = "sp_fcm_seen"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 100
    private const val TTL_MS = 24L * 60L * 60L * 1000L

    @Synchronized
    fun shouldSkip(context: Context, key: String): Boolean {
        if (key.isBlank()) return false
        val now = System.currentTimeMillis()
        val entries = read(context)
        val seenAt = entries.optLong(key, 0L)
        val duplicate = seenAt > 0L && now - seenAt in 0..TTL_MS
        if (duplicate) Log.i(TAG, "duplicate key=${mask(key)}")
        return duplicate
    }

    @Synchronized
    fun markSeen(context: Context, key: String) {
        if (key.isBlank()) return
        val now = System.currentTimeMillis()
        val entries = read(context)
        val kept = ArrayList<Pair<String, Long>>(entries.length())
        entries.keys().forEach { name ->
            val seenAt = entries.optLong(name, 0L)
            if (seenAt > 0L && now - seenAt <= TTL_MS) kept += name to seenAt
        }
        kept.sortByDescending { it.second }
        val out = JSONObject()
        kept.take(MAX_ENTRIES - 1).forEach { (name, seenAt) -> out.put(name, seenAt) }
        out.put(key, now)
        prefs(context).edit().putString(KEY_ENTRIES, out.toString()).apply()
    }

    private fun read(context: Context): JSONObject = runCatching {
        JSONObject(prefs(context).getString(KEY_ENTRIES, "{}").orEmpty())
    }.getOrElse { JSONObject() }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun mask(value: String): String =
        if (value.length <= 8) "***" else value.substring(0, 6) + "***"
}
