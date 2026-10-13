package com.scanpro.cam.pdf.boot

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.scanpro.cam.pdf.CamShellActivity
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivitySpBootBinding
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.analytics.SpSceneCode
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.guide.SpFirstLaunch
import com.scanpro.cam.pdf.guide.SpGuideLanguageActivity
import com.scanpro.cam.pdf.keepalive.SpForegroundServiceLauncher
import com.scanpro.cam.pdf.leave.SpLeaveEntry
import com.scanpro.cam.pdf.leave.SpUnInstall01Activity
import com.scanpro.cam.pdf.notice.SpNotifyCreator
import com.scanpro.cam.pdf.notice.SpNotifyExtras
import com.scanpro.cam.pdf.notice.SpNotifyPermit
import com.scanpro.cam.pdf.notice.SpSendManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 冷启动。Apollo `SplashActivity` 的等价：预加载 `ac_launch` 开屏广告 →
 * 到点（或超时）展示 → 卸载页 / 首次语言页 / [CamShellActivity]。
 */
class SpBootActivity : SpActivity<ActivitySpBootBinding>() {

    private val viewModel: SpBootViewModel by viewModels()
    private var finished = false
    private var openAdStarted = false
    private var adsFlowStarted = false
    private var maxTimeoutMs = SpBootViewModel.DEFAULT_BOOT_MS
    private var minDelayMs = SpBootViewModel.DEFAULT_BOOT_MIN_MS

    /** 通知带进来的目标（Apollo `jumpType`），开屏结束后转交主界面。 */
    private var entryJumpType: String? = null

    /** 通知带进来的触发场景（Apollo `sceneType`），埋点 `type` 参数用。 */
    private var entrySceneType: String = DEFAULT_SCENE_TYPE

    /** 本次开屏的场景码（Apollo `sceneCode`），由 `from_type` 推出来。 */
    private var entrySceneCode: String = SpSceneCode.AC_LAUNCH_OPEN

    override val applySystemBarsPadding: Boolean = false

    override fun inflateBinding(inflater: LayoutInflater): ActivitySpBootBinding {
        return ActivitySpBootBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        // Apollo `SplashActivity.onCreate`：通知进来时带的 flags 是 NEW_TASK|CLEAR_TASK，
        // 所以真正的冷启一定是 task root；不是就别再叠一个开屏。
        if (!isTaskRoot) {
            Log.i(TAG, "not task root, finish")
            finish()
            return
        }
        val baseBottomMargin =
            resources.getDimensionPixelSize(R.dimen.boot_progress_margin_bottom)
        ViewCompat.setOnApplyWindowInsetsListener(binding.bootRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = binding.bootProgress.layoutParams as ConstraintLayout.LayoutParams
            params.bottomMargin = baseBottomMargin + bars.bottom
            binding.bootProgress.layoutParams = params
            insets
        }
        ViewCompat.requestApplyInsets(binding.bootRoot)

        SpTracker.log(this, SpTracker.APP_COLD_BOOT)
        maxTimeoutMs = SpBootViewModel.DEFAULT_BOOT_MS
        minDelayMs = SpBootViewModel.DEFAULT_BOOT_MIN_MS
        binding.bootProgress.max = maxTimeoutMs.toInt().coerceAtLeast(1)
        binding.bootProgress.progress = 0

        handleNotificationEntry()

        // 先走 UMP（2.5 秒兜底），再预加载开屏。计时仍等通知权限框结束才开始。
        SpTracker.log(
            this,
            SpTracker.SHARK_AD_CHANCE,
            Bundle().apply {
                putString(KEY_SCENE, entrySceneCode)
                putString(KEY_TYPE, entrySceneType)
            },
        )
        lifecycleScope.launch {
            SpAdsHub.getOrNull()?.requestStartupConsent(this@SpBootActivity)
            SpAdsHub.getOrNull()?.preloadLaunchAd()
            checkNotifyPermission()
        }
    }

    // region Notification entry (Apollo `SplashActivity.onCreate` 开头那段)

