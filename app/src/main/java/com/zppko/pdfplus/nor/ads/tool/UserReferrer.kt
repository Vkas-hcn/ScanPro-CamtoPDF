package com.zppko.pdfplus.nor.ads.tool

import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.mymy.mymy.http.BiCloak
import com.mymy.mymy.http.BiRemoteConfig
import com.zppko.pdfplus.nor.Zppkoo
//import com.zenlytic.clean.master.ads.helper.EVenPlusUploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object UserReferrer {

    private var retry=0


    fun init(){
        if (ouwheofwnoSaveReferrer()){
            joiefesyncGet()
        }else{
            BiCloak.sendRequest()
            BiRemoteConfig.sendRequest()
        }
    }

    val icnoefg="h85fg6sd3cs"
    private fun ouwheofwnoSaveReferrer():Boolean{
        return SpUtil.get(icnoefg,"")==""
    }

    private fun owenfoiweretry(){
        if (ouwheofwnoSaveReferrer()){
            retry++
            CoroutineScope(Dispatchers.IO).launch {
                delay(if (retry >=5) 600_000 else 1000)
                withContext(Dispatchers.Main){
                    joiefesyncGet()
                }
            }
        }
    }


    private fun joiefesyncGet(){
            val referrerClient = InstallReferrerClient.newBuilder(Zppkoo.Companion.ii).build()
            referrerClient.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(code: Int) {
                    if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                        runCatching {
                            val details=referrerClient.installReferrer
                            val referrerUrl=details.installReferrer
                            SpUtil.put(icnoefg,referrerUrl)
//                            EVenPlusUploader.iiii(details)

                            BiRemoteConfig.sendRequest()
                            BiCloak.send()
                        }
                    }
                    referrerClient.endConnection()
                }
                override fun onInstallReferrerServiceDisconnected() {
                    owenfoiweretry()
                }
            })

    }

}