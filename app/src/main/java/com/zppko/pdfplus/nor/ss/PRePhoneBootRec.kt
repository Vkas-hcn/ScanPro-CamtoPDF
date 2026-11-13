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
        p0?.let {
            CoroutineScope(Dispatchers.IO).launch{
                Pin.event(Pin.keepalive_trigger, Pt("trigger_type","boot"))
                delay(2314)
                withContext(Dispatchers.Main){
                    HintExt01.startJob(it)
                }
            }
        }
    }
}