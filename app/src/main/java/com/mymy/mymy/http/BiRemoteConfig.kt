package com.mymy.mymy.http

import com.bockcock.adjust.AppConfig
import com.bockcock.adjust.ConfigBean
import com.bockcock.adjust.FbHelp
import com.bockcock.adjust.Pin
import com.scanpro.cam.pdf.CamPdfApp
import com.buncead.common.util.Util
import com.mymy.mymy.rece.HomeRecentsHelp
import com.zppko.pdfplus.nor.ads.tool.SpUtil
import com.zppko.pdfplus.nor.horus.HintExt05
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject


object BiRemoteConfig {

    val TAG = "BiRemote_help"

//    var mBiRemoteConfig = ""
//    val KEY_CONFIG_VALUE = "KEY_CONFIG_VALUE"
//    fun sendRequest2Check(){
//        val configValue = SaveKv.getString(KEY_CONFIG_VALUE)
//        if (configValue.isNullOrEmpty()){
//            Util.iii { "$TAG local bi config is null,go request=${configValue}" }
//            sendRequest()
//        }else{
//            Util.iii { "$TAG local bi config=${configValue}" }
//            mBiRemoteConfig = configValue
//        }
//    }

    val maxRetry:Int = 10
    var retryCount = 0;
    fun sendRequest(){
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val jsonStr = BiParameter().createJson()
                val url = if (AppConfig.isBundle()) "https://appconfig.pdfsmarter.uk/api/dispatch" else "https://testserver.googletogoogle.com/api/dispatch"
                Util.iii { "$TAG==>$url\n${jsonStr}" }

                val client = HttpUtil.getClient()
                // TODO xxx Url
                val request: Request = Request.Builder()
                    .url(url)
                    .post(jsonStr.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .build()

                val response: Response = client.newCall(request).execute()
                if (response.code ==200){
                    val bodyString = response.body?.string()
                    response.close()
                    Util.iii { "$TAG<==code=${response.code} body=${bodyString}" }
                    if (bodyString.isNullOrEmpty()){

                    }else{
                        parseData(bodyString)
                    }
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
            sendRequest()
        }
    }

    val KEY_ADS_CONFIG = "pps_ad_data"
    val KEY_REFERRER_CONFIG = "pps_refer_data"
    val KEY_POP_CONFIG_All = "comm_pop_data"
    val KEY_POP_CONFIG_SAMSUNG = "sams_pop_data"
    fun parseData(jsonStr: String){
        val jsonObj = JSONObject(jsonStr)
//        val result = jsonObj.getString("data")
//        Util.iii { "result=${result}" }
//        SaveKv.setString(KEY_CONFIG_VALUE,result)

        val jsonObj2:JSONObject = jsonObj.getJSONObject("data")

        val ads_conf: String = jsonObj2.getString(KEY_ADS_CONFIG)
        val bbb_conf: String = jsonObj2.getString(KEY_REFERRER_CONFIG)
        val ntf_conf: String = jsonObj2.getString(KEY_POP_CONFIG_All)
        val fot_conf: String = jsonObj2.getString(KEY_POP_CONFIG_SAMSUNG)

        SpUtil.put(KEY_ADS_CONFIG, ads_conf)
        SpUtil.put(KEY_REFERRER_CONFIG, bbb_conf)
        SpUtil.put(KEY_POP_CONFIG_All, ntf_conf)
        SpUtil.put(KEY_POP_CONFIG_SAMSUNG, fot_conf)
        Util.iii { "pps_ad_data=${ads_conf}" }
        Util.iii { "pps_refer_data=${bbb_conf}" }
        Util.iii { "comm_pop_data=${ntf_conf}" }
        Util.iii { "sams_pop_data=${fot_conf}" }

        FbHelp.startFB() // 解析远程的数据，


        ConfigBean.refer2adjust_isMUser()
    }



}