package com.mymy.mymy.ads.count

import com.bockcock.adjust.KvUtil
import com.mymy.mymy.ads.AdLog

/**
 * 按广告位 ID（pps_ad_data 里的 boxaxj）分别统计每日展示/点击次数。
 * 计数持久化在 SharedPreferences，自然日切换时由 ADManagerMy 统一清零。
 */
object AdIdCount {
    val TAG = "AdIdCount"

    private const val PREFIX_SHOW = "ADID_SHOW_"
    private const val PREFIX_CLICK = "ADID_CLICK_"

    private fun showKey(id: String) = PREFIX_SHOW + id
    private fun clickKey(id: String) = PREFIX_CLICK + id

    fun getShowCount(id: String): Int = KvUtil.getInt(showKey(id))

    fun getClickCount(id: String): Int = KvUtil.getInt(clickKey(id))

    fun addShowCount(id: String) {
        val count = getShowCount(id) + 1
        KvUtil.setInt(showKey(id), count)
        AdLog.ad("COUNT_ADD_SHOW", id, "showCount=$count")
    }

    fun addClickCount(id: String) {
        val count = getClickCount(id) + 1
        KvUtil.setInt(clickKey(id), count)
        AdLog.ad("COUNT_ADD_CLICK", id, "clickCount=$count")
    }

    /** 任意一个上限 <=0 视为当天关闭；-1（未配置）由调用方换成兜底值后再传入。 */
    fun canShow(id: String, showCap: Int, clickCap: Int): Boolean {
        if (id.isEmpty()) return true
        // 触发自然日重置（清零全部广告 ID 计数）；日切后仍需正常比较，避免 0 上限被放过
        ADManagerMy.isToday()
        val show = getShowCount(id)
        val click = getClickCount(id)
        val canShow = passes(show, click, showCap, clickCap)
        AdLog.ad("COUNT_CAN_SHOW", id, "canShow=$canShow show=$show/$showCap click=$click/$clickCap")
        return canShow
    }

    fun reset() {
        KvUtil.removeKeysByPrefix(PREFIX_SHOW)
        KvUtil.removeKeysByPrefix(PREFIX_CLICK)
        AdLog.ad("COUNT_RESET", null, "cleared all ad id counters")
    }

    internal fun passes(show: Int, click: Int, showCap: Int, clickCap: Int): Boolean {
        return showCap > 0 && clickCap > 0 && show < showCap && click < clickCap
    }
}
