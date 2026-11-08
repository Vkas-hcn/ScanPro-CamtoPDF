package com.zppko.pdfplus.nor.ads.tool

import android.app.Activity
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object OmUtil {
    fun start(act: Activity, close: () -> Unit) {
        if (UserParms.isOm()) {
            val xidinformation = UserMessagingPlatform.getConsentInformation(act.applicationContext)
//            val debugSettings = ConsentDebugSettings.Builder(act.applicationContext)
//                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
//                    .addTestDeviceHashedId("7AAE3687219177B917284E0A4235E323")
//                    .build()

            val valueddd = ConsentRequestParameters.Builder()
//                    .setConsentDebugSettings(debugSettings)
                    .build()

            xidinformation.requestConsentInfoUpdate(act, valueddd, {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(act) {
                        close.invoke()
                    }
                },
                {
                    close.invoke()
                },
            )
        } else {
            close.invoke()
        }
    }
}
