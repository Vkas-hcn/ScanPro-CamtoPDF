package com.zppko.pdfplus.nor.ss

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bockcock.adjust.Pin
import com.bockcock.adjust.Pt
import com.zppko.pdfplus.nor.horus.HintExt01
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PRePhoneBootRec: BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        if (p1?.action != Intent.ACTION_BOOT_COMPLETED) return
        p0?.let {
            // Migration adaptation: keep the delayed work inside the broadcast lifetime.
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch{
                try {
                    Pin.event(Pin.keepalive_trigger, Pt("trigger_type","boot"))
                    delay(2314)
                    withContext(Dispatchers.Main){
                        HintExt01.startJob(it)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}