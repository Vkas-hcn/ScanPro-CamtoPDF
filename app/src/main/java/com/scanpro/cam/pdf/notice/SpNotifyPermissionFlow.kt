package com.scanpro.cam.pdf.notice

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.scanpro.cam.pdf.CamPdfApp
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.ads.SpHotStartGate
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.databinding.DialogSpNotifyPermissionBinding
import com.scanpro.cam.pdf.util.AppLog

/**
 * 通知权限申请流程（对齐 SmartPdfScan_luow 的「两处问 + 二次引导」，但**系统框永远优先**）。
 *
 * 两处调用：
 * - 启动页 [com.scanpro.cam.pdf.boot.SpBootActivity]：`showGuide = false`，只弹系统框；
 * - 主界面 [com.scanpro.cam.pdf.CamShellActivity]：`showGuide = true`，先弹系统框；只有当系统已经
 *   不可能再弹（`shouldShowRequestPermissionRationale == false`）时才弹自定义引导框。
 *
 * 关键约束（修复「系统框还能弹却弹了自定义框」）：
 * 1. 申请只在宿主页面拿到窗口焦点后发起——避免在 Activity 还没交互时被系统直接 deny；
 * 2. 只要 `shouldShowRequestPermissionRationale` 还为 true，就**绝不**弹引导，下次进入继续弹系统框；
 * 3. 用「本次申请是否真的弹过框 / 耗时」只做日志，不参与判定，避免 500ms 误判。
 *
 * 「打开设置」跳系统通知设置页，返回后重新判定；整条流程只回调一次 [onAnswered]。
 */
