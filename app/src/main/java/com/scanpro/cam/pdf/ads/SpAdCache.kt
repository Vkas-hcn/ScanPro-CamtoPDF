package com.scanpro.cam.pdf.ads

import android.util.Log

/**
 * 全屏广告缓存。照抄 ApolloPDF `ad/AdCache.java`，但修正了两处会白丢填充的行为：
 *
 * - **先校验 TTL 再出队**：旧实现先 `removeLastOrNull()` 再判 TTL，过期或位置没配时
 *   广告已经出队，等于「填充成功但永远不会展示」。
 * - **每类留 2 条**（Apollo 是 1 条）：插屏/原生是跨位置共用一个池，只留 1 条时，
 *   一个位置的新填充会把另一个位置还没展示的广告挤掉。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/AdCache.java`
 */
object SpAdCache {

    private const val TAG = "SpAdCache"
    private const val MAX_CACHED = 2

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
            trim(interstitialAds)
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

    // region 探测（不出队）

    /** 只看有没有可用开屏，不取用。等待「广告就绪」时用。 */
    fun peekOpenAd(config: SpAdConfig, blocking: SpBlockingLogic): SpCachedOpenAd? {
        return synchronized(openAds) {
            if (!blocking.canShowAdByShowCountLimit()) return null
            val ad = openAds.lastOrNull() ?: return null
            if (!isFresh(ad, config, SpAdPosition.AC_LAUNCH)) {
                openAds.removeLast()
                return null
            }
            ad
        }
    }

    fun peekInterstitialAd(
        position: String,
        config: SpAdConfig,
        blocking: SpBlockingLogic,
    ): SpCachedInterstitialAd? {
        return synchronized(interstitialAds) {
            if (!blocking.canShowAdByShowCountLimit()) return null
            val ad = interstitialAds.lastOrNull() ?: return null
            if (!isFresh(ad, config, position)) {
                interstitialAds.removeLast()
                return null
            }
            ad
        }
    }

    fun peekNativeAd(
        position: String,
        config: SpAdConfig,
        blocking: SpBlockingLogic,
    ): SpCachedNativeAd? {
        return synchronized(nativeAds) {
            if (!blocking.canShowAdByShowCountLimit()) return null
            val ad = nativeAds.lastOrNull() ?: return null
            if (!isFresh(ad, config, position)) {
                nativeAds.removeLast()?.releaseNative()
                return null
            }
            ad
        }
    }
    // endregion

    // region 取用（先校验后出队）

    /** 取开屏：TTL 通过才出队，否则丢弃并返回 null。 */
    fun getOpenAd(config: SpAdConfig, blocking: SpBlockingLogic): SpCachedOpenAd? {
        return synchronized(openAds) {
            if (!blocking.canShowAdByShowCountLimit()) return null
            val ad = openAds.lastOrNull() ?: return null
            if (!isFresh(ad, config, SpAdPosition.AC_LAUNCH)) {
                openAds.removeLast()
                return null
            }
            openAds.removeLast()
            ad
        }
    }

    /** 取插屏，TTL 取 [position] 的列表首条；没配就退回入库时固化的 TTL。 */
    fun getInterstitialAd(
        position: String,
        config: SpAdConfig,
        blocking: SpBlockingLogic,
    ): SpCachedInterstitialAd? {
        return synchronized(interstitialAds) {
            if (!blocking.canShowAdByShowCountLimit()) return null
            val ad = interstitialAds.lastOrNull() ?: return null
            if (!isFresh(ad, config, position)) {
                interstitialAds.removeLast()
                return null
            }
            interstitialAds.removeLast()
            ad
        }
    }

    /** 取原生，TTL 取 [position] 的列表首条；没配就退回入库时固化的 TTL。 */
    fun getNativeAd(
        position: String,
        config: SpAdConfig,
        blocking: SpBlockingLogic,
    ): SpCachedNativeAd? {
        return synchronized(nativeAds) {
            if (!blocking.canShowAdByShowCountLimit()) return null
            val ad = nativeAds.lastOrNull() ?: return null
            if (!isFresh(ad, config, position)) {
                nativeAds.removeLast()?.releaseNative()
                return null
            }
            nativeAds.removeLast()
            ad
        }
    }
    // endregion

    // region 探测（Apollo isHaveOpenAd / isHaveEnableInterstitialAd / isHaveEnableNativeAd）

    /** 不取用，只看队尾那条是否还有效。 */
    fun isHaveOpenAd(config: SpAdConfig): Boolean = peekWithoutGate(openAds, config) != null

    fun isHaveEnableInterstitialAd(position: String, config: SpAdConfig): Boolean =
        peekWithoutGate(interstitialAds, config, position) != null

    fun isHaveEnableNativeAd(position: String, config: SpAdConfig): Boolean =
        peekWithoutGate(nativeAds, config, position) != null
    // endregion

    /**
     * 配置变化时只清理“按入库 TTL 已过期”的广告，未过期的一律保留。
     *
     * 旧实现是 `clearAll()`：每次远端配置下发都会把刚填充、还没展示的广告扔掉。
     * 这里按条目自带的 [SpCachedAd.ttlSeconds] 判定，配置里的 TTL 变化不影响已加载广告；
     * 真正的取用仍由 [peekOpenAd] / [getOpenAd] 等按当前配置再做一次校验。
     */
    fun clearExpired() {
        synchronized(openAds) {
            openAds.removeAll { !it.isFreshNow() }
        }
        synchronized(interstitialAds) {
            interstitialAds.removeAll { !it.isFreshNow() }
        }
        val expiredNative = ArrayList<SpCachedNativeAd>()
        synchronized(nativeAds) {
            nativeAds.removeAll { ad ->
                val expired = !ad.isFreshNow()
                if (expired) expiredNative += ad
                expired
            }
        }
        expiredNative.forEach { it.releaseNative() }
    }

    private fun <T> isFresh(ad: SpCachedAd<T>, config: SpAdConfig, position: String): Boolean {
        val fromConfig = config.ttlSecondsOf(position)
        return if (fromConfig != null) ad.isFresh(fromConfig) else ad.isFreshNow()
    }

    private fun <T> peekWithoutGate(
        queue: ArrayDeque<SpCachedAd<T>>,
        config: SpAdConfig,
        position: String = SpAdPosition.AC_LAUNCH,
    ): SpCachedAd<T>? {
        return synchronized(queue) {
            val ad = queue.lastOrNull() ?: return null
            if (!isFresh(ad, config, position)) {
                queue.removeLast()
                return null
            }
            ad
        }
    }

    private fun <T> trim(queue: ArrayDeque<SpCachedAd<T>>) {
        while (queue.size >= MAX_CACHED) queue.removeFirstOrNull()
    }

    private fun trimNative(queue: ArrayDeque<SpCachedNativeAd>) {
        while (queue.size >= MAX_CACHED) queue.removeFirstOrNull()?.releaseNative()
    }
}
