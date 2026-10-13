package com.scanpro.cam.pdf.ads

import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd

/**
 * 缓存里的全屏广告条目。对应 ApolloPDF `ad/bean/AppOpenAdBean.java` +
 * `ad/bean/InterstitialAdBean.java` + `ad/bean/NativeAdBean.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/bean/`
 *
 * 三个类结构完全一样：`(ad 对象, createTime, sessionId)`。这里用泛型收敛成一个类，
 * 并保留 Apollo 的 `release()` 语义。
 */
data class SpCachedAd<T>(
    val ad: T,
    /** 入库时间戳（毫秒）。Apollo 用 `System.currentTimeMillis()`。 */
    val createTime: Long,
    /** Apollo 的 `sessionId`，用来做加载重试计数与失败清理。 */
    val sessionId: String,
) {
    /** 缓存年龄（毫秒）。 */
    fun ageMs(now: Long = System.currentTimeMillis()): Long = now - createTime

    /**
     * Apollo 的 TTL 判据：`now - createTime < adTimeLimit * 1000`，`adTimeLimit` 取
     * 该位置列表**首条**的 `ad_timelimit`。
     */
    fun isFresh(ttlSeconds: Int, now: Long = System.currentTimeMillis()): Boolean =
        ageMs(now) < ttlSeconds * 1000L
}

typealias SpCachedOpenAd = SpCachedAd<AppOpenAd>
typealias SpCachedInterstitialAd = SpCachedAd<InterstitialAd>
typealias SpCachedNativeAd = SpCachedAd<NativeAd>

/**
 * 释放实现：Apollo 三个 Bean 的 `release()` 只是把引用置空 / 调 SDK 的 destroy。
 * GMA 的 [NativeAd] 需要显式 `destroy()`；开屏与插屏没有 destroy。
 */
internal fun SpCachedNativeAd.releaseNative() {
    runCatching { ad.destroy() }
}
