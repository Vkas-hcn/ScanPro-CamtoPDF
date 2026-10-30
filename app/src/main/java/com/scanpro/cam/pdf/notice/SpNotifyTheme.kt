package com.scanpro.cam.pdf.notice

import android.content.Context
import android.content.res.Configuration
import com.scanpro.cam.pdf.R

/**
 * 通知自定义版式的取色。
 *
 * 为什么需要它：`sp_notify_sys_*` / `sp_notify_persistent_*` 是 RemoteViews，
 * 里面的 `android:textColor` 是**写死的一次性取值**，系统画通知时不会按深浅色再反色一遍。
 * 白天通知卡片是浅色（近白），深色模式是深色（近黑），同一个 `#333333` 在后者上
 * 对比度只有 1.3:1 左右——字基本看不见。
 *
 * 所以每次造 RemoteViews 时都按当前 uiMode 用 `RemoteViews.setTextColor` 显式盖一次。
 *
 * 判据取的是**系统**的 uiMode：通知栏背景跟系统深色开关走，而本 App 是
 * `Theme.Material3.Light` + `forceDarkAllowed=false`（界面恒浅色），两者本来就不是一回事。
 * 本工程没有调过 `AppCompatDelegate.setDefaultNightMode`，所以 App 的配置就等于系统配置；
 * 以后若真给 App 锁了浅色，这里要改成 `Resources.getSystem().configuration.uiMode`。
 */
internal object SpNotifyTheme {

    /** 当前该用哪种通知正文色：深色模式给浅字，其余给深字。 */
    fun textColor(context: Context): Int {
        val uiMode = context.resources.configuration.uiMode
        val night = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return context.getColor(if (night) R.color.sp_notify_text_night else R.color.sp_notify_text)
    }
}
