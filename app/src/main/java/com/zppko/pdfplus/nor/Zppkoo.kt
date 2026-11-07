package com.zppko.pdfplus.nor

import android.app.Application
import android.content.Context
import com.mymy.mymy.rece.HomeRecentsHelp
import com.zppko.pdfplus.nor.ads.tool.SpUtil
import com.zppko.pdfplus.nor.horus.HintEx06
import com.zppko.pdfplus.nor.horus.HintExt01
import com.zppko.pdfplus.nor.horus.HintExt02
import com.zppko.pdfplus.nor.horus.HintExt03
import com.zppko.pdfplus.nor.horus.HintExt05
import com.zppko.pdfplus.nor.remote.RemoteConfigHelper

class Zppkoo {

    companion object {
        lateinit var ii: Context
    }

    fun onCreate(app: Application) {
        ii = app
        SpUtil.init(app)
        // B owns AdMob/Adjust/TrackSDK initialization. Do not initialize A's TradPlus SDK.
        // A's UserReferrer.init() starts BiCloak/BiRemoteConfig; B's dispatch owns that chain.
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
}
