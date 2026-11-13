package com.zppko.pdfplus.nor.horus

import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HintEx06 {



    private var coFiveMinute = 0

    fun start(){
        //轮训
        CoroutineScope(Dispatchers.IO).launch {
            delay(4_629)
            while (true) {

                coFiveMinute++
                HintExt04.sendTone()
                delay(61_989)
            }
        }

        //FCM
        Firebase.messaging.subscribeToTopic("scanpro_pdf").addOnCompleteListener {}
    }


}