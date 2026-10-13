package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Google UMP consent for EEA / UK / Switzerland before requesting ads.
 */
class SpUmpGate(
    context: Context,
) {

    private val appContext = context.applicationContext
    private val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)

    fun adsEligible(): Boolean = consentInformation.canRequestAds()

    fun isPrivacyOptionsRequired(): Boolean =
        consentInformation.privacyOptionsRequirementStatus ==
            com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Runs UMP update + optional form. Returns when finished or when [timeoutMs] elapses.
     */
    suspend fun requestUmpConsent(activity: Activity, timeoutMs: Long = DEFAULT_TIMEOUT_MS): FormError? {
        val result = withTimeoutOrNull(timeoutMs.coerceAtLeast(500L)) {
            gatherConsentInternal(activity)
        }
        if (result == null) {
            Log.w(
                DIAG_TAG,
                "UMP timed out after ${timeoutMs}ms status=${consentInformation.consentStatus} " +
                    "adsEligible=${consentInformation.canRequestAds()}",
            )
        }
        return result
    }

    private suspend fun gatherConsentInternal(activity: Activity): FormError? {
        return suspendCancellableCoroutine { cont ->
            runCatching {
                Log.i(
                    DIAG_TAG,
                    "UMP start forceEea=$DEBUG_FORCE_EEA " +
                        "status=${consentInformation.consentStatus} " +
                        "adsEligible=${consentInformation.canRequestAds()}",
                )
                val paramsBuilder = ConsentRequestParameters.Builder()
                if (DEBUG_FORCE_EEA) {
                    val debugSettings = ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .build()
                    paramsBuilder.setConsentDebugSettings(debugSettings)
                }
                val params = paramsBuilder.build()

                consentInformation.requestConsentInfoUpdate(
                    activity,
                    params,
                    {
                        Log.i(
                            DIAG_TAG,
                            "UMP info updated status=${consentInformation.consentStatus} " +
                                "formAvailable=${consentInformation.isConsentFormAvailable} " +
                                "adsEligible=${consentInformation.canRequestAds()}",
                        )
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            if (formError != null) {
                                Log.w(
                                    DIAG_TAG,
                                    "UMP form error code=${formError.errorCode} msg=${formError.message}",
                                )
                            } else {
                                Log.i(DIAG_TAG, "UMP form finished (shown or not required)")
                            }
                            Log.i(
                                DIAG_TAG,
                                "UMP done status=${consentInformation.consentStatus} " +
                                    "adsEligible=${consentInformation.canRequestAds()} " +
                                    "privacyOptions=${consentInformation.privacyOptionsRequirementStatus}",
                            )
                            if (cont.isActive) cont.resume(formError)
                        }
                    },
                    { requestError ->
                        Log.w(
                            DIAG_TAG,
                            "UMP update error code=${requestError.errorCode} msg=${requestError.message} " +
                                "adsEligible=${consentInformation.canRequestAds()}",
                        )
                        if (cont.isActive) cont.resume(requestError)
                    },
                )
            }.onFailure { error ->
                Log.w(DIAG_TAG, "UMP gather crashed", error)
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    companion object {
        const val DIAG_TAG = "SpAdsDiag"
        const val DEFAULT_TIMEOUT_MS = 2_500L

        /** Set true temporarily to verify GDPR form (needs AdMob Privacy messaging + test device). */
        const val DEBUG_FORCE_EEA = false
    }
}
