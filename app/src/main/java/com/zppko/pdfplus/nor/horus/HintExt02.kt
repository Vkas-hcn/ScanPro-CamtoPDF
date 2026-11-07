package com.zppko.pdfplus.nor.horus

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import com.zppko.pdfplus.nor.remote.ConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

object HintExt02 {

    var isForeground = false
    private var mActivityNum = 0

    fun init(context: Application) {
        //前后台监听
        context.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (mActivityNum == 0) {
                    // 应用从后台切到前台
                    isForeground = true
                }
                mActivityNum++
            }

            override fun onActivityStopped(activity: Activity) {
                mActivityNum--
                if (mActivityNum == 0) {
                    // 应用从前台切到后台
                    isForeground = false

                    fsd()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
            }
        })

    }

    // 后面增加的需求，退到后台发送通知，发送解锁类型的通知
    fun fsd(){
        if ( ConfigHelper.canShowAdsss_a2()) {
            CoroutineScope(Dispatchers.IO).launch {
                val time = Random.nextLong(2561,3401)
                delay(time)
                withContext(Dispatchers.Main){
                    HintExt04.sendTtwo()
                }
            }
        }
    }
}