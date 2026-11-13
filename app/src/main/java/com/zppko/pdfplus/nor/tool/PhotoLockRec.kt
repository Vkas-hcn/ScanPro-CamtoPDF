package com.zppko.pdfplus.nor.tool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buncead.common.util.Util
import com.zppko.pdfplus.nor.horus.HintExt04
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoLockRec: BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        p0?.let {
            CoroutineScope(Dispatchers.IO).launch {
                delay(1523)
                Util.iii { "PhotoLockRec() p1=${p1}" }
                withContext(Dispatchers.Main){
                    HintExt04.sendTtwo()
                }
            }
        }
    }
}