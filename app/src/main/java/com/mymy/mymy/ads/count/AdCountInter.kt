package com.mymy.mymy.ads.count

import com.buncead.common.util.Util
import com.mymy.mymy.ads.AdsConf
import com.zppko.pdfplus.nor.ads.sett.AdsSetting


object AdCountInter {
    val TAG = "AdCountInter"

    fun inter_addShowCount(id: String){
        interLastShowTime = System.currentTimeMillis()
        AdIdCount.addShowCount(id)
    }

    fun inter_addClickCount(id: String){
        AdIdCount.addClickCount(id)
    }

    /** 每个广告位（boxaxj）单独计数；条目未配 day_sh/day_cli 时用 AdsConf 内置兜底值。 */
    fun inter_idCountCheck(setting: AdsSetting?): Boolean {
        if (setting == null || setting.zId.isEmpty()) {
            return true
        }
        val showCap = if (setting.zDayShow >= 0) setting.zDayShow else AdsConf.inter_day_show
        val clickCap = if (setting.zDayClick >= 0) setting.zDayClick else AdsConf.inter_day_click
        return AdIdCount.canShow(setting.zId, showCap, clickCap)
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

    fun interCanShow(setting: AdsSetting?): Boolean {
        return  inter_idCountCheck(setting) && inter_checkTimeDiff()
    }

}
