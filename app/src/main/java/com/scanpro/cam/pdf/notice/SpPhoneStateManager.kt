package com.scanpro.cam.pdf.notice

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 手机锁屏/解锁状态。照抄 ApolloPDF `notice/launcher_scene/PhoneStateManager.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/notice/launcher_scene/PhoneStateManager.java`
 *
 * 解锁那一刻不能立刻发通知：锁屏还没收回去，通知会被系统压掉。Apollo 等 1.5 秒再发，
 * 这里照做。
 */
internal object SpPhoneStateManager {

    private const val TAG = "SpPhoneState"

    /** Apollo `onUserUnlocked` 里的等待。 */
    private const val UNLOCK_SETTLE_MS = 1500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Apollo `PhoneStateManager.State`。 */
    data class State(
        val isScreenOn: Boolean,
        val isKeyguardLocked: Boolean,
        val isUserUnlocked: Boolean,
    ) {
        /** 锁屏锁着 **或** 用户还没解锁，都算「真的锁着」。 */
        val isReallyLocked: Boolean get() = isKeyguardLocked || !isUserUnlocked
    }

    /** Apollo `resolve(context)`：读三个系统服务拼出一次快照。 */
    fun resolve(context: Context): State {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val user = context.getSystemService(Context.USER_SERVICE) as? UserManager
        return State(
            isScreenOn = power?.isInteractive == true,
            isKeyguardLocked = keyguard?.isKeyguardLocked == true,
            isUserUnlocked = user?.isUserUnlocked == true,
        )
    }

    /**
     * Apollo `onUserUnlocked(context, state)`：真的解锁了才排队；等 1.5 秒发一条解锁提醒。
     */
    fun onUserUnlocked(context: Context, state: State) {
        if (state.isReallyLocked) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                delay(UNLOCK_SETTLE_MS)
                SpTriggerManager.sendUnlockNotification(appContext)
            }.onFailure { Log.w(TAG, "onUserUnlocked send failed", it) }
        }
    }
}
