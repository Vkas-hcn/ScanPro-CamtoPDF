package com.scanpro.cam.pdf.config

import android.content.Context
import com.scanpro.cam.pdf.util.AppLog
import org.json.JSONObject

/**
 * 本地默认配置，仅在「远端没有返回数据且本地没有接口缓存」时使用。
 *
 * 文件：`app/src/main/assets/default_config.json`。支持两种写法：
 * - 直接是配置 `data` 对象；
 * - 或完整响应 `{ "code": 200, "msg": "...", "data": { ... } }`。
 *
 * 只要 `SpConfigRepository` 命中缓存，或远端返回有效数据，就不会调用这里。
 */
object SpDefaultConfig {

    private const val TAG = "SpDefaultConfig"
    private const val ASSET = "default_config.json"

    fun load(context: Context): JSONObject? = runCatching {
        val text = context.applicationContext.assets
            .open(ASSET)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = JSONObject(text)
        val payload = root.optJSONObject("data") ?: root
        payload.takeIf { it.length() > 0 }
    }.onFailure {
        AppLog.network.w("$TAG 本地默认配置读取失败，按无配置处理", it)
    }.getOrNull()
}
