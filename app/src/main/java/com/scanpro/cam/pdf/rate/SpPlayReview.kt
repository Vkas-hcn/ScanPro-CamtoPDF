package com.scanpro.cam.pdf.rate

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

object SpPlayReview {

    private const val TAG = "PlayReview"

    fun launch(activity: Activity, onFinished: () -> Unit = {}) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    manager.launchReviewFlow(activity, task.result)
                        .addOnCompleteListener {
                            Log.i(TAG, "in-app review flow finished")
                            onFinished()
                        }
                } else {
                    Log.w(TAG, "requestReviewFlow failed, fallback to store", task.exception)
                    openStore(activity)
                    onFinished()
                }
            }
    }

    private fun openStore(activity: Activity) {
        val pkg = activity.packageName
        val market = Uri.parse("market://details?id=$pkg")
        val web = Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, market))
        } catch (_: ActivityNotFoundException) {
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, web)) }
        }
    }
}
