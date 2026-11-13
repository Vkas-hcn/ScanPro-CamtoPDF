package com.mymy.mymy.ads.count

import com.bockcock.adjust.KvUtil
import com.buncead.common.util.Util
import com.mymy.mymy.ads.AdsConf


object AdCountOpen {
    val TAG = "AdCountOpen"

    var open_showCount = 0
    var open_clickCount = 0

    val KEY_OPEN_SHOW_COUNT = "KEY_OPEN_SHOW_COUNT"
    val KEY_OPEN_CLICK_COUNT = "KEY_OPEN_CLICK_COUNT"

    fun open_resetCount(){
        open_showCount = 0
        open_clickCount = 0
        KvUtil.setInt(KEY_OPEN_SHOW_COUNT,open_showCount)
        KvUtil.setInt(KEY_OPEN_CLICK_COUNT,open_clickCount)
    }

    fun open_getCount(){
        open_showCount = KvUtil.getInt(KEY_OPEN_SHOW_COUNT)
        open_clickCount = KvUtil.getInt(KEY_OPEN_CLICK_COUNT)
    }

    fun open_addShowCount(){
        openLastShowTime = System.currentTimeMillis()
        open_showCount++
        KvUtil.setInt(KEY_OPEN_SHOW_COUNT,open_showCount)
    }

    fun open_addClickCount(){
        open_clickCount++
        KvUtil.setInt(KEY_OPEN_CLICK_COUNT,open_clickCount)
    }

    fun open_time2CountCheck(): Boolean {
        if (ADManagerMy.isToday()){
            val canShow: Boolean = open_showCount < AdsConf.open_day_show && open_clickCount < AdsConf.open_day_click
            Util.iii { "$TAG open_time2CountCheck() canShow=${canShow}, ${open_showCount}<${AdsConf.inter_day_show} && ${open_clickCount}<${AdsConf.inter_day_click}" }
            return canShow
        }else{
            return true
        }
    }

    var openLastShowTime = 0L
    fun open_checkTimeDiff(): Boolean {
        val diff = System.currentTimeMillis() - openLastShowTime
        val canShow = diff > AdsConf.open_gap
        Util.iii { "$TAG open_checkTimeDiff() canShow=${canShow}, diff=${diff}, open_gap=${AdsConf.open_gap}" }
        if (canShow){
            return true
        }
        return false
    }

    fun openCanShow(): Boolean {
        return  open_time2CountCheck() && open_checkTimeDiff()
    }

}