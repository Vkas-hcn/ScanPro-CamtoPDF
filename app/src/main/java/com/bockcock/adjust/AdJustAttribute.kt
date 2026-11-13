package com.bockcock.adjust

import com.adjust.sdk.AdjustConfig
import com.buncead.common.util.Util
import com.mymy.mymy.rece.HomeRecentsHelp



object AdJustAttribute {
    val TAG = "AdJustHelper"

    // 用户类型监听，买量，或者自然量（非买量）
    fun userTypeListener(config:AdjustConfig){
        val muserData:Int = getMUserData()
        adjustMUserData =muserData
        if (SG_ADJUST_MUSER == muserData){// 如果是买量用户，就不用监听了
            return
        }

        val count: Int = getNatureCount()
        if (count>5){// 如果5 次标记为自然量，标记为自然量（不改成买量就行）
            return
        }

        // 归因回调
        config.setOnAttributionChangedListener { attribution ->
            Util.iii { "$TAG OnAttributionChangedListener()111 =${attribution}" }
            attribution?.let {
                // 这里处理归因数据
                // it.network / it.campaign / it.adgroup 等
                val network: String = attribution.network
//                val campaign: String  = attribution.campaign
//                val adgroup: String  = attribution.adgroup
//                val creative: String  = attribution.creative
//                val trackerToken: String  = attribution.trackerToken
//                Util.iii { "$TAG network=${network}, campaign=${campaign}, adgroup=${adgroup}, creative=${creative}, trackerToken=${trackerToken}, " }

                var usrInfo = ""
                if (AppConfig.isBundle()){
                    usrInfo = network.lowercase()
                }else{
                    usrInfo = network.lowercase()+"aaa"
                }
                if ("organic".equals(usrInfo)){ // 自然量用户
                    setNatureCount(count+1)
                    Util.iii { "$TAG OnAttributionChangedListener()222 Natural usrInfo=${usrInfo}" }
                }else{ // 买量用户
                    Util.iii { "$TAG OnAttributionChangedListener()333 Buy usrInfo=${usrInfo}" }
                    setMUserData(SG_ADJUST_MUSER)

                    ConfigBean.refer2adjust_isMUser()
                }

            }
        }
    }

    fun isAdjustMuser(): Boolean {
        Util.iii { "$TAG isAdjustMuser() adjustMUserData=${adjustMUserData}" }
        if (SG_ADJUST_MUSER == adjustMUserData) {
//            Util.iii { "adjust 判断为'买量'" }
            return true
        }
//        Util.iii { "adjust 判断为'非买量'" }
        return false
    }

    val SG_ADJUST_MUSER = 56216 // 6666 是买量，否则非买量

    val KEY_ADJUST_MUSER_INFO = "h4s65gdf53cv2"
    var adjustMUserData = 0 // 6666 是买量，否则非买量
     fun setMUserData(data: Int) {
        adjustMUserData = data
        KvUtil.setInt(KEY_ADJUST_MUSER_INFO, data)
    }
    fun getMUserData(): Int {
        return KvUtil.getInt(KEY_ADJUST_MUSER_INFO)
    }

    // 检查为自然量用户 次数
    val KEY_CHECK_NATURE_COUNT = "g4rf65gdfg23"
    var checkNatureCount = 0
     fun setNatureCount(data: Int) {
        checkNatureCount = data
        KvUtil.setInt(KEY_CHECK_NATURE_COUNT, data)
    }

     fun getNatureCount(): Int {
        return KvUtil.getInt(KEY_CHECK_NATURE_COUNT)
    }







}