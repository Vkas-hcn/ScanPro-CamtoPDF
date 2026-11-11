package com.zppko.pdfplus.nor.ads.tool

import com.zppko.pdfplus.nor.ads.ImpressionUtils
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
// 迁移适配：com.buncead.common.pdfpro.my.BaseActivity → android.app.Activity
import android.app.Activity
// 迁移适配：B 不存在 AdJustHelp/AppConfig/FbHelp/KvUtil/Pin/Pt 在 PDF26 的 import 列表里均为未使用，
// 已移除不存在的 import（AdJustHelp/AppConfig、pdfpro.my.Util），保留其余未使用 import 原样。
import com.zppko.pdfplus.nor.ads.sett.AdsSetting
import com.scanpro.cam.pdf.analytics.AdTrackLife
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.scanpro.cam.pdf.util.AppLog

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
        AppLog.ads.w(
            "加载 位置=${setting.zPlace} 厂商=${setting.zFirm} 类型=${setting.zType} " +
                "id=${setting.zId} 结果=失败 原因=${msg.ifBlank { "无错误信息" }}",
        )
        zIsLoadingAds = false
        trackLife?.let { AdTrackingHelper.failed(it, -1, msg) }
        val next = ImpressionUtils.getNewLoadConfig(setting.zPlace, setting.zWeight)
        if (next != null) {
            setting = next
            AppLog.ads.i(
                "加载 位置=${setting.zPlace} 厂商=${setting.zFirm} 类型=${setting.zType} " +
                    "id=${setting.zId} 结果=降级重试 权重=${setting.zWeight}",
            )
            zLoadeee()
        } else {
            AppLog.ads.w("加载 位置=${setting.zPlace} 结果=放弃 原因=降级链已无可用配置")
        }
    }

    public fun sendValueToServer(adValue: Any, adSourceName: String?) {
        // 迁移适配：TradPlus 收益统一走 GlobalImpressionListener → Price.priceUsd，
        // 不再使用 AdMob OnPaidEventListener(micros)。保留空实现以避免历史调用点编译失败。
    }

    protected abstract fun childLoad()

    //广告是否过期
    open fun zisTimeOut(): Boolean {
        val zowiefparkingTime = System.currentTimeMillis() - zLoadedTimeForAds
        return zowiefparkingTime > setting.zTime * 1000L
    }

    //广告加载成功
    protected fun zmyAdsLoadSSSS(adSourceName: String? = null) {
        zIsLoadingAds = false
        zLoadedTimeForAds = System.currentTimeMillis()
        trackLife?.let { AdTrackingHelper.filled(it, adSourceName) }
        AppLog.ads.i(
            "加载 位置=${setting.zPlace} 厂商=${setting.zFirm} 类型=${setting.zType} " +
                "结果=成功 缓存=${setting.zTime}s 来源=${adSourceName?.takeIf { it.isNotBlank() } ?: "-"}",
        )
    }

    protected abstract fun zcanUsable(): Boolean



    private var zLoadedTimeForAds = 0L

    /** 本次加载的发起时间，用 [LOAD_TIMEOUT_MS] 判断回调是否已经丢失。 */
    private var zLoadStartedAt = 0L


    fun canReloadAds(): Boolean {
        // 回调丢失兜底：TradPlus 在部分情况下（例如 SDK 未初始化完成就 load）既不回调
        // onAdLoaded 也不回调 onAdFailed。没有这段，zIsLoadingAds 会永久为 true，
        // 该广告位就再也发不出请求，日志上永远只有「跳过 原因=正在加载中」。
        if (zIsLoadingAds && System.currentTimeMillis() - zLoadStartedAt > LOAD_TIMEOUT_MS) {
            val waitedSec = (System.currentTimeMillis() - zLoadStartedAt) / 1000
            zIsLoadingAds = false
            AppLog.ads.w(
                "加载 位置=${setting.zPlace} 厂商=${setting.zFirm} 类型=${setting.zType} " +
                    "结果=超时 原因=${waitedSec}s 未回调，已复位允许重发",
            )
        }
        return !zIsLoadingAds && (!zcanUsable() || zisTimeOut())
    }

    /** 是否正在加载（供池层判断"跳过加载"的原因）。 */
    fun zIsLoading(): Boolean = zIsLoadingAds

    /** 上次加载成功的时间点（0 = 从未加载成功）。 */
    fun zLoadedAt(): Long = zLoadedTimeForAds

    /** 配置的缓存有效期（毫秒）。 */
    fun zCacheTtlMs(): Long = setting.zTime * 1000L

    /** 缓存剩余有效期（毫秒），已过期返回 0。 */
    fun zCacheLeftMs(): Long =
        (setting.zTime * 1000L - (System.currentTimeMillis() - zLoadedTimeForAds)).coerceAtLeast(0L)


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
            AppLog.ads.i(
                "展示 位置=${setting.zPlace} 厂商=${setting.zFirm} 类型=${setting.zType} " +
                    "场景=$event 结果=开始",
            )
            trackLife?.let { AdTrackingHelper.impression(it) }
            childShow(act, event, whenOnVisible, finish)
        } else {
            val reason = if (act.isFinishing || act.isDestroyed) "页面已结束" else "广告不可用或已过期"
            AppLog.ads.w(
                "展示拦截 位置=${setting.zPlace} 类型=${setting.zType} 场景=$event 结果=拦截 原因=$reason",
            )
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
        val life = trackLife
        if (life != null) {
            AppLog.ads.i(
                "展示 位置=${setting.zPlace} 类型=${setting.zType} 结果=" +
                    if (life.showStartMs > 0) "关闭" else "未展示",
            )
        }
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
            zLoadStartedAt = System.currentTimeMillis()
            AppLog.ads.i(
                "加载 位置=${setting.zPlace} 厂商=${setting.zFirm} 类型=${setting.zType} " +
                    "id=${setting.zId} 权重=${setting.zWeight} 结果=开始",
            )
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

    /** TradPlus eCPM 原始串（开屏/插屏覆盖返回 `mTPAdInfo?.ecpm`），供开屏比价用。 */
    open fun getEcpm(): String = ""

    override fun toString(): String {
        return "${setting.zPlace}-${setting.zFirm}-${setting.zType} "
    }

    private companion object {
        /**
         * 加载超时兜底。TradPlus 开屏自身超时 10s，实测插屏 3~4s 内回调；
         * 超过 20s 视为 SDK 回调已丢失（例如 SDK 未初始化完成就发起请求）。
         */
        const val LOAD_TIMEOUT_MS = 20_000L
    }

}
