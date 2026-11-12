package com.zppko.pdfplus.nor.horus

import android.content.Context
import android.content.Intent
import com.buncead.common.util.Util
import com.zppko.pdfplus.nor.job.SafeJobIntentService


class AbcLaunceJob: SafeJobIntentService() {
    override fun onHandleWork(intent: Intent) {
        Util.iii { "AssistanceLaunceJob() onHandleWork()" }

        HintExt01.launchFg(true)
    }

    companion object{

        fun enqueueWork(context: Context){
            enqueueWork(context, AbcLaunceJob::class.java,4321,Intent(context, AbcLaunceJob::class.java))
        }
    }
}