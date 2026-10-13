package com.scanpro.cam.pdf.rate

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.analytics.SpTracker

object SpRateSheet {

    private const val TAG = "RatingDialog"
    private const val HIGH_RATING_THRESHOLD = 4

    fun show(
        activity: Activity,
        onDismissed: () -> Unit = {},
    ): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        return runCatching {
            val dialog = Dialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val view = LayoutInflater.from(activity).inflate(R.layout.dialog_sp_rate, null)
            dialog.setContentView(view)
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.5f)
                setLayout(
                    (activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setGravity(Gravity.CENTER)
            }

            val ivHero = view.findViewById<ImageView>(R.id.iv_rating_hero)
            val tvMessage = view.findViewById<TextView>(R.id.tv_rating_message)
            val btnAction = view.findViewById<TextView>(R.id.btn_rating_action)
            val stars = listOf(
                view.findViewById<ImageView>(R.id.star1),
                view.findViewById<ImageView>(R.id.star2),
                view.findViewById<ImageView>(R.id.star3),
                view.findViewById<ImageView>(R.id.star4),
                view.findViewById<ImageView>(R.id.star5),
            )

            var selected = 5
            fun heroRes(count: Int): Int = when (count) {
                1 -> R.drawable.ic_1star
                2 -> R.drawable.ic_2star
                3 -> R.drawable.ic_3star
                4 -> R.drawable.ic_4star
                else -> R.drawable.ic_5star
            }

            fun refreshUi() {
                ivHero.setImageResource(heroRes(selected))
                stars.forEachIndexed { index, star ->
                    star.setImageResource(
                        if (index < selected) R.drawable.icon_star_selected
                        else R.drawable.icon_star_unselected,
                    )
                }
                if (selected >= HIGH_RATING_THRESHOLD) {
                    tvMessage.setText(R.string.rating_message_high)
                    btnAction.setText(R.string.rating_action_high)
                } else {
                    tvMessage.setText(R.string.rating_message_low)
                    btnAction.setText(R.string.rating_action_low)
                }
            }

            stars.forEachIndexed { index, star ->
                star.setOnClickListener {
                    selected = index + 1
                    refreshUi()
                }
            }
            refreshUi()

            var finished = false
            fun finishOnce(block: () -> Unit) {
                if (finished) return
                finished = true
                block()
            }

            dialog.setOnCancelListener {
                finishOnce {
                    dialog.dismiss()
                    onDismissed()
                }
            }

            btnAction.setOnClickListener {
                val prefs = SpRateStore(activity)
                if (selected < HIGH_RATING_THRESHOLD) {
                    SpTracker.log(activity, SpTracker.RATING_SUBMIT_LOW)
                    prefs.markRated()
                    Toast.makeText(
                        activity,
                        R.string.rating_thanks_internal,
                        Toast.LENGTH_LONG,
                    ).show()
                    dialog.dismiss()
                    finishOnce(onDismissed)
                } else {
                    SpTracker.log(activity, SpTracker.RATING_SUBMIT_HIGH)
                    prefs.markRated()
                    dialog.dismiss()
                    SpPlayReview.launch(activity) {
                        finishOnce(onDismissed)
                    }
                }
            }

            dialog.show()
            SpTracker.log(activity, SpTracker.RATING_DIALOG_SHOW)
            Log.i(TAG, "rating dialog shown")
            true
        }.getOrElse {
            Log.w(TAG, "show failed", it)
            onDismissed()
            false
        }
    }
}
