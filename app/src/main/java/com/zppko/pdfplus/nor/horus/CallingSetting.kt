package com.zppko.pdfplus.nor.horus

import com.scanpro.cam.pdf.notice.SpJumpType

data class CallingSetting public constructor(val route:String, val title:Int, val msg:Int, val bigImg:Int, val btnStr:Int,val btnBg:Int, val id:Int) {
    // B's file category and scene are independent of A's navigation route.
    var jumpType: SpJumpType? = null
    var sceneType: Int? = null
}
