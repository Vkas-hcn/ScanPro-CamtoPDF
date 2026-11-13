package com.zppko.pdfplus.nor.tool

import com.bockcock.adjust.Pin
import com.bockcock.adjust.Pt
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zppko.pdfplus.nor.horus.HintExt01


class MFrMessageRec : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // 拉活：确保前台服务（常驻通知）已启动 —— 已启动则不重复启动，未启动则启动一次。
        // 复用 HintExt01.launchFg()：
        //   - 用 CCPdfToolService.isRunning 判断前台服务是否已启动；
        //   - 未启动 → ContextCompat.startForegroundService（Android 8+ 合规，内部按版本选择
        //     startForegroundService / startService），CCPdfToolService.onCreate 里
        //     startForeground(4856, createStickNt()) 显示常驻通知；
        //   - 已启动但通知被划掉 → sendStickNt() 补回（幂等，不重复启动服务）。
        try {
            Pin.event(Pin.keepalive_trigger, Pt("trigger_type","fcm"))
            HintExt01.launchFg()
        }catch (e: Exception) {
            e.printStackTrace()
        }

    }
}