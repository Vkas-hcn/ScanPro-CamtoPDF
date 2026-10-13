package com.scanpro.cam.pdf.viewer

import android.content.Context
import com.scanpro.cam.pdf.data.CamRecentStore
import com.scanpro.cam.pdf.home.FileType
import com.scanpro.cam.pdf.home.CamFileItem

object CamDocRouter {

    fun open(context: Context, item: CamFileItem) {
        CamRecentStore(context).record(item)
        if (item.type == FileType.PDF) {
            CamPdfReaderActivity.start(context, item)
        } else {
            CamOfficeReaderActivity.start(context, item)
        }
    }
}
