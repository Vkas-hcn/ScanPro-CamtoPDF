package com.zppko.pdfplus.nor.horus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buncead.common.util.Util

class WidgetPinnedReceiver : BroadcastReceiver() {

    companion object {
        val ACTION_SHOW_NOTIFY = "com.myapp.action.show_ntf"
    }

    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action.equals(ACTION_SHOW_NOTIFY)) { // 当用户划掉前台通知，发广播到这里在显示通知
//            val appCtx = context.applicationContext
          Util.iii { "onReceive() ---------------------" }
          HintExt01.launchFg()
        }

    }
}