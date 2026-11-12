package com.scanpro.cam.pdf.rate

import android.app.Activity
import android.util.Log
import com.scanpro.cam.pdf.leave.SpLeaveEntry

object SpRateFlow {

    private const val TAG = "RatingOrch"
    private val RETURN_DAYS = setOf(2L, 3L, 7L)

    /** Set true to re-enable automatic rating prompts. Settings "Rate US" is unaffected. */
    const val AUTO_PROMPT_ENABLED = false

    enum class Trigger {
        FIRST_ENTRY,
        FIRST_PDF_SAVE,
        RETURN_DAY,
    }

    fun maybePromptFirstLaunch(activity: Activity, onDismissed: () -> Unit = {}): Boolean {
        return tryShow(activity, Trigger.FIRST_ENTRY, onDismissed)
    }

    fun tryPromptAfterPdfSave(activity: Activity, onDismissed: () -> Unit = {}): Boolean {
        return tryShow(activity, Trigger.FIRST_PDF_SAVE, onDismissed)
    }

    fun maybePromptOnHome(activity: Activity) {
        if (!AUTO_PROMPT_ENABLED) return
        if (activity.isFinishing || activity.isDestroyed) return
        if (SpLeaveEntry.isLeaveLaunch(activity.intent)) return
        tryShow(activity, Trigger.RETURN_DAY)
    }

    private fun tryShow(
        activity: Activity,
        trigger: Trigger,
        onDismissed: () -> Unit = {},
    ): Boolean {
        if (!AUTO_PROMPT_ENABLED) {
            Log.d(TAG, "skip: auto prompt disabled trigger=$trigger")
            onDismissed()
            return false
        }
        val prefs = SpRateStore(activity)
        prefs.ensureInstallRecorded()
        if (prefs.hasRated()) {
            Log.d(TAG, "skip: already rated trigger=$trigger")
            return false
        }
        if (!shouldShow(prefs, trigger)) {
            Log.d(TAG, "skip: gate trigger=$trigger day=${prefs.daysSinceInstall()}")
            return false
        }
        when (trigger) {
            Trigger.FIRST_ENTRY -> prefs.markFirstEntryPromptShown()
            Trigger.RETURN_DAY -> prefs.markReturnDayPromptShown(prefs.daysSinceInstall())
            Trigger.FIRST_PDF_SAVE -> prefs.markFirstPdfSaveTriggered()
        }
        Log.i(TAG, "show trigger=$trigger day=${prefs.daysSinceInstall()}")
        return SpRateSheet.show(activity, onDismissed)
    }

    private fun shouldShow(prefs: SpRateStore, trigger: Trigger): Boolean = when (trigger) {
        Trigger.FIRST_ENTRY ->
            !prefs.hasShownFirstEntryPrompt()
        Trigger.FIRST_PDF_SAVE ->
            !prefs.hasTriggeredFirstPdfSave()
        Trigger.RETURN_DAY -> {
            if (!prefs.hasShownFirstEntryPrompt() || prefs.hasRated()) {
                false
            } else {
                val day = prefs.daysSinceInstall()
                day in RETURN_DAYS && !prefs.hasShownReturnDayPrompt(day)
            }
        }
    }
}
