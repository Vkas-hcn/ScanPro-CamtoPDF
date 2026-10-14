package com.scanpro.cam.pdf.notice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.keepalive.SpForegroundServiceLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 三个广播接收器 + 它们的触发时机。照抄 ApolloPDF：
 *
 * - [SpPhoneLockReceiver] ← `notice/launcher_scene/PhoneLockRe.java`
 * - [SpKeyReceiver] ← `notice/launcher_scene/KeyReceiverKt.java`
 * - [SpAlarmReceiver] ← `notice/launcher_scene/AlarmReceiver.java`
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/notice/launcher_scene/`
 */

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * 屏幕亮灭 / 解锁。清单里注册，带 `directBootAware`（Apollo 也是）。
 *
 * 两件事并行下发（Apollo `PhoneLockRe.onReceive`）：
 * 1. `USER_PRESENT` / `USER_UNLOCKED` → 等 1.5 秒发解锁提醒（[SpPhoneStateManager.onUserUnlocked]）
 * 2. 任何一条广播 → 等 2 秒去点一次保活（[SpForegroundServiceLauncher.startLaunchService]）
 *
 * **差异**：Apollo 除了清单注册，还在 `ApoApplication` 里动态注册了同一个类的另一个实例
 * （`USER_PRESENT` + `SCREEN_ON/OFF`），同一件事会被触发两遍。这里只保留清单注册。
 */
class SpPhoneLockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        runCatching {
            val state = SpPhoneStateManager.resolve(context)
            Log.i(TAG, "onReceive action=$action state=$state")

            scope.launch {
                runCatching {
                    if (action == Intent.ACTION_USER_PRESENT || action == Intent.ACTION_USER_UNLOCKED) {
                        SpTrackEvents.keepaliveTrigger("user_present")
                        SpPhoneStateManager.onUserUnlocked(context, state)
                    }
                }.onFailure { Log.w(TAG, "user-present handling failed", it) }
            }
            scope.launch {
                delay(UNLOCK_KEEP_ALIVE_DELAY_MS)
                SpForegroundServiceLauncher.startLaunchService(context.applicationContext)
            }
        }.onFailure { Log.w(TAG, "onReceive failed action=$action", it) }
    }

    companion object {
        private const val TAG = "SpPhoneLockReceiver"

        /** Apollo `PhoneLockRe$onReceive$1$2` 里的 2000ms。 */
        private const val UNLOCK_KEEP_ALIVE_DELAY_MS = 2000L
    }
}

/**
 * 按 Home 键 / 最近任务键。**必须动态注册**：`CLOSE_SYSTEM_DIALOGS` 在 Android 8 以后
 * 不进清单广播的白名单，只有上下文注册的接收器收得到（Apollo 也是动态注册的）。
 *
 * 判定规则（Apollo `keyReceiver`）：
 * - action 必须是 `android.intent.action.CLOSE_SYSTEM_DIALOGS`，且带 `reason`
 * - `reason` 含 `home`（忽略大小写）→ 距上次 > 1.5 秒才算一次新的 Home
 * - `reason` 含 `recent` → 同上，用另一份时间戳
 * - 命中的动作：发对应提醒（[SpTriggerManager.sendHomeClickNotification] /
 *   [SpTriggerManager.sendRecentClickNotification]），并顺手点一次保活
 *
 * 1.5 秒去抖是 Apollo 的写法：部分机型一次按键会连发多条广播。
 */
class SpKeyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
        val reason = intent.getStringExtra(REASON) ?: return
        Log.i(TAG, "reason: $reason")
        if (context == null) return

        val now = System.currentTimeMillis()
        if (reason.contains(HOME, ignoreCase = true) && now - lastHomeTime > DEBOUNCE_MS) {
            lastHomeTime = now
            Log.i(TAG, "Home key pressed")
            scope.launch {
                runCatching { SpTriggerManager.sendHomeClickNotification(context) }
                    .onFailure { Log.w(TAG, "home trigger failed", it) }
            }
            scope.launch { SpForegroundServiceLauncher.startLaunchService(context.applicationContext) }
        }
        if (reason.contains(RECENT) && now - lastRecentTime > DEBOUNCE_MS) {
            lastRecentTime = now
            Log.i(TAG, "Recent key pressed")
            scope.launch {
                runCatching { SpTriggerManager.sendRecentClickNotification(context) }
                    .onFailure { Log.w(TAG, "recent trigger failed", it) }
            }
            scope.launch { SpForegroundServiceLauncher.startLaunchService(context.applicationContext) }
        }
    }

    companion object {
        private const val TAG = "SpKeyReceiver"

        private const val REASON = "reason"
        private const val HOME = "home"
        private const val RECENT = "recent"

        /** Apollo：1.5 秒内的重复广播算同一次按键。 */
        private const val DEBOUNCE_MS = 1500L

        @Volatile
        private var lastHomeTime = 0L

        @Volatile
        private var lastRecentTime = 0L

        /**
         * 注册。在 [com.scanpro.cam.pdf.CamPdfApp] 里调一次。
         * `RECEIVER_NOT_EXPORTED`：系统广播能进来，别的应用发的不行（Android 13+ 的强制要求）。
         */
        fun register(context: Context) {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            runCatching {
                androidx.core.content.ContextCompat.registerReceiver(
                    context,
                    SpKeyReceiver(),
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }.onFailure { Log.w(TAG, "register failed", it) }
        }
    }
}

/**
 * 闹钟到点。Apollo `AlarmReceiver`：
 * 1. 发一条闹钟提醒（[SpTriggerManager.sendClockNotification]）
 * 2. 点一次保活（[SpForegroundServiceLauncher.startLaunchService]）
 * 3. **无条件**把下一个闹钟排上（`setAlarm(force = true)`）——不然只响一次
 *
 * 只在清单里注册（没有 intent-filter），只由 [SpTriggerManager.setAlarm] 的
 * PendingIntent 显式拉起。
 */
class SpAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val app = context?.applicationContext
        if (app == null) return
        SpTrackEvents.keepaliveTrigger("alarm")
        scope.launch {
            runCatching { SpTriggerManager.sendClockNotification(app) }
                .onFailure { Log.w(TAG, "clock trigger failed", it) }
        }
        runCatching { SpForegroundServiceLauncher.startLaunchService(app) }
            .onFailure { Log.w(TAG, "startLaunchService failed", it) }
        runCatching { SpTriggerManager.setAlarm(app, force = true) }
            .onFailure { Log.w(TAG, "setAlarm failed", it) }
    }

    companion object {
        private const val TAG = "SpAlarmReceiver"
    }
}