    /**
     * 从通知点进来时的埋点、场景码与收尾。Apollo `SplashActivity.onCreate`：
     *
     * - `sys_notify` → `system_notification_click`，带 `type` = 触发场景
     * - `media_notify` → `media_notification_click`，带 `type`；并且**顺手撤掉**那条媒体式提醒
     * - `persistent_notify` → `always_notification_click`（没有 `type`）
     * - 不管从哪进来，最后都把 `SpSendManager` 的刷新循环停掉
     *
     * `jump_type` 原样转交给主界面（Apollo 也是把它塞进 HomeActivity 的 intent）；
     * `from_type` 推出来的场景码决定这次开屏广告按哪个场景上报。
     *
     * Apollo 的 `media_notify_2`（`channel_ado_pdf_media_two` 那一路）在这三个分支之外，
     * 既不上报点击也不撤通知，这里保持一样。
     */
    private fun handleNotificationEntry() {
        val fromType = intent.getStringExtra(SpNotifyExtras.FROM_TYPE)
        entryJumpType = intent.getStringExtra(SpNotifyExtras.JUMP_TYPE)
        entrySceneType = intent.getStringExtra(SpNotifyExtras.SCENE_TYPE) ?: DEFAULT_SCENE_TYPE
        entrySceneCode = fromTypeToSceneCode(fromType)
        val params = Bundle().apply { putString(KEY_TYPE, entrySceneType) }
        when (fromType) {
            SpNotifyCreator.FROM_SYS ->
                SpTracker.log(this, SpTracker.SYSTEM_NOTIFICATION_CLICK, params)

            SpNotifyCreator.FROM_MEDIA -> {
                SpTracker.log(this, SpTracker.MEDIA_NOTIFICATION_CLICK, params)
                runCatching {
                    NotificationManagerCompat.from(this).cancel(SpSendManager.MEDIA_NOTIFY_ID)
                }.onFailure { Log.w(TAG, "cancel media notification failed", it) }
            }

            SpNotifyCreator.FROM_PERSISTENT ->
                SpTracker.log(this, SpTracker.ALWAYS_NOTIFICATION_CLICK)
        }
        SpSendManager.stopNotifyUpdate()
    }

