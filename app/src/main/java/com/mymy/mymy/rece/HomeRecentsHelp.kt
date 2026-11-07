package com.mymy.mymy.rece

import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.zppko.pdfplus.nor.remote.ConfigHelper

class HomeRecentsHelp {

    /**
     * Aura Cleaner 1.0.9 把这条 receiver 做成运行时 `new f()`，不写进清单。
     * Android 12+ 清单静态注册收不到 CLOSE_SYSTEM_DIALOGS，动态注册仍可能收到系统发出的广播。
     */
    fun registerHomeRecentsReceiver(context: Context) {
        if (ConfigHelper.canShowAdsss_a2()) {
            synchronized(registrationLock) {
                // B retries at cache/remote/Adjust readiness; keep one A receiver per process.
                if (homeRecentsReceiver != null) return
                val receiver = LookLauReceiver()
                val filter = IntentFilter(LookLauReceiver.ACTION_CLOSE_SYSTEM_DIALOGS)
                ContextCompat.registerReceiver(
                    context.applicationContext,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                homeRecentsReceiver = receiver
            }
        }
    }

    companion object {
        private val registrationLock = Any()
        private var homeRecentsReceiver: LookLauReceiver? = null
    }
}
