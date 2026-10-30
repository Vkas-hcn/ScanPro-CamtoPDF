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
    /** 这条广告是为哪个位置加载的（`ac_*`）。取用时按它自己的 TTL 判断。 */
    val position: String = "",
    /** 入库时固化的 TTL（秒）。0 表示不按时间过期。 */
    val ttlSeconds: Int = 0,
) {
    /** 缓存年龄（毫秒）。 */
    fun ageMs(now: Long = System.currentTimeMillis()): Long = now - createTime

    /**
     * TTL 判据：`now - createTime < adTimeLimit * 1000`。
     *
     * `ttlSeconds <= 0` 视为「不按时间过期」。旧实现直接返回 false，后端漏发 `ad_timelimit`
     * 时每条已填充广告都会在出队瞬间被判失效。
     */
    fun isFresh(ttlSeconds: Int, now: Long = System.currentTimeMillis()): Boolean =
        ttlSeconds <= 0 || ageMs(now) < ttlSeconds * 1000L

    /** 用入库时固化的 TTL 判断是否仍然可用。 */
    fun isFreshNow(now: Long = System.currentTimeMillis()): Boolean = isFresh(ttlSeconds, now)
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
