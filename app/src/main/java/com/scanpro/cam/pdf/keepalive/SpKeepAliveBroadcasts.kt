package com.scanpro.cam.pdf.keepalive

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.scanpro.cam.pdf.notice.SpPackageReceiver
import com.scanpro.cam.pdf.notice.SpPhoneLockReceiver

/**
 * 保活相关的**动态注册**广播总入口。
 *
 * 1.0.3 把亮灭屏 / 解锁 / 充电插拔 / 装包这些广播写在清单里，但 targetSdk ≥ 26 之后
 * 这些 action 不在隐式广播豁免名单内，清单注册收不到（真机确认）。这里在前台服务存活期间
 * 统一动态注册，服务销毁时统一注销。
 *
 * 只在前台服务存活期间生效——这正是需要的时机：进程在，就顺手把下一轮唤醒点起来。
 */
internal object SpKeepAliveBroadcasts {

    private const val TAG = "SpKeepAliveBc"

    private var lockReceiver: SpPhoneLockReceiver? = null
    private var packageReceiver: SpPackageReceiver? = null

    fun register(context: Context) {
        if (lockReceiver == null) {
            runCatching {
                val receiver = SpPhoneLockReceiver()
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_USER_UNLOCKED)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED,
                )
                lockReceiver = receiver
                Log.i(TAG, "lock/power receiver registered")
            }.onFailure { Log.w(TAG, "register lock receiver failed", it) }
        }
        if (packageReceiver == null) {
            runCatching {
                val receiver = SpPackageReceiver()
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                    // 包广播自带 package: data，不写 scheme 的话 filter 匹配不上（1.0.3 的 bug）
                    addDataScheme("package")
                }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED,
                )
                packageReceiver = receiver
                Log.i(TAG, "package receiver registered")
            }.onFailure { Log.w(TAG, "register package receiver failed", it) }
        }
    }

    fun unregister(context: Context) {
        lockReceiver?.let { receiver ->
            runCatching { context.unregisterReceiver(receiver) }
            lockReceiver = null
        }
        packageReceiver?.let { receiver ->
            runCatching { context.unregisterReceiver(receiver) }
            packageReceiver = null
        }
    }
}
