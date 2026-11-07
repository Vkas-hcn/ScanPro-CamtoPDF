package com.zppko.pdfplus.nor.horus

import android.os.Build
import android.widget.RemoteViews
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.notice.SpJumpType
import com.scanpro.cam.pdf.notice.SpNotifyTheme
import com.smart.pdf.scanner.util.LocaleHelper
import com.zppko.pdfplus.nor.Route

/** Presentation only: A owns channels, IDs, dispatch and repeat timing. */
internal object HintNotifyViews {
    fun persistent(small: Boolean): RemoteViews {
        val context = HintUtil.coIntance()
        return RemoteViews(context.packageName, if (small) R.layout.sp_notify_persistent_small else R.layout.sp_notify_persistent_big).apply {
            if (!small && Build.VERSION.SDK_INT < 30) setViewPadding(R.id.root, 0, 20, 0, 20)
            bindPersistent(this)
        }
    }

    fun bindPersistent(view: RemoteViews) {
        val color = SpNotifyTheme.textColor(HintUtil.coIntance())
        view.setTextColor(R.id.tagPdf, color)
        view.setTextColor(R.id.tagExcel, color)
        view.setTextColor(R.id.tagPpt, color)
        view.setOnClickPendingIntent(R.id.pdfLayout, HintUtil.createIntent(Route.LargeFile, HintExt01.CO_S_TYPE_BAR, HintExt01.CO_FIX_NOTIFY_ID, SpJumpType.PDF))
        view.setOnClickPendingIntent(R.id.excelLayout, HintUtil.createIntent(Route.DuplicateFile, HintExt01.CO_S_TYPE_BAR, HintExt01.CO_FIX_NOTIFY_ID, SpJumpType.EXCEL))
        view.setOnClickPendingIntent(R.id.pptLayout, HintUtil.createIntent(Route.DuplicateFile, HintExt01.CO_S_TYPE_BAR, HintExt01.CO_FIX_NOTIFY_ID, SpJumpType.PPT))
    }

    fun normal(setting: CallingSetting, small: Boolean): RemoteViews {
        val context = HintUtil.coIntance()
        return RemoteViews(context.packageName, if (small) R.layout.sp_notify_sys_small else R.layout.sp_notify_sys_big).apply {
            if (small && Build.VERSION.SDK_INT < 30) setViewPadding(R.id.root, 20, 20, 20, 20)
            bindNormal(this, setting, LocaleHelper.getLocalString(setting.msg), small)
        }
    }

    fun bindNormal(view: RemoteViews, setting: CallingSetting, content: String, small: Boolean = false) {
        if (!small) view.setImageViewResource(R.id.cover, setting.bigImg)
        view.setTextViewText(R.id.messageText, content)
        view.setTextColor(R.id.messageText, SpNotifyTheme.textColor(HintUtil.coIntance()))
        val button = if (small) R.id.btnText else R.id.btn
        view.setTextViewText(button, LocaleHelper.getLocalString(setting.btnStr))
        if (Build.VERSION.SDK_INT > 28) {
            view.setImageViewResource(R.id.anim_btn, R.drawable.sp_notify_btn_anim)
        } else {
            view.setInt(button, "setBackgroundResource", setting.btnBg)
        }
    }
}
