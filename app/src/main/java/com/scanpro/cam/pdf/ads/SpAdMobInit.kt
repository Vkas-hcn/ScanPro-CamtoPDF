package com.scanpro.cam.pdf.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MobileAds 初始化。照抄 ApolloPDF `ad/AdMobInit.java`：
 *
 * - 一次性的 `MobileAds.initialize`，成功后置 `isMobileAdsInitialize = true`；
 * - 全栈（加载 / 展示 / 保活）都用这个 flag 当「SDK 是否可用」的前置条件。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/AdMobInit.java`
 *
 * 差异：Apollo 在 `RequestConfiguration` 里硬编码了测试机 ID，这里改用 [SpAdDevFlags]
 * 控制是否走官方测试单元，不在源码里留设备 ID。
 */
class SpAdMobInit private constructor(private val appContext: Context) {

    @Volatile
    private var initialized = false

    fun isMobileAdsInitialize(): Boolean = initialized

    /**
     * @param onReady SDK 初始化完成回调。旧实现在这里只打日志，导致「配置先到、SDK 后到」
     *   时首轮预加载被跳过后再也不会重试。
     */
    fun init(scope: CoroutineScope, onReady: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(if (SpAdDevFlags.preferTestUnits()) TEST_DEVICE_IDS else emptyList())
                        .build(),
                )
                MobileAds.initialize(appContext) {
                    initialized = true
                    AppLog.ads.i("广告SDK初始化完成")
                    SpTracker.log(appContext, SpTracker.AD_SDK_READY)
                    runCatching { onReady() }
                        .onFailure { AppLog.ads.w("广告SDK就绪回调失败", it) }
                }
            }.onFailure { AppLog.ads.w("广告SDK初始化失败", it) }
        }
    }

    companion object {
        /** 官方文档给的通用测试机 ID。仅在 [SpAdDevFlags] 打开时下发。 */
        private val TEST_DEVICE_IDS = listOf("B3EEABB8EE11C2BE770B684D95219ECB")

        @Volatile
        private var instance: SpAdMobInit? = null

        fun get(context: Context): SpAdMobInit =
            instance ?: synchronized(this) {
                instance ?: SpAdMobInit(context.applicationContext).also { instance = it }
            }

        fun getOrNull(): SpAdMobInit? = instance
    }
}
