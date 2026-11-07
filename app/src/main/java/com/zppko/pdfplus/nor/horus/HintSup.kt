package com.zppko.pdfplus.nor.horus

import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.notice.SpJumpType
import com.scanpro.cam.pdf.notice.SpNotifyCatalog
import com.zppko.pdfplus.nor.Route

object HintSup {
    private val categories = arrayOf(
        SpJumpType.WORD, SpJumpType.WORD, SpJumpType.EXCEL, SpJumpType.EXCEL,
        SpJumpType.PPT, SpJumpType.PPT, SpJumpType.PDF, SpJumpType.PDF,
    )

    private fun pool(type: Int, messages: IntArray): Array<CallingSetting> =
        Array(messages.size) { index ->
            val category = categories[index]
            val route = when (category) {
                SpJumpType.WORD -> Route.Garbage
                SpJumpType.EXCEL, SpJumpType.PPT -> Route.DuplicateFile
                SpJumpType.PDF -> Route.LargeFile
            }
            CallingSetting(route, R.string.app_name, messages[index], SpNotifyCatalog.coverIcon(category),
                R.string.sp_notify_action, R.drawable.sp_shape_notify_btn, 70101).apply {
                jumpType = category
                sceneType = type
            }
        }

    private val scheduled = pool(HintUtil.CO_S_TYPE_INTERVAL, intArrayOf(
        R.string.nt_scheduled_1, R.string.nt_scheduled_2, R.string.nt_scheduled_3, R.string.nt_scheduled_4,
        R.string.nt_scheduled_5, R.string.nt_scheduled_6, R.string.nt_scheduled_7, R.string.nt_scheduled_8,
    ))
    private val unlock = pool(HintUtil.CO_S_TYPE_UNLOCK, intArrayOf(
        R.string.nt_unlock_1, R.string.nt_unlock_2, R.string.nt_unlock_3, R.string.nt_unlock_4,
        R.string.nt_unlock_5, R.string.nt_unlock_6, R.string.nt_unlock_7, R.string.nt_unlock_8,
    ))
    private val alarm = pool(HintUtil.CO_S_TYPE_ALARM, intArrayOf(
        R.string.nt_alarm_1, R.string.nt_alarm_2, R.string.nt_alarm_3, R.string.nt_alarm_4,
        R.string.nt_alarm_5, R.string.nt_alarm_6, R.string.nt_alarm_7, R.string.nt_alarm_8,
    ))

    private val launcher = scheduled.map { setting ->
        setting.copy().apply {
            jumpType = setting.jumpType
            sceneType = HintExt04_lan.CO_S_TYPE_LAUNCHER
        }
    }.toTypedArray()

    val text3 = arrayOf(scheduled[0], scheduled[2], scheduled[6])

    fun forType(type: Int): Array<CallingSetting> = when (type) {
        HintUtil.CO_S_TYPE_UNLOCK -> unlock
        HintUtil.CO_S_TYPE_ALARM -> alarm
        HintExt04_lan.CO_S_TYPE_LAUNCHER -> launcher
        else -> scheduled
    }
}
