package com.scanpro.cam.pdf.ads

import android.util.Log

/**
 * 全屏广告缓存。照抄 ApolloPDF `ad/AdCache.java`：
 *
 * - 三类各**只留 1 条**（`MAX_CACHED_*_ADS = 1`）：入库前先踢旧的并 release。
 * - 取用时**先 remove 再校验 TTL**，过期就不还（等于丢弃）。
 * - TTL 取该位置列表首条的 `ad_timelimit`（秒）。
 * - 全部操作先在 [SpBlockingLogic.canShowAdByShowCountLimit] 闸下。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/AdCache.java`
 */
object SpAdCache {

    private const val TAG = "SpAdCache"
    private const val MAX_CACHED = 1

    private val openAds = ArrayDeque<SpCachedOpenAd>()
    private val interstitialAds = ArrayDeque<SpCachedInterstitialAd>()
    private val nativeAds = ArrayDeque<SpCachedNativeAd>()

    // region 入库（Apollo putOpenAd / putInterstitialAd / putNativeAd）

    fun putOpenAd(ad: SpCachedOpenAd) {
        synchronized(openAds) {
            trim(openAds)
            openAds.addLast(ad)
            Log.d(TAG, "putOpenAd: cache size = ${openAds.size}")
        }
    }

    fun putInterstitialAd(ad: SpCachedInterstitialAd) {
        synchronized(interstitialAds) {
            trimInterstitial(interstitialAds)
            interstitialAds.addLast(ad)
            Log.d(TAG, "putInterstitialAd: cache size = ${interstitialAds.size}")
        }
    }

    fun putNativeAd(ad: SpCachedNativeAd) {
        synchronized(nativeAds) {
            trimNative(nativeAds)
            nativeAds.addLast(ad)
            Log.d(TAG, "putNativeAd: cache size = ${nativeAds.size}")
        }
    }
    // endregion

    // region 取用（Apollo getOpenAd / getInterstitialAd / getNativeAd）

    /**
     * 取开屏。**出队后校验 TTL**：`now - createTime < ac_launch 首条 adTimeLimit * 1000`。
     * 过期或列表没配 → 返回 null（该条已在出队时丢弃）。
     */
    fun getOpenAd(config: SpAdConfig, blocking: SpBlockingLogic): SpCachedOpenAd? {
        if (!blocking.canShowAdByShowCountLimit()) return null
        val ad = synchronized(openAds) { openAds.removeLastOrNull() } ?: return null
        val ttl = config.ttlSecondsOf(SpAdPosition.AC_LAUNCH) ?: return null
        return if (ad.isFresh(ttl)) ad else null
    }

    /** 取插屏，TTL 取 [position] 的列表首条。 */
    fun getInterstitialAd(
        position: String,
        config: SpAdConfig,
        blocking: SpBlockingLogic,
    ): SpCachedInterstitialAd? {
        if (!blocking.canShowAdByShowCountLimit()) return null
        val ad = synchronized(interstitialAds) { interstitialAds.removeLastOrNull() } ?: return null
        val ttl = config.ttlSecondsOf(position) ?: return null
        return if (ad.isFresh(ttl)) ad else null
    }

    /** 取原生，TTL 取 [position] 的列表首条。 */
    fun getNativeAd(
        position: String,
        config: SpAdConfig,
        blocking: SpBlockingLogic,
    ): SpCachedNativeAd? {
        if (!blocking.canShowAdByShowCountLimit()) return null
        val ad = synchronized(nativeAds) { nativeAds.removeLastOrNull() } ?: return null
        val ttl = config.ttlSecondsOf(position) ?: return null
        return if (ad.isFresh(ttl)) ad else null
    }
    // endregion

    // region 探测（Apollo isHaveOpenAd / isHaveEnableInterstitialAd / isHaveEnableNativeAd）

    /** 不取用，只看队尾那条是否还有效。 */
    fun isHaveOpenAd(config: SpAdConfig): Boolean {
        val ad = synchronized(openAds) { openAds.lastOrNull() } ?: return false
        val ttl = config.ttlSecondsOf(SpAdPosition.AC_LAUNCH) ?: return false
        return ad.isFresh(ttl)
    }

    fun isHaveEnableInterstitialAd(position: String, config: SpAdConfig): Boolean {
        val ad = synchronized(interstitialAds) { interstitialAds.lastOrNull() } ?: return false
        val ttl = config.ttlSecondsOf(position) ?: return false
        return ad.isFresh(ttl)
    }

    fun isHaveEnableNativeAd(position: String, config: SpAdConfig): Boolean {
        val ad = synchronized(nativeAds) { nativeAds.lastOrNull() } ?: return false
        val ttl = config.ttlSecondsOf(position) ?: return false
        return ad.isFresh(ttl)
    }
    // endregion

    /** 清空全部（[SpAdsHub] 在配置换了一版时调用）。 */
    fun clearAll() {
        synchronized(openAds) { openAds.clear() }
        synchronized(interstitialAds) { interstitialAds.clear() }
        synchronized(nativeAds) {
            nativeAds.forEach { it.releaseNative() }
            nativeAds.clear()
        }
    }

    private fun trim(queue: ArrayDeque<SpCachedOpenAd>) {
        while (queue.size >= MAX_CACHED) queue.removeFirstOrNull()
    }

    private fun trimInterstitial(queue: ArrayDeque<SpCachedInterstitialAd>) {
        while (queue.size >= MAX_CACHED) queue.removeFirstOrNull()
    }

    private fun trimNative(queue: ArrayDeque<SpCachedNativeAd>) {
        while (queue.size >= MAX_CACHED) queue.removeFirstOrNull()?.releaseNative()
    }
}
