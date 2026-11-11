package com.scanpro.cam.pdf.rate

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.scanpro.cam.pdf.ads.SpHotStartGate

object SpPlayReview {

    private const val TAG = "PlayReview"

    /**
     * @param fallbackToStore 请求评价流失败时是否跳商店。首页自动调用必须传 false：
     *        否则从商店返回会再次触发 onResume，形成「失败→跳商店→返回→再失败」的死循环。
     */
    fun launch(
        activity: Activity,
        fallbackToStore: Boolean = true,
        onFinished: () -> Unit = {},
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }
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
                    Log.w(TAG, "requestReviewFlow failed, fallback=$fallbackToStore", task.exception)
                    if (fallbackToStore) openStore(activity)
                    onFinished()
                }
            }
    }

    private fun openStore(activity: Activity) {
        val pkg = activity.packageName
        val market = Uri.parse("market://details?id=$pkg")
        val web = Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
        // 跳商店再回来不该弹开屏
        SpHotStartGate.suppressNext()
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, market))
        } catch (_: ActivityNotFoundException) {
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, web)) }
        }
    }
}
