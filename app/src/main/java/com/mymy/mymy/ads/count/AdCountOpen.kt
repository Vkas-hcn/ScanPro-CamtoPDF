package com.mymy.mymy.ads.count

import com.buncead.common.util.Util
import com.mymy.mymy.ads.AdsConf
import com.zppko.pdfplus.nor.ads.sett.AdsSetting


object AdCountOpen {
    val TAG = "AdCountOpen"

    fun open_addShowCount(id: String){
        openLastShowTime = System.currentTimeMillis()
        AdIdCount.addShowCount(id)
    }

    fun open_addClickCount(id: String){
        AdIdCount.addClickCount(id)
    }

    /** 每个广告位（boxaxj）单独计数；条目未配 day_sh/day_cli 时用 AdsConf 内置兜底值。 */
    fun open_idCountCheck(setting: AdsSetting?): Boolean {
        if (setting == null || setting.zId.isEmpty()) {
            return true
        }
        val showCap = if (setting.zDayShow >= 0) setting.zDayShow else AdsConf.open_day_show
        val clickCap = if (setting.zDayClick >= 0) setting.zDayClick else AdsConf.open_day_click
        return AdIdCount.canShow(setting.zId, showCap, clickCap)
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

    fun openCanShow(setting: AdsSetting?): Boolean {
        return  open_idCountCheck(setting) && open_checkTimeDiff()
    }

}
