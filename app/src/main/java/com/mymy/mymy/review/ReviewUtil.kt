package com.mymy.mymy.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play 应用内评价（不再有自定义评价弹框）。
 *
 * 首页每次进入都直接调用；是否真正弹出由 Google Play 的配额决定，
 * API 本身不返回“弹没弹”，所以用 [onFinished] 回调告诉调用方本次流程结束。
 */
class ReviewUtil {

    fun checkReview(activity: Activity, onFinished: () -> Unit = {}) {
        if (activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful && !activity.isFinishing && !activity.isDestroyed) {
                manager.launchReviewFlow(activity, task.result)
                    .addOnCompleteListener { onFinished() }
            } else {
                onFinished()
            }
        }
    }
}
