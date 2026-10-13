package com.scanpro.cam.pdf.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object SpLocale {

    private const val PREFS = "sp_locale"
    private const val KEY_TAG = "tag"
    const val DEFAULT_TAG = "en"

    @Volatile
    var generation: Int = 0
        private set

    fun tag(context: Context): String {
        return prefs(context).getString(KEY_TAG, DEFAULT_TAG) ?: DEFAULT_TAG
    }

    fun set(context: Context, rawTag: String) {
        val next = normalize(rawTag)
        if (next == tag(context)) return
        prefs(context).edit().putString(KEY_TAG, next).apply()
        generation += 1
    }

    fun wrap(context: Context): Context {
        val locale = localeFor(tag(context))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    /** Clear AppCompat-managed locales so they won't force Activity stack recreate. */
    fun resetCompatLocalesIfNeeded(context: Context) {
        val stored = AppCompatDelegate.getApplicationLocales()
        if (!stored.isEmpty) {
            val migrated = normalize(stored.toLanguageTags().substringBefore(',').substringBefore('-'))
            if (!prefs(context).contains(KEY_TAG)) {
                prefs(context).edit().putString(KEY_TAG, migrated).apply()
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    fun normalize(tag: String): String {
        val lower = tag.lowercase().ifBlank { DEFAULT_TAG }
        return when (lower) {
            "id" -> "in"
            else -> lower
        }
    }

    fun localeFor(tag: String): Locale {
        val normalized = normalize(tag)
        return when (normalized) {
            "in" -> Locale.forLanguageTag("id")
            else -> Locale.forLanguageTag(normalized)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
