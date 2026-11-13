package com.mymy.mymy

import android.content.Context
import com.bockcock.adjust.KvUtil
import com.buncead.common.util.Util
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GaidUtil {

    var mAdInfoId:String = ""
    val KEY_ADINFO_ID = "KEY_ADINFO_ID_ARC"

    fun init(mContext: Context){
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {

                mAdInfoId= KvUtil.getString(KEY_ADINFO_ID)
                if ( mAdInfoId.isNullOrEmpty()){
                    val adInfo: AdvertisingIdClient.Info  = AdvertisingIdClient.getAdvertisingIdInfo(mContext)
                    val gaid: String? = adInfo.getId() // 获取 GAID
                    val limitAdTracking: Boolean = adInfo.isLimitAdTrackingEnabled() // 是否限制广告跟踪
                    Util.iii { "GAID:${gaid}, Limit Ad Tracking:${limitAdTracking}" }

                    if (!gaid.isNullOrEmpty()){
                        mAdInfoId = gaid
                        KvUtil.setString(KEY_ADINFO_ID,gaid)
                    }
                }
            }.onFailure {tr->
                tr.printStackTrace()
            }
        }
    }


}