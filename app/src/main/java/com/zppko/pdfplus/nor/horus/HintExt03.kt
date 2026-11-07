package com.zppko.pdfplus.nor.horus

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.zppko.pdfplus.nor.tool.PhotoLockRec


object HintExt03 {






    fun init(context: Application){


        //广播
//        IntentFilter(Intent.ACTION_USER_PRESENT).run {
//            ContextCompat.registerReceiver(context, PhoneLockRe(), this, ContextCompat.RECEIVER_EXPORTED)
//        }

        val filter: IntentFilter = IntentFilter()
        filter. addAction(Intent.ACTION_USER_PRESENT)     // 解锁
//        filter.  addAction(Intent.ACTION_BATTERY_CHANGED)  // 电量变化
        filter.  addAction(Intent.ACTION_POWER_CONNECTED) // 插电
        filter.  addAction(Intent.ACTION_POWER_DISCONNECTED) // 插电
        filter.run {
            val flags = ContextCompat.RECEIVER_EXPORTED
            ContextCompat.registerReceiver(context, PhotoLockRec(), this, flags)
        }

        // Filter 2: 监听安装和卸载（带 Scheme 的广播）
        val appFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package") // 必须独立加这行
        }.run {
            val flags = ContextCompat.RECEIVER_EXPORTED
            ContextCompat.registerReceiver(context, PhotoLockRec(), this, flags)
        }


    }







}