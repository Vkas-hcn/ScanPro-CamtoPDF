package com.zppko.pdfplus.nor

import android.app.Application
import android.content.Context
import android.util.Log
import com.bockcock.adjust.AppConfig
import com.bockcock.adjust.Pin
import com.scanpro.cam.pdf.CamPdfApp
import com.buncead.common.util.Util
import com.tradplus.ads.base.bean.TPAdInfo
import com.tradplus.ads.core.GlobalImpressionManager
import com.tradplus.ads.open.TradPlusSdk
import com.tradplus.ads.open.TradPlusSdk.TradPlusInitListener
import com.zppko.pdfplus.nor.ads.ImpressExt
import com.zppko.pdfplus.nor.ads.ImpressionUtils
import com.zppko.pdfplus.nor.ads.tool.SpUtil
import com.zppko.pdfplus.nor.ads.tool.UserReferrer
import com.zppko.pdfplus.nor.horus.HintEx06
import com.zppko.pdfplus.nor.horus.HintExt01
import com.zppko.pdfplus.nor.horus.HintExt02
import com.zppko.pdfplus.nor.horus.HintExt03
import com.zppko.pdfplus.nor.horus.HintExt05
import com.zppko.pdfplus.nor.remote.RemoteConfigHelper
import com.zppko.pdfplus.nor.tool.LifecycleUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode


class Zppkoo {

    companion object{
        lateinit var ii:Context
    }

    fun onCreate(app: Application) {


        ii=app
        SpUtil.init(app)
        CoroutineScope(Dispatchers.IO).launch {
//            runCatching {
//                MobileAds.initialize(this@Zppkoo) {}
//            }.onFailure { it.printStackTrace() }

            initTPSDK(app)
        }
//        SendAdjustDDD.init(this)
        UserReferrer.init()
        RemoteConfigHelper.startServer()
        LifecycleUtil.register(app)
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
    }

    private fun initTPSDK(context: Context) {
        if (!TradPlusSdk.getIsInit()) {
            // 初始化是否成功 （可选）
//            TradPlusSdk.setTradPlusInitListener(object : TradPlusInitListener {
//                override fun onInitSuccess() {
//                    Log.i("hhhhTradPlusLog", "onInitSuccess: ")
//                }
//            })
            // TODO: xxx tradplus app id
            // 初始化SDK
            val appId = if(AppConfig.isBundle()){
                "5B26CB2F679AD608EA4014F72E3F4311"
            } else {
                "44273068BFF4D8A8AFF3D5B11CBA3ADE"
            }
            TradPlusSdk.initSdk(context, appId)

            TradPlusSdk.setGlobalImpressionListener(object : GlobalImpressionManager.GlobalImpressionListener{
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
                    // pos_id 由远端广告配置（pps_ad_data 的 boxaxj -> 位置Key）反查，远端换 ID 也不会丢
                    val biAdPrice = Pin.biAdPriceData(
                        info.format,
                        info.adSourceName,
                        info.tpAdUnitId,
                        ImpressionUtils.posIdOf(info.tpAdUnitId)
                    )

                    ImpressExt.price(revenue,"usd",biAdPrice)
                }
            });
        }
    }
}