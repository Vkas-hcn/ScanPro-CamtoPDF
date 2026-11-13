package com.zppko.pdfplus.nor.ads.tool


import android.util.Log
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.buncead.common.ui.BaseActivity
import com.mymy.mymy.ads.poi.ADBlockReason
import com.mymy.mymy.ads.poi.TrackAd
import com.mymy.mymy.ads.poi.TrackBean


import com.zppko.pdfplus.nor.ads.ImpressionUtils
import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import kotlin.Unit

abstract class BaseAdsLoad(var setting: AdsSetting) {

    //广告加载失败
    protected fun zmyAdsLoadFFFF(msg: String) {
        Log.e("hhhh广告", "加载失败 ${msg} ${setting.zPlace}-${setting.zFirm}-${setting.zType}-${setting.zId}")
        zIsLoadingAds = false
    }

    protected abstract fun childLoad()

    //广告是否过期
    open fun zisTimeOut(): Boolean { // tradplus 自动加载没有过期的说法
        return false
//        val zowiefparkingTime = System.currentTimeMillis() - zLoadedTimeForAds
//        return zowiefparkingTime > setting.zTime * 1000L
    }

    //广告加载成功
    protected fun zmyAdsLoadSSSS() {
        Log.e("hhhh广告", "加载成功 ${setting.zPlace}-${setting.zFirm}-${setting.zType}-${setting.zId}")
        zIsLoadingAds = false
        zLoadedTimeForAds = System.currentTimeMillis()
    }

    protected abstract fun zcanUsable(): Boolean



    private var zLoadedTimeForAds = 0L


    fun canReloadAds(): Boolean {
        return !zIsLoadingAds && (!zcanUsable() || zisTimeOut())
    }


    open fun zShoweee(act: BaseActivity, event: String, container: FrameLayout, whenOnVisible: Boolean = true, finish: () -> Unit) {
        if (!act.isHide() && zcanUsable()) {
            Log.e("hhhh广告", "展示广告 ${setting.zPlace}-${setting.zFirm}-${setting.zType}-${setting.zId}")
            childShow(act, event, container, whenOnVisible, finish)
        } else {
            TrackAd.blocked(TrackBean.getTradBean(setting.zType,setting.zPlace, CodeKeyAndEvent.ddd_FIRM_TRADPLUS,setting.zId),ADBlockReason.disabled)

            zDismiss(act, whenOnVisible, finish)
        }
    }

    fun zIsShowAds(): Boolean {
        return zcanUsable() && !zisTimeOut()
    }

    private var zIsLoadingAds = false


    //关闭广告
    protected fun zDismiss(act: BaseActivity, whenOnVisible: Boolean = true, finish: () -> Unit) {
        if (whenOnVisible) {
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    act.lifecycle.removeObserver(this)
                    finish.invoke()
                }
            }
            act.lifecycle.addObserver(observer)
        } else {
            finish.invoke()
        }
    }

    //距离广告过期还有多久
    fun remainingTime(): Long {
        val time = setting.zTime * 1000L - (System.currentTimeMillis() - zLoadedTimeForAds)
        return maxOf(0L, time)
    }

    open fun zLoadeee() {
        if (!zIsLoadingAds && !zcanUsable()) {
            zIsLoadingAds = true
            Log.e("hhhh广告", "开始加载广告 ${setting.zPlace}-${setting.zFirm}-${setting.zType}-${setting.zId}",)
            childLoad()
        }
    }

    protected abstract fun childShow(
        act: BaseActivity,
        event: String,
        container: FrameLayout,
        whenOnVisible: Boolean = true,
        finish: () -> Unit
    )

    abstract fun zAdsDestory()

    override fun toString(): String {
        return "${setting.zPlace}-${setting.zFirm}-${setting.zType} "
    }


    open fun getEcpm(): String {
        return ""
    }
}