package com.zppko.pdfplus.nor.ads.trad


abstract class TradShowCallback {
    abstract fun onAdShowed()
    abstract fun onAdClicked()
    abstract fun onAdDismissed()
    abstract fun onAdFailedToShow()
}