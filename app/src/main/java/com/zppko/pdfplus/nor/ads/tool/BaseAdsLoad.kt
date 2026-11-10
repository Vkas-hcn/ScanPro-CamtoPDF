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
import com.scanpro.cam.pdf.analytics.AdTrackLife
import com.scanpro.cam.pdf.analytics.AdTrackingHelper

abstract class BaseAdsLoad(var setting: AdsSetting) {

    /** 当前广告请求-展示的生命周期上下文；广告全链路埋点（E05–E09）用。 */
    protected var trackLife: AdTrackLife? = null

    /** 配置里的 zType（op/int/nat/banner）→ 规范 §2.3 的 ad_type。 */
    private fun specAdType(): String = when (setting.zType) {
        "op" -> AdTrackingHelper.AdType.OPEN
        "int" -> AdTrackingHelper.AdType.INTERSTITIAL
        "nat" -> AdTrackingHelper.AdType.NATIVE
        "banner" -> AdTrackingHelper.AdType.BANNER
        else -> setting.zType
    }

    //广告加载失败
    protected fun zmyAdsLoadFFFF(msg: String) {
        Log.e("hhhh广告", "加载失败 ${msg} ${setting.zPlace}-${setting.zFirm}-${setting.zType}-${setting.zId}",)
        zIsLoadingAds = false
        trackLife?.let { AdTrackingHelper.failed(it, -1, msg) }
        val setting = ImpressionUtils.getNewLoadConfig(setting.zPlace, setting.zWeight)
        if (setting != null) {
            this.setting = setting
            zLoadeee()
        }
    }

    public fun sendValueToServer(adValue: com.google.android.gms.ads.AdValue, adSourceName: String?) {
        Price.qqq(adValue.valueMicros, adValue.currencyCode, setting.zType, setting.zPlace, setting.zId, adSourceName)
    }

    protected abstract fun childLoad()

    //广告是否过期
    open fun zisTimeOut(): Boolean {
        val zowiefparkingTime = System.currentTimeMillis() - zLoadedTimeForAds
        return zowiefparkingTime > setting.zTime * 1000L
    }

    //广告加载成功
    protected fun zmyAdsLoadSSSS(responseInfo: com.google.android.gms.ads.ResponseInfo? = null) {
        Log.e("hhhh广告", "加载成功 ${setting.zPlace}-${setting.zFirm}-${setting.zType} ")
        zIsLoadingAds = false
        zLoadedTimeForAds = System.currentTimeMillis()
        trackLife?.let { AdTrackingHelper.filled(it, responseInfo) }
    }

    protected abstract fun zcanUsable(): Boolean



    private var zLoadedTimeForAds = 0L


    fun canReloadAds(): Boolean {
        return !zIsLoadingAds && (!zcanUsable() || zisTimeOut())
    }


    /** 原生渲染层展示时调用：原生不走 zShoweee，需单独记录展示起点。 */
    fun trackNativeImpression() {
        trackLife?.let { AdTrackingHelper.impression(it) }
    }

    /** 原生渲染层关闭时调用：上报 ad_close（仅真正展示过才算）。 */
    fun trackNativeClose() {
        trackLife?.let { life -> if (life.showStartMs > 0) AdTrackingHelper.close(life) }
        trackLife = null
    }

    open fun zShoweee(act: Activity, event: String, whenOnVisible: Boolean = true, finish: () -> Unit) {
        // 迁移适配：act.isHide() → 页面未结束可用
        if (!(act.isFinishing || act.isDestroyed) && zcanUsable()) {
            Log.e("hhhh广告", "展示广告 ${setting.zPlace}-${setting.zFirm}-${setting.zType}")
            trackLife?.let { AdTrackingHelper.impression(it) }
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
        // 只有真正展示过（记录过 showStartMs）才上报 ad_close，避免「未展示也报关闭」。
        trackLife?.let { life -> if (life.showStartMs > 0) AdTrackingHelper.close(life) }
        trackLife = null
    }


    open fun zLoadeee() {
        if (!zIsLoadingAds && !zcanUsable()) {
            zIsLoadingAds = true
            Log.e("hhhh广告", "开始加载广告 ${setting.zPlace}-${setting.zFirm}-${setting.zType}-${setting.zId}",)
            trackLife = AdTrackingHelper.begin(specAdType(), setting.zPlace, setting.zId)
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
