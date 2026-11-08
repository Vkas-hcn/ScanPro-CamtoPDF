package com.bockcock.adjust

import com.facebook.appevents.AppEventsLogger
import com.flux.tracksdk.core.TrackSDK
import java.util.Currency

object FbHelp  {


    fun facebook_revenue(value: Double,currency: String){
        val context = TrackSDK.getContext()
        if (context != null) {
            try {
                AppEventsLogger.newLogger(context).logPurchase(value.toBigDecimal(), Currency.getInstance(currency))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}
