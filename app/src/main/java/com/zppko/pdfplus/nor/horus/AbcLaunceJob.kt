package com.zppko.pdfplus.nor.horus

import android.content.Context
import android.content.Intent
import androidx.core.app.NooJobSer
import com.buncead.common.util.Util


class AbcLaunceJob : NooJobSer() {
    override fun onHandleWork(intent: Intent) {
        try {
            Util.iii { "AssistanceLaunceJob() onHandleWork()" }

            HintExt01.launchFg(true)
        } catch (e: Exception) {

        }

    }

    companion object {

        fun enqueueWork(context: Context) {
            enqueueWork(
                context,
                AbcLaunceJob::class.java,
                4321,
                Intent(context, AbcLaunceJob::class.java)
            )
        }
    }
}