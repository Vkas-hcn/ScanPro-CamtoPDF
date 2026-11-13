package com.mymy.mymy.review

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import com.bockcock.adjust.KvUtil
import com.buncead.common.util.Util
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.mymy.mymy.DialogUtil
import com.zppko.pdfplus.nor.remote.ConfigHelper

class ReviewUtil {
    val TAG = "ReviewU"

    fun checkReview(mActivity: AppCompatActivity){
        if ( ConfigHelper.canShowAdsss_a2()){
            showReview(mActivity)
        }
    }

    // 是否已经评价过
    val KEY_ALR_EVALUATION = "key_alr_evaluation"
    val VALUE_ALR_EVALUATION = 2323L
    fun showReview(mActivity: AppCompatActivity){
        val data: Long = KvUtil.getLong(KEY_ALR_EVALUATION)
        if (data == VALUE_ALR_EVALUATION){ // 已经评价过，就不显示评价弹框了
            return
        }

        if (isShowReview()){
            DialogUtil.review(mActivity,{
                KvUtil.setLong(KEY_ALR_EVALUATION,VALUE_ALR_EVALUATION)

                showShopRateUs(mActivity,it.toFloat())
            })
        }
    }

    fun isShowReview(): Boolean {
        if (isToday()) {
            return false
        }else{
            return true
        }
    }

    val KEY_CUR_TIME_100 = "review_gjdlk" // 是不是当天
    fun isToday(): Boolean {
        val curTime: Long = KvUtil.getLong(KEY_CUR_TIME_100)
        if (Util.isSameDayAsToday(curTime)) {
            Util.iii { "$TAG isToday() yes same day, lastTime=${curTime}" }
            return true
        } else {
            Util.iii { "$TAG isToday() no same day, lastTime=${curTime}" }
            // 重置时间
            KvUtil.setLong(KEY_CUR_TIME_100,System.currentTimeMillis())
            return false
        }
    }


    fun showShopRateUs(mActivity: Activity,rating: Float) {
        if (rating >= 4.0f) {
//        if (rating >= 4.5f) {
            val manager: ReviewManager = ReviewManagerFactory.create(mActivity)
            val request: Task<ReviewInfo> = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo: ReviewInfo = task.result
                    manager.launchReviewFlow(mActivity, reviewInfo)
                }
            }
        } else {

        }
    }

}