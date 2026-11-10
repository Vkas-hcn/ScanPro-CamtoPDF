package com.zppko.pdfplus.nor.ss

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.bockcock.adjust.Pin
import com.bockcock.adjust.Pt
import com.buncead.common.util.Util
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.zppko.pdfplus.nor.horus.HintExt01

class CCPdfToolService: Service() {
    override fun onBind(p0: Intent?): IBinder?=null

    companion object{
        var isRunning=false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    var startTime = 0L
    override fun onCreate() {
        super.onCreate()
        Util.iii { "Service() onCreate()" }
        startTime = System.currentTimeMillis()
        try {
            Util.iii { "Service() onCreate()222" }
//            Pin.event(Pin.ntf_top_trigger)
            Pin.event(Pin.notification_trigger, Pt("pos_id","always").put("trigger_source","resident"))
            startForeground(HintExt01.CO_FIX_NOTIFY_ID, HintExt01.createStickNt())
            SpTrackEvents.notificationShow(this, "always", "resident")
        }catch (ex:Exception){
            ex.printStackTrace()
            Util.iii { "Service() onCreate()333" }
            stopSelf()
        }
        Util.iii { "Service() onCreate()444" }
        Pin.event(Pin.keepalive_service_start, Pt("service_name","top").put("trigger_source","launch" ))
        isRunning =true
    }

    override fun onDestroy() {
        Util.iii { "Service() onDestroy()555" }
       val aliveTime =  System.currentTimeMillis() - startTime
        Pin.event(Pin.keepalive_service_stop, Pt("service_name","top").put("alive_ms",aliveTime))

        super.onDestroy()
        isRunning =false
    }
}