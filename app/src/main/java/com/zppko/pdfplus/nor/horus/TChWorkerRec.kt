package com.zppko.pdfplus.nor.horus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buncead.common.util.Util

//import com.zenlytic.clean.master.ads.helper.EVEtnUploader

// TimeWorkerRe
class TChWorkerRec: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
//        Log.e("通知弹窗", "收到闹钟: ", )
        Util.iii { "Notification pop-up, Received alarm" }
//        EVEtnUploader.mine("receive_inexact_alarm")
        HintExt04.sendTthree()
        HintExt05.markLing()
    }
}