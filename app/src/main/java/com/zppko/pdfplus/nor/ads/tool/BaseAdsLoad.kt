package com.zppko.pdfplus.nor.ads.tool


import android.util.Log
import android.widget.FrameLayout
import com.buncead.common.ui.BaseActivity
import com.mymy.mymy.ads.AdLog
import com.mymy.mymy.ads.poi.ADBlockReason
import com.mymy.mymy.ads.poi.TrackAd
import com.mymy.mymy.ads.poi.TrackBean


import com.zppko.pdfplus.nor.ads.ImpressionUtils
import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import kotlin.Unit

abstract class BaseAdsLoad(var setting: AdsSetting) {

    /** 广告真正曝光（onAdShowed）时回调一次；由 ImpressionUtils.show 按次设置 */
    var zOnShown: (() -> Unit)? = null

    /** 子类在各自 onAdShowed 里调用，通知“广告已曝光” */
    protected fun zNotifyShown() {
        val callback = zOnShown
        zOnShown = null
        callback?.invoke()
    }

    //广告加载失败
    protected fun zmyAdsLoadFFFF(msg: String) {
        Log.e("hhhh广告", "加载失败 ${msg} ${setting.zPlace}-${setting.zFirm}-${setting.zType}-${setting.zId}")
        AdLog.log("LOAD_FAILED", setting.zPlace, setting.zId, "type=${setting.zType} msg=${msg}")
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
        AdLog.log(
            "LOAD_SUCCESS",
            setting.zPlace,
            setting.zId,
            "type=${setting.zType} caps=${setting.zDayShow}/${setting.zDayClick}"
        )
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
            AdLog.log("SHOW_ACCEPT", setting.zPlace, setting.zId, "type=${setting.zType} point=$event")
            childShow(act, event, container, whenOnVisible, finish)
        } else {
            AdLog.log(
                "SHOW_BLOCKED",
                setting.zPlace,
                setting.zId,
                "type=${setting.zType} reason=" + if (act.isHide()) "activity_hidden" else "ad_not_ready"
            )
            TrackAd.blocked(TrackBean.getTradBean(setting.zType,setting.zPlace, CodeKeyAndEvent.ddd_FIRM_TRADPLUS,setting.zId),ADBlockReason.disabled)

            zDismiss(act, whenOnVisible, finish)
        }
    }

    fun zIsShowAds(): Boolean {
        return zcanUsable() && !zisTimeOut()
    }

    /** 该广告位当天是否还有展示额度；达到上限后不再预加载下一次（各 ID 独立判定）。 */
    protected fun zHasDailyQuota(): Boolean = ImpressionUtils.canShowAd(setting.zPlace)

    private var zIsLoadingAds = false


    //关闭广告
    protected fun zDismiss(act: BaseActivity, whenOnVisible: Boolean = true, finish: () -> Unit) {
        // 已 RESUMED 立即执行；否则等下一次 RESUMED；避免关闭回调晚于 resume 时永久等待
        RunOnResumed.run(act, whenOnVisible, finish)
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
            AdLog.log(
                "LOAD_START",
                setting.zPlace,
                setting.zId,
                "type=${setting.zType} caps=${setting.zDayShow}/${setting.zDayClick}"
            )
            childLoad()
        } else {
            AdLog.log(
                "LOAD_SKIP",
                setting.zPlace,
                setting.zId,
                "type=${setting.zType} loading=$zIsLoadingAds ready=${zcanUsable()}"
            )
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