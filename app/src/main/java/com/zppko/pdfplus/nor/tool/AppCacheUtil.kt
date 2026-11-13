package com.zppko.pdfplus.nor.tool

import android.os.Build

object AppCacheUtil {


    fun isAndroid12(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S



}