class SpNotifyPermissionFlow(
    private val activity: ComponentActivity,
    private val showGuide: Boolean,
    private val onAnswered: (granted: Boolean) -> Unit,
) {

    private var requestedAtElapsed = 0L

    /** 已发起、等结果。 */
    private var requesting = false

    /** 已检查过权限、等窗口焦点再发起。 */
    private var pending = false

    /** 一次申请只回调一次。 */
    private var answered = false

    private var guideDialog: Dialog? = null

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> handleResult(granted, fromSettings = false) }

    private val settingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { handleResult(SpNotifyPermit.isHaveNotifyPermission(activity), fromSettings = true) }

    /**
     * 每次进入页面调用一次：没授权就准备申请；窗口焦点就绪后真正弹系统框。
     */
    fun checkAndRequest() {
        if (answered || requesting || pending) return
        if (!SpBlockingLogic.get(activity).canRequestNotificationPermission()) {
            // 总闸不放行：不申请、也不引导。
            finish(false)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // 13 以下没有运行时权限；与 luow 一致，视为已就绪。
            notifyReadyIfGranted()
            finish(true)
            return
        }
        if (SpNotifyPermit.isHaveNotifyPermission(activity)) {
            notifyReadyIfGranted()
            finish(true)
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            finish(false)
            return
        }
        pending = true
        AppLog.notification.i(
            "通知权限待申请 showGuide=$showGuide hasFocus=${activity.hasWindowFocus()}",
        )
        if (activity.hasWindowFocus()) launchPending()
    }

    /** 宿主在 `onWindowFocusChanged` 里转发；拿到焦点才发起申请。 */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus || answered || requesting) return
        // 只在「已经决定要申请、正等焦点」时动作；避免在 checkAndRequest 之前误回调。
        if (!pending) return
        if (SpNotifyPermit.isHaveNotifyPermission(activity)) {
            // 等焦点期间用户已在系统里打开通知：直接按已授权收尾。
            notifyReadyIfGranted()
            finish(true)
            return
        }
        launchPending()
    }

    /** 页面销毁时收起引导框，避免窗口泄漏。 */
    fun dismiss() {
        guideDialog?.dismiss()
        guideDialog = null
    }

    private fun launchPending() {
        if (!pending || requesting || answered) return
        if (activity.isFinishing || activity.isDestroyed) {
            pending = false
            finish(false)
            return
        }
        pending = false
        requesting = true
        requestedAtElapsed = SystemClock.elapsedRealtime()
        AppLog.notification.i(
            "申请通知权限 rationale=${activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)}",
        )
        SpTracker.log(activity, SpTracker.SYSTEM_NOTIFICATION_POPUP_VIEW)
        runCatching {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }.onFailure {
            Log.w(TAG, "request notification permission failed", it)
            finish(false)
        }
    }

    private fun handleResult(granted: Boolean, fromSettings: Boolean) {
        if (answered) return
        requesting = false
        val elapsedMs = SystemClock.elapsedRealtime() - requestedAtElapsed
        val canAskAgain = activity.shouldShowRequestPermissionRationale(
            Manifest.permission.POST_NOTIFICATIONS,
        )
        AppLog.notification.i(
            "通知权限结果 granted=$granted fromSettings=$fromSettings " +
                "elapsed=${elapsedMs}ms rationale=$canAskAgain",
        )
        SpTracker.logNotifAuth(activity, granted)
        if (granted) {
            notifyReadyIfGranted()
            finish(true)
            return
        }
        if (showGuide && !fromSettings && shouldShowGuide(canAskAgain)) {
            showGuideDialog()
            return
        }
        finish(false)
    }

    /**
     * 只要系统**还能**弹框（`canAskAgain == true`）就绝不弹自定义引导，下次进入继续让系统框优先；
     * 只有系统明确不再弹（拒绝两次 / 被策略限制）时才走自定义引导。
     *
     * 弹框请求本身已经由 [launchPending] 做了「窗口焦点就绪」保护，不会在页面没准备好时
     * 被系统直接 deny，因此这里不再用耗时做二次判断。
     */
    private fun shouldShowGuide(canAskAgain: Boolean): Boolean = !canAskAgain

    private fun showGuideDialog() {
        if (activity.isFinishing || activity.isDestroyed) {
            finish(false)
            return
        }
        if (guideDialog?.isShowing == true) return
        val binding = DialogSpNotifyPermissionBinding.inflate(LayoutInflater.from(activity))
        val dialog = Dialog(activity, R.style.Theme_CamPdf_Dialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setWindowAnimations(0)
            setCanceledOnTouchOutside(true)
            setContentView(binding.root)
            setOnCancelListener { finish(false) }
            setOnDismissListener { guideDialog = null }
            setOnShowListener {
                window?.setLayout(
                    (activity.resources.displayMetrics.widthPixels * 0.84f).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
            }
        }
        binding.btnNotNow.setOnClickListener {
            dialog.dismiss()
            finish(false)
        }
        binding.btnOpenSettings.setOnClickListener {
            dialog.dismiss()
            openNotificationSettings()
        }
        guideDialog = dialog
        runCatching { dialog.show() }.onFailure {
            Log.w(TAG, "show notification guide failed", it)
            guideDialog = null
            finish(false)
        }
    }

    private fun openNotificationSettings() {
        if (activity.isFinishing || activity.isDestroyed) {
            finish(false)
            return
        }
        // 从系统设置回来不该再叠一层开屏（热启动阈值是 0）。
        SpHotStartGate.suppressNext()
        requestedAtElapsed = SystemClock.elapsedRealtime()
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        runCatching {
            settingsLauncher.launch(intent)
        }.onFailure {
            Log.w(TAG, "open notification settings failed", it)
            finish(false)
        }
    }

    /** 已授权时补点一次前台服务（与 luow 授权回调里的 `HintExt01.launchFg()` 等价）。 */
    private fun notifyReadyIfGranted() {
        if (!SpNotifyPermit.isHaveNotifyPermission(activity)) return
        runCatching { CamPdfApp.get().onNotificationPermissionReady() }
            .onFailure { Log.w(TAG, "foreground notification startup failed", it) }
    }

    private fun finish(granted: Boolean) {
        if (answered) return
        answered = true
        requesting = false
        pending = false
        guideDialog?.dismiss()
        guideDialog = null
        onAnswered(granted)
    }

    private companion object {
        private const val TAG = "SpNotifyPermitFlow"
    }
}
