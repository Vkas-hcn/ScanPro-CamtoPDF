package com.zppko.pdfplus.nor.ads.trad

abstract class TradLoadCallback {
    abstract fun onAdLoaded()

    abstract fun onAdFailedToLoad(code:Int, message:String)
}
