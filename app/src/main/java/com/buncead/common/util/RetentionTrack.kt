package com.buncead.common.util


import android.content.Context
import android.os.Build
import com.bockcock.adjust.Pin
import com.mymy.mymy.SaveKv
import java.util.concurrent.TimeUnit

/**
 * E24：`retention_d1` / `d7` / `d14` / `d30`。
 * 出口：adjust、firebase（不经 bi）。Adjust token 【待填】时只发 firebase。
 * 受 `biz.activated.enabled` 控制；本地无该开关时默认开启。
 */
object RetentionTrack {

    private val NODES = listOf(
        1 to "retention_d1",
        7 to "retention_d7",
        14 to "retention_d14",
        30 to "retention_d30"
    )

    fun checkAndReport(context: Context) {
        if (!isRetentionEnabled()) return
        val installMs = firstInstallTimeMs(context) ?: return
        val days = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - installMs
        ).toInt()
        for ((day, name) in NODES) {
            if (days != day) continue
            val key = "track_evt_$name"
            if (SaveKv.getBoolean(key)) continue

            Pin.event(name)
            SaveKv.setBoolean(key, true)
        }
    }

    private fun isRetentionEnabled(): Boolean {
        // 规范 §5.3：biz.activated.enabled 控制留存回传；工程尚未接入该开关时默认开
        return true
    }

    private fun firstInstallTimeMs(context: Context): Long? = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        info.firstInstallTime.takeIf { it > 0L }
    }.getOrNull()
}