    /** Apollo `SplashActivity.fromTypeToSceneCode`：认不出来的 `from_type` 一律按自然打开算。 */
    private fun fromTypeToSceneCode(fromType: String?): String = when (fromType) {
        SpNotifyCreator.FROM_SYS -> SpSceneCode.AC_LAUNCH_NOTIFICATION
        SpNotifyCreator.FROM_MEDIA -> SpSceneCode.AC_LAUNCH_MEDIA
        SpNotifyCreator.FROM_PERSISTENT -> SpSceneCode.AC_LAUNCH_BAR
        SpSceneCode.FROM_FLOATING_WINDOW -> SpSceneCode.AC_LAUNCH_POPUP
        SpSceneCode.FROM_DELETE_APP -> SpSceneCode.AC_LAUNCH_UNINSTALL
        else -> SpSceneCode.AC_LAUNCH_OPEN
    }
    // endregion

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.progress.collect { value ->
                        binding.bootProgress.setProgressCompat(
                            value.coerceIn(0, binding.bootProgress.max),
                            true,
                        )
                    }
                }
                launch {
                    viewModel.finished.collect { done ->
                        if (done) onBootTimeout()
                    }
                }
            }
        }
    }

    private fun beginBootAdsFlow() {
        if (adsFlowStarted || finished || isFinishing) return
        adsFlowStarted = true
        viewModel.runBootProgress(maxTimeoutMs)

        lifecycleScope.launch {
            delay(minDelayMs.coerceAtLeast(0L))
            val remaining = (maxTimeoutMs - minDelayMs).coerceAtLeast(0L)
            runCatching {
                withTimeoutOrNull(remaining) {
                    SpAdsHub.getOrNull()?.configApplied?.first { it }
                }
            }
            tryPresentOpenIfReady()
        }
    }

    /**
     * Apollo `SplashActivity.showAdTimer`：到点就取 `ac_launch` 的缓存展示；
     * 没有缓存就直接进主界面。展示后由广告关闭回调推进。
     *
     * Apollo 传的是 `(AdPosition.AC_LAUNCH, sceneCode, sceneType)`；本工程 `showAd` 只吃
     * 一个 scene 串，取场景码那个（`sceneType` 只在 `Shark_ad_chance` 里当 `type` 用）。
     */
    private fun tryPresentOpenIfReady() {
        if (finished || openAdStarted || isFinishing) return
        val hub = SpAdsHub.getOrNull() ?: run { continueToShell(); return }
        openAdStarted = true
        hub.presentLaunchAd(this, entrySceneCode) { continueToShell() }
    }

    // region Notification permission (Apollo `SplashActivity.checkNotifyPermission`)

    /** Apollo `Constants.notify_permission_count`：最多问 2 次。 */
    private val notifyPermitPrefs by lazy { getSharedPreferences(PREFS_NOTIFY_PERMIT, MODE_PRIVATE) }

    private val notifyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            onNotifyPermissionAnswered()
        }

    /**
     * Apollo `SplashActivity.checkNotifyPermission`（`onCreate` 里调的那次）。
     *
     * **它管的是开屏广告什么时候开始计时**，不是「进主界面前补一次授权」：
     * 系统授权框没回答之前 `showAdTimer()` 不会跑，回答之后（或无需询问时）才跑。
     *
     * - 总闸不放行（[SpBlockingLogic.canRequestNotificationPermission]）、已有权限、
     *   或者已经问过 2 次 → 直接 [onNotifyPermissionAnswered]
     * - 否则弹系统授权框、计数 +1、报一次 `system_notification_popup_view`；
     *   用户点完由 [notifyPermissionLauncher] 走 [onNotifyPermissionAnswered]
     *
     * 与 Apollo 的差别只有一处：Apollo 在 `isStop()` 时既不弹也不继续（启动就停在那了），
     * 这里只判 `isFinishing / isDestroyed`。
     */
    private fun checkNotifyPermission() {
        var askedNow = false
        runCatching {
            if (!SpBlockingLogic.get(this).canRequestNotificationPermission()) return@runCatching
            val asked = notifyPermitPrefs.getInt(KEY_NOTIFY_PERMIT_COUNT, 0)
            if (SpNotifyPermit.isHaveNotifyPermission(this) || asked >= MAX_NOTIFY_PERMIT_ASK) {
                return@runCatching
            }
            if (isFinishing || isDestroyed) return
            notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            notifyPermitPrefs.edit().putInt(KEY_NOTIFY_PERMIT_COUNT, asked + 1).apply()
            SpTracker.log(this, SpTracker.SYSTEM_NOTIFICATION_POPUP_VIEW)
            askedNow = true
        }.onFailure { Log.w(TAG, "checkNotifyPermission failed", it) }
        // 没弹框 = 被拦 / 已有权限 / 问满 2 次：Apollo 走的是 lambda1 那条
        if (!askedNow) onNotifyPermissionAnswered()
    }

    /**
     * Apollo `onCreate$lambda$0` / `onCreate$lambda$1` 的共同部分：开计时；
     * 并且「已拿到通知权限」时补点一次前台服务。
     *
     * Apollo 这里还显式报一次 `SYSTEM_NOTIFICATION_ALLOW`；我们的
     * [SpNotifyPermit.isHaveNotifyPermission] 内部已经就报一次（带「只报一次」的落库标记），
     * 所以这里不重复报，净效果和 Apollo 一样是一次。
     */
    private fun onNotifyPermissionAnswered() {
        if (SpNotifyPermit.isHaveNotifyPermission(this)) {
            SpForegroundServiceLauncher.launchForegroundService(applicationContext)
        }
        beginBootAdsFlow()
    }
    // endregion

    private fun onBootTimeout() {
        if (finished || !adsFlowStarted || openAdStarted) return
        continueToShell()
    }

    /**
     * 开屏结束（展示完 / 超时）后的下一页。对照 Apollo `SplashActivity.nextPage`：
     * 卸载入口优先，其次首次语言页，否则首页。
     */
    private fun continueToShell() {
        if (finished || isFinishing) return
        finished = true
        val next = when {
            SpLeaveEntry.isLeaveLaunch(intent) ->
                Intent(this, SpUnInstall01Activity::class.java)
            SpFirstLaunch.isFirstOpen(this) ->
                Intent(this, SpGuideLanguageActivity::class.java).apply {
                    entryJumpType?.let { putExtra(SpNotifyExtras.JUMP_TYPE, it) }
                }
            else ->
                Intent(this, CamShellActivity::class.java).apply {
                    entryJumpType?.let { putExtra(SpNotifyExtras.JUMP_TYPE, it) }
                }
        }
        Log.i(TAG, "finishToMain target=${next.component?.className}")
        startActivity(next)
        finish()
    }

    companion object {
        private const val TAG = "SpBootGate"

        private const val PREFS_NOTIFY_PERMIT = "sp_notify_permit_ask"

        private const val KEY_NOTIFY_PERMIT_COUNT = "notify_permission_count"

        /** Apollo `Constants.notify_permission_count` 的上限。 */
        private const val MAX_NOTIFY_PERMIT_ASK = 2

        /** Apollo `scene_type` 缺省值。 */
        private const val DEFAULT_SCENE_TYPE = "start"

        /** 埋点里的触发场景参数名。 */
        private const val KEY_TYPE = "type"

        /** `Shark_ad_chance` 里的场景码参数名（Apollo `reportAdChance(scene, type)`）。 */
        private const val KEY_SCENE = "scene"
    }
}
