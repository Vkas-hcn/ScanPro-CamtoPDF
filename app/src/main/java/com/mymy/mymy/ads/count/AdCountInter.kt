package com.mymy.mymy.ads.count

import com.bockcock.adjust.KvUtil
import com.buncead.common.util.Util
import com.mymy.mymy.ads.AdsConf


object AdCountInter {
    val TAG = "AdCountInter"


    var interShowCount = 0
    var interClickCount = 0

    val KEY_INTER_SHOW_COUNT = "KEY_INTER_SHOW_COUNT"
    val KEY_INTER_CLICK_COUNT = "KEY_INTER_CLICK_COUNT"

    fun inter_resetCount(){
        interShowCount = 0
        interClickCount = 0
        KvUtil.setInt(KEY_INTER_SHOW_COUNT,interShowCount)
        KvUtil.setInt(KEY_INTER_CLICK_COUNT,interClickCount)
    }

    fun inter_getCount(){
        interShowCount = KvUtil.getInt(KEY_INTER_SHOW_COUNT)
        interClickCount = KvUtil.getInt(KEY_INTER_CLICK_COUNT)
    }

    fun inter_addShowCount(){
        interLastShowTime = System.currentTimeMillis()
        interShowCount++
        KvUtil.setInt(KEY_INTER_SHOW_COUNT,interShowCount)
    }

    fun inter_addClickCount(){
        interClickCount++
        KvUtil.setInt(KEY_INTER_CLICK_COUNT,interClickCount)
    }

    fun inter_time2CountCheck(): Boolean {
        if (ADManagerMy.isToday()){
            val canShow: Boolean = interShowCount < AdsConf.inter_day_show &&  interClickCount < AdsConf.inter_day_click
            Util.iii { "$TAG inter_time2CountCheck() canShow=${canShow}, ${interShowCount}<${AdsConf.inter_day_show} && ${interClickCount}<${AdsConf.inter_day_click}" }
            return canShow
        }else{
            return true
        }
    }

    var interLastShowTime = 0L
    fun inter_checkTimeDiff(): Boolean {
        val diff = System.currentTimeMillis() - interLastShowTime
        val canShow = diff > AdsConf.inter_gap
        Util.iii { "$TAG inter_checkTimeDiff() canShow=${canShow}, diff=${diff}, inter_gap=${AdsConf.inter_gap}" }
        if (canShow){
            return true
        }
        return false
    }


    fun interCanShow(): Boolean {
        return  inter_time2CountCheck() && inter_checkTimeDiff()
    }

}