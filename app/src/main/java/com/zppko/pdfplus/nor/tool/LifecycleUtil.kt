package com.zppko.pdfplus.nor.tool

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Intent
import android.os.Bundle
import com.adjust.sdk.Adjust

import com.buncead.common.util.Util
import com.buncead.common.ui.AdMyActivity
import com.scanpro.cam.pdf.boot.SpBootActivity

import com.zppko.pdfplus.nor.RAction
import com.zppko.pdfplus.nor.Zppkoo
import kotlinx.coroutines.Job

object LifecycleUtil {
    private var activityCount = 0
    private var isLaunch = true
    private val activityList = mutableListOf<Activity>()
    private var job :Job?=null
    private var extras:Bundle?=null
    private var showHotScreen = false

    fun isEmpty():Boolean{
        return activityList.none { it !is AdMyActivity && it !is SpBootActivity }
    }
    fun notLaunch() {
        isLaunch = false
    }

    fun register(context: Application) {
        context.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                Util.iii { "ActivityLifecycle onActivityStarted() =${activity}"}
                if (activityCount == 0) {
                    // 应用从后台切到前台
                    job?.cancel()
                    if (showHotScreen){
                        //防止冷启动 启动两次开屏页
                        if (activityList.lastOrNull() !is SpBootActivity){
                            activityList.filter {
                                val simpleName = it.javaClass.simpleName
                                simpleName.equals("AdActivity")
                            }.forEach {
                                it.finish()
                            }
//                            activityList.filterIsInstance<AdMyActivity>().forEach { it.finish() }
                            val intent = Intent(Zppkoo.Companion.ii, SpBootActivity::class.java)
                            extras?.let { intent.putExtras(it) }
                            if (extras?.getString(RAction.Launch) == RAction.LaunchShowOnceAds){
                                val act=activityList.lastOrNull()
                                if (act==null ){
//                                if (act==null || act is NewGuideActivity){
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    Zppkoo.Companion.ii.startActivity(intent)
                                }else{
                                    act.startActivity(intent)
                                    activityList.filterIsInstance<SpBootActivity>().forEach { it.finish() } // 发现问题，我加的
                                }
                            }else{
                                //这个地方的触发，只会在冷启动开屏，点击后回来
                                intent.putExtra(RAction.NotUploadEvent,true)
                                intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                Zppkoo.Companion.ii.startActivity(intent)
                            }
                        }
                    }
                    showHotScreen = false
                }
                activityCount++
            }

            override fun onActivityStopped(activity: Activity) {
                Util.iii { "ActivityLifecycle onActivityStopped() =${activity}"}
                activityCount--
                if (activityCount == 0) {
                    // 应用从前台切到后台
                    if (isLaunch){
//                        val isHotLaunch= activityList.any { it !is AdMyActivity && it !is SpBootActivity }
                        val isHotLaunch= activityList.any {
                            val simpleName = it.javaClass.simpleName
                            !simpleName.equals("AdActivity") && it !is SpBootActivity
                        }
                        //如果任务栈里面没有广告和启动页（说明已经进入主页），判断走热启动逻辑
                        if (isHotLaunch){
                            extras = Bundle().apply { putString(RAction.Launch, RAction.LaunchShowOnceAds) }
                        }else{
                            //这里走冷启动流程
                            //保存intent，如果点击通知进入，再点击广告回来，继续走点击流程，但是要处理重复埋点
                            extras = activityList.filterIsInstance<SpBootActivity>().firstOrNull()?.intent?.extras
                        }
                        showHotScreen = true
                    }else{
                        isLaunch = true
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Util.iii { "ActivityLifecycle onActivityCreated() =${activity}"}
                activityList.add(activity)
            }
            override fun onActivityResumed(activity: Activity) {
                Util.iii { "ActivityLifecycle onActivityResumed() =${activity}"}
                Adjust.onResume()
            }
            override fun onActivityPaused(activity: Activity) {
                Util.iii { "ActivityLifecycle onActivityPaused() =${activity}"}
                Adjust.onPause()
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                Util.iii { "ActivityLifecycle onActivityDestroyed() =${activity}"}
                activityList.remove(activity)
            }
        })
    }
}