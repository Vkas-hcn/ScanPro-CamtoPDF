package com.scanpro.cam.pdf.notice

/** Notification entry contract shared by migrated notifications and existing click handling. */
internal object SpNotifyCreator {
    const val FROM_SYS = "sys_notify"
    const val FROM_MEDIA = "media_notify"
    const val FROM_MEDIA_TWO = "media_notify_2"
    const val FROM_PERSISTENT = "persistent_notify"
}
