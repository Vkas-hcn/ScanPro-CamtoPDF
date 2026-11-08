package com.mymy.mymy.ads.count

import com.bockcock.adjust.KvUtil
import com.buncead.common.util.Util
import com.mymy.mymy.ads.AdsConf


object AdCountNati {
    val TAG = "AdCountNati"

    var nati_showCount = 0
    var nati_clickCount = 0

    val KEY_NATI_SHOW_COUNT = "KEY_NATI_SHOW_COUNT"
    val KEY_NATI_CLICK_COUNT = "KEY_NATI_CLICK_COUNT"

    fun nati_resetCount(){
        nati_showCount = 0
        nati_clickCount = 0
        KvUtil.setInt(KEY_NATI_SHOW_COUNT,nati_showCount)
        KvUtil.setInt(KEY_NATI_CLICK_COUNT,nati_clickCount)
    }

    fun nati_getCount(){
        nati_showCount = KvUtil.getInt(KEY_NATI_SHOW_COUNT)
        nati_clickCount = KvUtil.getInt(KEY_NATI_CLICK_COUNT)
    }

    fun nati_addShowCount(){
        natiLastShowTime = System.currentTimeMillis()
        nati_showCount++
        KvUtil.setInt(KEY_NATI_SHOW_COUNT,nati_showCount)
    }

    fun nati_addClickCount(){
        nati_clickCount++
        KvUtil.setInt(KEY_NATI_CLICK_COUNT,nati_clickCount)
    }

    fun nati_time2CountCheck(): Boolean {
        if (ADManagerMy.isToday()){
            val canShow: Boolean = nati_showCount < AdsConf.native_day_show && nati_clickCount < AdsConf.native_day_click
            Util.iii { "$TAG nati_time2CountCheck() canShow=${canShow}, ${nati_showCount}<${AdsConf.inter_day_show} && ${nati_clickCount}<${AdsConf.inter_day_click}" }
            return canShow
        }else{
            return true
        }
    }

    var natiLastShowTime = 0L
    fun nati_checkTimeDiff(): Boolean {
        val diff = System.currentTimeMillis() - natiLastShowTime
        val canShow = diff > AdsConf.native_gap
        Util.iii { "$TAG nati_checkTimeDiff() canShow=${canShow}, diff=${diff}, inter_gap=${AdsConf.native_gap}" }
        if (canShow){
            return true
        }
        return false
    }

    fun natiCanShow(): Boolean {
        return  nati_time2CountCheck() && nati_checkTimeDiff()
    }


}
