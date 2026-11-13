package com.mymy.mymy.http

import com.bockcock.adjust.AppConfig
import com.bockcock.adjust.KvUtil
import com.buncead.common.util.Util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

object BiCloak {

    val TAG = "BiCloak_help"


    var mColorValue = ""
    val KEY_COLOR_VALUE = "KEY_COLOR_VALUE_ARC"
    fun sendRequest(){
        val arcColorValue = KvUtil.getString(KEY_COLOR_VALUE)
        if (arcColorValue.isNullOrEmpty()){
            Util.iii { "$TAG ColorValue111=${arcColorValue}" }
            send()
        }else{
            Util.iii { "$TAG ColorValue222=${arcColorValue}" }
            mColorValue = arcColorValue
        }
    }

    val maxRetry:Int = 10
    var retryCount = 0;
    fun send(){
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val jsonStr = BiParameter().createJson()
                val url = if (AppConfig.isBundle()) "https://config.scanpro.uk/api/cloak" else "https://testserver.googletogoogle.com/api/cloak"
                Util.iii { "${BiRemoteConfig.TAG}==>$url\n${jsonStr}" }

                val client = HttpUtil.getClient()
                // TODO xxx Url
                val request: Request = Request.Builder()
                    .url(url)
                    .post(jsonStr.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .build()

                val response: Response = client.newCall(request).execute()
                if (response.code ==200){
                    val bodyString = response.body?.string()
                    Util.iii { "$TAG<==code=${response.code} body=${bodyString}" }
                    if (bodyString.isNullOrEmpty()){

                    }else{
                        parseData(bodyString)
                    }
                    response.close()
                }else{
                    Util.iii { "$TAG<==code=${response.code} body=${response.body?.string()}" }
                    response.close()
                    retry()
                }
            }.onFailure { tr->
                tr.printStackTrace()
                retry()
            }
        }
    }


    suspend fun retry() {
        if (retryCount>=maxRetry){

        }else{
            retryCount=retryCount+1
            delay(20_000L)
            send()
        }
    }


    fun parseData(json: String){
        val jsonObj = JSONObject(json)
        val cloakStr = jsonObj.getString("data")
        mColorValue = cloakStr
        KvUtil.setString(KEY_COLOR_VALUE,cloakStr)
    }







}