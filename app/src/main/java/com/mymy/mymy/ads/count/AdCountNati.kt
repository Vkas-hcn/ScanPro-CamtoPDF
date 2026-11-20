package com.mymy.mymy.ads.count

import com.buncead.common.util.Util
import com.mymy.mymy.ads.AdsConf
import com.zppko.pdfplus.nor.ads.sett.AdsSetting


object AdCountNati {
    val TAG = "AdCountNati"

    fun nati_addShowCount(id: String){
        natiLastShowTime = System.currentTimeMillis()
        AdIdCount.addShowCount(id)
    }

    fun nati_addClickCount(id: String){
        AdIdCount.addClickCount(id)
    }

    /** 每个广告位（boxaxj）单独计数；条目未配 day_sh/day_cli 时用 AdsConf 内置兜底值。 */
    fun nati_idCountCheck(setting: AdsSetting?): Boolean {
        if (setting == null || setting.zId.isEmpty()) {
            return true
        }
        val showCap = if (setting.zDayShow >= 0) setting.zDayShow else AdsConf.native_day_show
        val clickCap = if (setting.zDayClick >= 0) setting.zDayClick else AdsConf.native_day_click
        return AdIdCount.canShow(setting.zId, showCap, clickCap)
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

    fun natiCanShow(setting: AdsSetting?): Boolean {
        return  nati_idCountCheck(setting) && nati_checkTimeDiff()
    }


}
