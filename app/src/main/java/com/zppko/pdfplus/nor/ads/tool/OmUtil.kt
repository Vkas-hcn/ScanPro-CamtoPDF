package com.zppko.pdfplus.nor.ads.tool

import android.app.Activity
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object OmUtil {
    fun start(act: Activity, close: () -> Unit) {
        if (UserParms.isOm()) {
            val xidinformation = UserMessagingPlatform.getConsentInformation(act.applicationContext)
            val debugSettings = ConsentDebugSettings.Builder(act.applicationContext)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId("C5FC54B05B21DFAC5282A81E74DD93FA")
                    .build()

            val valueddd = ConsentRequestParameters.Builder()
                    .setConsentDebugSettings(debugSettings)
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
