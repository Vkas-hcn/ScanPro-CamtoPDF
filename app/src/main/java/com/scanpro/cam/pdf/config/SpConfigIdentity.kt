package com.scanpro.cam.pdf.config

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.scanpro.cam.pdf.BuildConfig
import java.util.Locale
import java.util.UUID

/**
 * 组装 [SpConfigRequest]。
 *
 * 各字段来源：
 * - `appName` = applicationId
 * - `version` = `versionName.versionCode`
 * - `distinctId` = 本机持久化的 UUID（首次生成后不再变）
 * - `gaid` = Google Advertising ID；取不到、用户已删除广告 ID、或后台线程限制时为空串
 * - `phoneModel` = `Build.MODEL`
 * - `refer` = Install Referrer 原文；**ScanPro 尚未接入 Install Referrer**，暂为空串（见待定清单）
 * - `language` / `osVersion` = 当前语言与系统版本
 */
object SpConfigIdentity {

    private const val TAG = "SpConfig"
    private const val PREFS = "sp_config_identity"
    private const val KEY_DISTINCT_ID = "distinct_id"

    fun build(context: Context): SpConfigRequest {
        val app = context.applicationContext
        return SpConfigRequest(
            appName = app.packageName,
            version = "${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}",
            distinctId = distinctId(app),
            gaid = gaid(app),
            phoneModel = Build.MODEL.orEmpty(),
            refer = readInstallReferrer(app),
            language = Locale.getDefault().language.uppercase(Locale.ROOT),
            osVersion = Build.VERSION.RELEASE.orEmpty(),
        )
    }

    /** 与 BI / Adjust 对齐的设备标识；尚未接 BI，先用本机持久 UUID 占位。 */
    private fun distinctId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DISTINCT_ID, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DISTINCT_ID, generated).apply()
        return generated
    }

    /**
     * 必须离开主线程调用：AdvertisingIdClient 在主线程会抛 IllegalStateException。
     * 本方法由 [SpConfigRemoteSource.fetch] 的 IO 上下文发起，安全。
     */
    private fun gaid(context: Context): String = runCatching {
        AdvertisingIdClient.getAdvertisingIdInfo(context).id.orEmpty()
    }.onFailure {
        Log.i(TAG, "gaid unavailable: ${it.javaClass.simpleName}")
    }.getOrDefault("")

    /**
     * Install Referrer 原文。ScanPro 未接入该依赖，恒为空串。
     * 接入后改成读 SDK 结果；接入前不许在此编造。
     */
    private fun readInstallReferrer(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REFERRER, "")
            .orEmpty()

    private const val KEY_REFERRER = "install_referrer"
}
