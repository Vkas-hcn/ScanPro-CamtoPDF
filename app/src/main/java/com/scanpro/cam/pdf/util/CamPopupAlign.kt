package com.scanpro.cam.pdf.util

import android.view.View
import android.widget.PopupWindow

object CamPopupAlign {

    /** Show [popup] below [anchor], with right edges aligned. */
    fun showBelowAlignEnd(popup: PopupWindow, anchor: View, yOff: Int = 0) {
        val content = popup.contentView
        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val xOff = anchor.width - content.measuredWidth
        popup.showAsDropDown(anchor, xOff, yOff)
    }
}
