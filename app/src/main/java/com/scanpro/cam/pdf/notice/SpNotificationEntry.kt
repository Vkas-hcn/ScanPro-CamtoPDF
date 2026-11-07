package com.scanpro.cam.pdf.notice

import android.content.Intent

/** Normalizes A's notification payload without changing the old B intent contract. */
object SpNotificationEntry {
    const val A_NAV = "g_cik_nav"
    const val A_LAUNCH = "g_launch"
    const val A_SHOW_ONCE = "g_sh_on_ad"
    const val A_NO_REPORT = "g_not_up_eve"
    const val A_SHOW_TYPE = "g2sd65"
    const val A_SHOW_DATA = "gsd3ax"

    fun route(intent: Intent): String? {
        val supported = setOf("b_home", "b_scan", "b_recents", "WORD", "EXCEL", "PPT", "PDF")
        // B's explicit file category wins over the legacy A navigation carried beside it.
        // Keep navigation-only PendingIntents working without reinterpreting file categories.
        return intent.getStringExtra(SpNotifyExtras.JUMP_TYPE)?.takeIf { it in supported }
            ?: intent.getStringExtra(A_NAV)?.takeIf { it in supported }
    }

    fun fromType(intent: Intent): String? = intent.getStringExtra(SpNotifyExtras.FROM_TYPE)
        ?: if (intent.hasExtra(A_SHOW_TYPE)) {
            when (intent.getIntExtra(A_SHOW_TYPE, -1)) {
                3 -> SpNotifyCreator.FROM_PERSISTENT
                4 -> SpNotifyCreator.FROM_MEDIA
                0, 1, 2 -> SpNotifyCreator.FROM_SYS
                else -> null
            }
        } else null

    fun sceneType(intent: Intent): String = intent.getStringExtra(SpNotifyExtras.SCENE_TYPE)
        ?: when (intent.getIntExtra(A_SHOW_TYPE, -1)) {
            0 -> "unlock"
            1 -> "time"
            2 -> "alarm"
            3 -> "resident"
            4 -> "media"
            else -> "start"
        }

    fun isNotification(intent: Intent): Boolean = fromType(intent) != null
    fun isHotStart(intent: Intent): Boolean = intent.getStringExtra(A_LAUNCH) == A_SHOW_ONCE
}
