package com.zppko.pdfplus.nor.ads.tool

import com.zppko.pdfplus.nor.ads.ImpressionUtils
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
// 迁移适配：com.buncead.common.pdfpro.my.BaseActivity → android.app.Activity
import android.app.Activity
import android.util.Log
// 迁移适配：B 不存在 AdJustHelp/AppConfig/FbHelp/KvUtil/Pin/Pt 在 PDF26 的 import 列表里均为未使用，
// 已移除不存在的 import（AdJustHelp/AppConfig、pdfpro.my.Util），保留其余未使用 import 原样。
import com.zppko.pdfplus.nor.ads.sett.AdsSetting

abstract class BaseAdsLoad(var setting: AdsSetting) {

    //广告加载失败
    protected fun zmyAdsLoadFFFF(msg: String) {
        Log.e("hhhh广告", "加载失败 ${msg} ${setting.zPlace}-${setting.zFirm}-${setting.zType}-${setting.zId}",)
        zIsLoadingAds = false
        val setting = ImpressionUtils.getNewLoadConfig(setting.zPlace, setting.zWeight)
        if (setting != null) {
            this.setting = setting
            zLoadeee()
        }
    }

    public fun sendValueToServer(adValue: com.google.android.gms.ads.AdValue, adSourceName: String?) {
        Price.qqq(adValue.valueMicros,adValue.currencyCode)
    }

    protected abstract fun childLoad()

    //广告是否过期
    open fun zisTimeOut(): Boolean {
        val zowiefparkingTime = System.currentTimeMillis() - zLoadedTimeForAds
        return zowiefparkingTime > setting.zTime * 1000L
    }

    //广告加载成功
    protected fun zmyAdsLoadSSSS() {
        Log.e("hhhh广告", "加载成功 ${setting.zPlace}-${setting.zFirm}-${setting.zType} ")
        zIsLoadingAds = false
        zLoadedTimeForAds = System.currentTimeMillis()
    }

    protected abstract fun zcanUsable(): Boolean



    private var zLoadedTimeForAds = 0L


    fun canReloadAds(): Boolean {
        return !zIsLoadingAds && (!zcanUsable() || zisTimeOut())
    }


    open fun zShoweee(act: Activity, event: String, whenOnVisible: Boolean = true, finish: () -> Unit) {
        // 迁移适配：act.isHide() → 页面未结束可用
        if (!(act.isFinishing || act.isDestroyed) && zcanUsable()) {
            Log.e("hhhh广告", "展示广告 ${setting.zPlace}-${setting.zFirm}-${setting.zType}")
            childShow(act, event, whenOnVisible, finish)
        } else {
            zDismiss(act, whenOnVisible, finish)
        }
    }

    fun zIsShowAds(): Boolean {
        return zcanUsable() && !zisTimeOut()
    }

    private var zIsLoadingAds = false


    //关闭广告
    // 迁移适配：android.app.Activity 无 lifecycle 属性，宿主为 LifecycleOwner（AppCompatActivity）时才走
    // onResume 延迟回调；否则立即 finish（B 接线层实际均以 whenOnVisible=false 调用）。
    protected fun zDismiss(act: Activity, whenOnVisible: Boolean = true, finish: () -> Unit) {
        val host = act as? LifecycleOwner
        if (whenOnVisible && host != null) {
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    host.lifecycle.removeObserver(this)
                    finish.invoke()
                }
            }
            host.lifecycle.addObserver(observer)
        } else {
            finish.invoke()
        }
    }


    open fun zLoadeee() {
        if (!zIsLoadingAds && !zcanUsable()) {
            zIsLoadingAds = true
            Log.e("hhhh广告", "开始加载广告 ${setting.zPlace}-${setting.zFirm}-${setting.zType}-${setting.zId}",)
            childLoad()
        }
    }

    protected abstract fun childShow(
        act: Activity,
        event: String,
        whenOnVisible: Boolean = true,
        finish: () -> Unit
    )

    abstract fun zAdsDestory()

    override fun toString(): String {
        return "${setting.zPlace}-${setting.zFirm}-${setting.zType} "
    }

}
