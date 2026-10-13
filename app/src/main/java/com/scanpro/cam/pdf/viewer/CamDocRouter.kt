package com.scanpro.cam.pdf.viewer

import android.app.Activity
import com.scanpro.cam.pdf.ads.SpPageAdTriggers
import com.scanpro.cam.pdf.data.CamRecentStore
import com.scanpro.cam.pdf.home.FileType
import com.scanpro.cam.pdf.home.CamFileItem

object CamDocRouter {

    /**
     * 打开文件。对照 Apollo `FileListFragment`：先 `ac_check_int`，
     * 关掉或无缓存后再进阅读器。
     */
    fun open(activity: Activity, item: CamFileItem) {
        SpPageAdTriggers.showCheckIntThen(activity) {
            if (activity.isFinishing || activity.isDestroyed) return@showCheckIntThen
            CamRecentStore(activity).record(item)
            if (item.type == FileType.PDF) {
                CamPdfReaderActivity.start(activity, item)
            } else {
                CamOfficeReaderActivity.start(activity, item)
            }
        }
    }
}
