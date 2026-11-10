package com.zppko.pdfplus.nor

import android.app.Application
import android.content.Context
import android.util.Log
import com.mymy.mymy.rece.HomeRecentsHelp
import com.tradplus.ads.base.bean.TPAdInfo
import com.tradplus.ads.core.GlobalImpressionManager
import com.tradplus.ads.open.TradPlusSdk
import com.zppko.pdfplus.nor.ads.ImpressionUtils
import com.zppko.pdfplus.nor.ads.tool.Price
import com.zppko.pdfplus.nor.ads.tool.SpUtil
import com.zppko.pdfplus.nor.horus.HintEx06
import com.zppko.pdfplus.nor.horus.HintExt01
import com.zppko.pdfplus.nor.horus.HintExt02
import com.zppko.pdfplus.nor.horus.HintExt03
import com.zppko.pdfplus.nor.horus.HintExt05
import com.zppko.pdfplus.nor.remote.RemoteConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class Zppkoo {

    companion object {
        lateinit var ii: Context

        /** TradPlus AppId（测试）。 */
        private const val TRADPLUS_APP_ID = "44273068BFF4D8A8AFF3D5B11CBA3ADE"
    }

    fun onCreate(app: Application) {
        ii = app
        SpUtil.init(app)
        CoroutineScope(Dispatchers.IO).launch {
            initTPSDK(app)
        }
        // B owns Adjust/TrackSDK initialization (CamPdfApp.initSDK). B's dispatch owns the BI chain.
        RemoteConfigHelper.startServer()
        // Do not register A's LifecycleUtil: B retains SpHotStartGate/SpAdSession.
        runCatching {
            HintExt02.init(app)
            HintExt03.init(app)

            HintExt05.markLing()
        }.onFailure { it.printStackTrace() }
        HintExt01.startJob(app)
//        EVenPlusUploader.getInfo()

        runCatching {
            HintEx06().start()
        }.onFailure { it.printStackTrace() }

        // Cache may already qualify; a late dispatch/Adjust result retries this same registration.
        runCatching {
            HomeRecentsHelp().registerHomeRecentsReceiver(app)
        }.onFailure { it.printStackTrace() }
    }

    private fun initTPSDK(context: Context) {
        if (!TradPlusSdk.getIsInit()) {
            // 初始化 SDK（照 SmartPdfScan_luow：无 init 回调，无自动加载）
            TradPlusSdk.initSdk(context, TRADPLUS_APP_ID)

            TradPlusSdk.setGlobalImpressionListener(object : GlobalImpressionManager.GlobalImpressionListener {
                override fun onImpressionSuccess(info: TPAdInfo?) {
                    if (info == null) {
                        return
                    }

                    // TradPlus 的 eCPM，单位通常为美元/千次展示
                    var ecpm = 0.0
                    try {
                        if (info.ecpm != null) {
                            ecpm = info.ecpm.toDouble()
                        }
                    } catch (e: NumberFormatException) {
                        Log.e("AdRevenue", "Invalid eCPM", e)
                        return
                    }

                    // 单次展示预估收益
                    val revenue = BigDecimal.valueOf(ecpm).divide(BigDecimal.valueOf(1000), 6, RoundingMode.DOWN).toDouble()
                    val adUnitId = info.tpAdUnitId.orEmpty()
                    val place = ImpressionUtils.placeForAdId(adUnitId)
                    val zType = ImpressionUtils.adTypeForPlace(place)
                    Price.priceUsd(
                        revenue,
                        "USD",
                        zType.orEmpty(),
                        place.orEmpty(),
                        adUnitId,
                        info.adSourceName,
                    )
                }
            })
        }
    }
}
