package com.zppko.pdfplus.nor.ads

import com.bockcock.adjust.ConfigBean
import com.buncead.common.util.Util
import com.mymy.mymy.ads.AdLog
import com.mymy.mymy.ads.count.AdCountInter
import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent

object InterShCount {
   private var count = 0


    fun isCanShow(): Boolean {
        val setting = ImpressionUtils.currentSetting(CodeKeyAndEvent.zx_PLACE_INTER_ClEAN)
        // 该广告位（boxaxj）当天达到 per-ID 上限后不再展示，也不再推进展示序号
        val capOk = AdCountInter.inter_idCountCheck(setting)
        if (!capOk) {
            AdLog.log(
                "GATE_INTER_SH",
                CodeKeyAndEvent.zx_PLACE_INTER_ClEAN,
                setting?.zId,
                "capOk=false show=false caps=${setting?.zDayShow}/${setting?.zDayClick}"
            )
            return false
        }
        if (ConfigBean.inter_op_count_interval <= 0) {
            AdLog.log(
                "GATE_INTER_SH",
                CodeKeyAndEvent.zx_PLACE_INTER_ClEAN,
                setting?.zId,
                "capOk=true interval=always show=true"
            )
            return true
        }

//        Util.iii { "count=${count}" }
        val n = count
        val result = count % (ConfigBean.inter_op_count_interval+1)
//        Util.iii { "result=${result}" }
//        Util.iii { "========================" }
        count = count + 1
        val canShow = result == 0
        AdLog.log(
            "GATE_INTER_SH",
            CodeKeyAndEvent.zx_PLACE_INTER_ClEAN,
            setting?.zId,
            "capOk=true n=$n interval=${ConfigBean.inter_op_count_interval} result=$result show=$canShow"
        )
        return canShow
    }

}