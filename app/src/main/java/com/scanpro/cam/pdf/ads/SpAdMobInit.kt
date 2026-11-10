package com.scanpro.cam.pdf.ads

import android.content.Context
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.util.AppLog
import com.tradplus.ads.open.TradPlusSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 广告 SDK 就绪判定。SDK 内核由 AdMob MobileAds 换成 TradPlus：
 * - TradPlus 初始化在 `Zppkoo.onCreate` 的 IO 协程里执行（TradPlusSdk.initSdk + 收益监听）；
 * - 本类只负责「等 TradPlus 初始化完成」并回调 onReady，供开屏预加载补拉使用。
 * 类名保留 SpAdMobInit 以最小化调用点改动。
 */
class SpAdMobInit private constructor(private val appContext: Context) {

    @Volatile
    private var initialized = false

    fun isMobileAdsInitialize(): Boolean = initialized

    /**
     * @param onReady SDK 初始化完成回调（用于补拉因 SDK 未就绪被推迟的开屏预加载）。
     */
    fun init(scope: CoroutineScope, onReady: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            var attempts = 0
            while (!TradPlusSdk.getIsInit() && attempts < INIT_WAIT_ATTEMPTS) {
                delay(INIT_POLL_MS)
                attempts++
            }
            initialized = TradPlusSdk.getIsInit()
            if (initialized) {
                AppLog.ads.i("广告SDK初始化完成")
                SpTracker.log(appContext, SpTracker.AD_SDK_READY)
                runCatching { onReady() }
                    .onFailure { AppLog.ads.w("广告SDK就绪回调失败", it) }
            } else {
                AppLog.ads.w("广告SDK初始化未就绪（TradPlus 未初始化）")
            }
        }
    }

    companion object {
        private const val INIT_POLL_MS = 100L
        private const val INIT_WAIT_ATTEMPTS = 100

        @Volatile
        private var instance: SpAdMobInit? = null

        fun get(context: Context): SpAdMobInit =
            instance ?: synchronized(this) {
                instance ?: SpAdMobInit(context.applicationContext).also { instance = it }
            }

        fun getOrNull(): SpAdMobInit? = instance
    }
}
