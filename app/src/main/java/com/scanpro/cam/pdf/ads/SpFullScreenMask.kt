package com.scanpro.cam.pdf.ads

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.scanpro.cam.pdf.R

/**
 * 全屏广告前的「遮罩 + 假加载」。照抄 ApolloPDF `ad/FullScreenDialogKt.java`：
 *
 * 1. 先弹一个不可取消、不可点外部关闭的全屏 loading 对话框；
 * 2. [LOADING_DIALOG_DURATION] 毫秒后再真正 `show()` 广告；
 * 3. 广告的 `show` 在回调里执行，所以用户看到的是「加载页 → 广告」，而不是白屏。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ad/FullScreenDialogKt.java`
 */
object SpFullScreenMask {

    private const val TAG = "SpFullScreenMask"

    /** Apollo 常量 `LOADING_DIALOG_DURATION = 500`。 */
    const val LOADING_DIALOG_DURATION = 500L

    private val dismissHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var loadingDialog: Dialog? = null

    /**
     * 展示遮罩，[onDismissed] 在遮罩关闭后（主线程）回调，用来放真正的 `ad.show()`。
     *
     * 与 Apollo 一致：Activity 正在结束/已销毁时**不弹遮罩**，直接回调。
     */
    fun show(activity: Activity, onDismissed: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onDismissed()
            return
        }
        // 旧实现每次 show 都 removeCallbacksAndMessages(null)，会把上一个还在等的遮罩回调
        // 一起取消掉，导致那条广告永远不会 show()。这里改成每个遮罩管自己的回调。
        var dialog: Dialog? = null
        val dismissAction = Runnable {
            runCatching {
                if (dialog?.isShowing == true) dialog?.dismiss()
                if (loadingDialog === dialog) loadingDialog = null
            }.onFailure { Log.w(TAG, "dismiss mask failed", it) }
            onDismissed()
        }
        try {
            val created = Dialog(activity, R.style.SpFullScreenLoadingDialog)
            created.setContentView(R.layout.sp_dialog_full_screen_loading)
            created.setCancelable(false)
            created.setCanceledOnTouchOutside(false)
            created.window?.let { window ->
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                // Apollo 这里做的是 immersive（隐藏系统栏），保持全屏观感
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            created.show()
            dialog = created
            loadingDialog = created
            dismissHandler.postDelayed(dismissAction, LOADING_DIALOG_DURATION)
        } catch (e: Exception) {
            Log.w(TAG, "showFullScreenMask: ${e.message}")
            dismissHandler.removeCallbacks(dismissAction)
            runCatching { dialog?.dismiss() }
            onDismissed()
        }
    }

    /** 当前是否正盖着遮罩。 */
    fun isShowing(): Boolean = loadingDialog?.isShowing == true
}
