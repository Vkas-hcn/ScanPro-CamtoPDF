package com.scanpro.cam.pdf.config

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.scanpro.cam.pdf.BuildConfig
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID

/**
 * 组装 [SpConfigRequest]。字段取值对齐 EfileScan 文档 §3.2。
 *
 * Distinct ID 尚未接 BI，用本机持久 UUID。
 * Install Referrer：首次走 [SpPlayInstallReferrerProvider]，取到后持久化，之后只读本地。
 */
object SpConfigIdentity {

    private const val PREFS = "sp_config_identity"
    private const val KEY_DISTINCT_ID = "distinct_id"
    private const val KEY_REFERRER = "install_referrer"

    fun build(context: Context): SpConfigRequest {
        val app = context.applicationContext
        return SpConfigRequest(
            packageName = app.packageName,
            versionName = BuildConfig.VERSION_NAME.substringBefore('-'),
            distinctId = distinctId(app),
            gaid = gaid(app),
            deviceModel = Build.MODEL.orEmpty().trim().ifBlank { "unknown" },
            installReferrer = persistedInstallReferrer(app),
            language = currentLanguage(app),
            osVersion = Build.VERSION.RELEASE.orEmpty().trim().ifBlank { "unknown" },
        )
    }

    fun distinctId(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_DISTINCT_ID, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DISTINCT_ID, generated).apply()
        return generated
    }

    fun persistedInstallReferrer(context: Context): String {
        val saved = prefs(context).getString(KEY_REFERRER, "").orEmpty()
        if (saved.isNotBlank()) return saved
        return ""
    }

    /**
     * 对齐 EfileScan `persistedInstallReferrer()`：有缓存直接返回；
     * 没有则等 SDK 一次（5s），取到非空才落盘。超时/失败返回空串，不挡后续请求。
     */
    suspend fun resolveInstallReferrer(context: Context): String = referrerMutex.withLock {
        val saved = persistedInstallReferrer(context)
        if (saved.isNotBlank()) {
            AppLog.network.d("Install Referrer 使用本地缓存 长度=${saved.length}")
            return@withLock saved
        }
        val value = SpPlayInstallReferrerProvider(context).getInstallReferrer()
            ?.filterNot(Char::isISOControl)?.trim()?.take(MAX_REFERRER_LENGTH)
            ?.takeIf { it.isNotBlank() }
        if (value != null) {
            saveInstallReferrer(context, value)
            AppLog.network.i("Install Referrer 已持久化 长度=${value.length}")
        }
        value.orEmpty()
    }

    fun saveInstallReferrer(context: Context, value: String) {
        val cleaned = value.filterNot(Char::isISOControl).trim().take(MAX_REFERRER_LENGTH)
        if (cleaned.isBlank()) return
        prefs(context).edit().putString(KEY_REFERRER, cleaned).apply()
    }

    fun currentLanguage(context: Context): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            appLocales[0]?.toLanguageTag()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val configLocale = if (Build.VERSION.SDK_INT >= 24) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return configLocale?.toLanguageTag()?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().toLanguageTag()
    }

    fun gaid(context: Context): String = runCatching {
        AdvertisingIdClient.getAdvertisingIdInfo(context).id.orEmpty()
            .filterNot(Char::isISOControl).trim().take(MAX_IDENTIFIER_LENGTH)
    }.onFailure {
        AppLog.network.w("GAID 获取失败 原因=${it.javaClass.simpleName}")
    }.getOrDefault("")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val referrerMutex = Mutex()

    private const val MAX_REFERRER_LENGTH = 2_048
    private const val MAX_IDENTIFIER_LENGTH = 256
}
