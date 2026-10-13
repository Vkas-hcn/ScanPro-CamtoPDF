package com.scanpro.cam.pdf.rate

import android.content.Context
import java.util.concurrent.TimeUnit

class SpRateStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun ensureInstallRecorded() {
        if (!prefs.contains(KEY_INSTALL_MS)) {
            prefs.edit().putLong(KEY_INSTALL_MS, System.currentTimeMillis()).apply()
        }
    }

    fun hasRated(): Boolean = prefs.getBoolean(KEY_HAS_RATED, false)

    fun markRated() {
        prefs.edit().putBoolean(KEY_HAS_RATED, true).apply()
    }

    fun hasShownFirstEntryPrompt(): Boolean =
        prefs.getBoolean(KEY_FIRST_ENTRY_SHOWN, false)

    fun markFirstEntryPromptShown() {
        prefs.edit().putBoolean(KEY_FIRST_ENTRY_SHOWN, true).apply()
    }

    fun hasTriggeredFirstPdfSave(): Boolean =
        prefs.getBoolean(KEY_FIRST_PDF_TRIGGERED, false)

    fun markFirstPdfSaveTriggered() {
        prefs.edit().putBoolean(KEY_FIRST_PDF_TRIGGERED, true).apply()
    }

    /** 1 = install day, 2 = next calendar day, … */
    fun daysSinceInstall(): Long {
        ensureInstallRecorded()
        val install = prefs.getLong(KEY_INSTALL_MS, System.currentTimeMillis())
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - install)
        return days + 1L
    }

    fun markReturnDayPromptShown(day: Long) {
        val set = prefs.getStringSet(KEY_RETURN_DAYS_SHOWN, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        set.add(day.toString())
        prefs.edit().putStringSet(KEY_RETURN_DAYS_SHOWN, set).apply()
    }

    fun hasShownReturnDayPrompt(day: Long): Boolean {
        return prefs.getStringSet(KEY_RETURN_DAYS_SHOWN, emptySet())?.contains(day.toString()) == true
    }

    companion object {
        private const val PREF_NAME = "sp_rate_store"
        private const val KEY_HAS_RATED = "has_rated"
        private const val KEY_INSTALL_MS = "install_ms"
        private const val KEY_FIRST_ENTRY_SHOWN = "first_entry_shown"
        private const val KEY_FIRST_PDF_TRIGGERED = "first_pdf_triggered"
        private const val KEY_RETURN_DAYS_SHOWN = "return_days_shown"
    }
}
