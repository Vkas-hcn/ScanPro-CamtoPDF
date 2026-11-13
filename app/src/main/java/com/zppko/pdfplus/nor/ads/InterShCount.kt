package com.zppko.pdfplus.nor.ads

import com.bockcock.adjust.ConfigBean
import com.buncead.common.util.Util

object InterShCount {
   private var count = 0


    fun isCanShow(): Boolean {
        if (ConfigBean.inter_op_count_interval <= 0) {
            return true
        }

//        Util.iii { "count=${count}" }
        val result = count % (ConfigBean.inter_op_count_interval+1)
//        Util.iii { "result=${result}" }
//        Util.iii { "========================" }
        count = count + 1
        if (result == 0) {
            return true
        }

        return false
    }

}