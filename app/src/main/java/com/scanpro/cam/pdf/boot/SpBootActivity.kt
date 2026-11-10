package com.scanpro.cam.pdf.boot

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
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
import com.scanpro.cam.pdf.ads.SpAdPosition
import com.scanpro.cam.pdf.ads.SpAdShowConfig
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.ads.SpHotStartGate
import com.scanpro.cam.pdf.analytics.AdTrackingHelper
import com.scanpro.cam.pdf.analytics.SpSceneCode
import com.scanpro.cam.pdf.analytics.SpTracker
import com.scanpro.cam.pdf.analytics.SpTrackEvents
import com.scanpro.cam.pdf.guide.SpFirstLaunch
import com.scanpro.cam.pdf.guide.SpGuideLanguageActivity
import com.scanpro.cam.pdf.notice.SpNotificationEntry
import com.scanpro.cam.pdf.notice.SpNotifyPermissionFlow
import com.scanpro.cam.pdf.leave.SpLeaveEntry
import com.scanpro.cam.pdf.leave.SpUnInstall01Activity
import com.scanpro.cam.pdf.notice.SpNotifyCreator
import com.scanpro.cam.pdf.notice.SpNotifyExtras
import com.zppko.pdfplus.nor.horus.HintMt
import com.zppko.pdfplus.nor.horus.HintUtil
import com.zppko.pdfplus.nor.ads.ImpressionUtils
import com.scanpro.cam.pdf.notice.notificationTriggerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private var bootFinishStarted = false
    private var bootFlowStartedAt = 0L
    private var maxTimeoutMs = SpBootViewModel.DEFAULT_BOOT_MS

    /**
     * 原有 SpHotStartGate 负责热启动触发；兼容接收 `g_launch=g_sh_on_ad` 入参。
     * 热启动开屏结束后回到原页面；无通知的桌面图标再次进入也按热启动处理。
     */
    private var isHotStart = false

    /** 通知带进来的目标（Apollo `jumpType`），开屏结束后转交主界面。 */
    private var entryJumpType: String? = null

    /** 通知带进来的触发场景（Apollo `sceneType`），埋点 `type` 参数用。 */
    private var entrySceneType: String = DEFAULT_SCENE_TYPE

    /** 本次开屏的场景码（Apollo `sceneCode`），由 `from_type` 推出来。 */
    private var entrySceneCode: String = SpSceneCode.AC_LAUNCH_OPEN

    /** 第一处通知权限申请（第二处在 [CamShellActivity]，那边还带二次引导）。 */
    private lateinit var notifyPermissionFlow: SpNotifyPermissionFlow

    override val applySystemBarsPadding: Boolean = false

    override fun inflateBinding(inflater: LayoutInflater): ActivitySpBootBinding {
        return ActivitySpBootBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        SpHotStartGate.onSplashCreated()
        notifyPermissionFlow = SpNotifyPermissionFlow(this, showGuide = false) {
            // 启动页只负责「问一次」：无论允许/拒绝，都继续开屏流程（与 Apollo 一致）。
            onNotifyPermissionAnswered()
        }
        isHotStart = intent?.getBooleanExtra(EXTRA_HOT_START, false) == true ||
            SpNotificationEntry.isHotStart(intent)
        // Apollo `SplashActivity.onCreate`：通知进来时带的 flags 是 NEW_TASK|CLEAR_TASK，
        // 所以真正的冷启一定是 task root。
        // 不是 root 只剩一种情况：桌面图标在「任务还在后台」时被系统压进现有任务栈——
        // 这本来就是热启动要做的事，就地按热启动继续走，而不是像 Apollo 那样直接 finish
        //（直接 finish 等于点图标什么都没发生，还白闪一帧）。
        if (!isTaskRoot && !isHotStart && !SpNotificationEntry.isNotification(intent)) {
            // 底下本来就是一层启动页/挽留/引导流程（比如冷启的开屏还没走完），别叠第二层
            if (SpHotStartGate.isProtectedSurface()) {
                Log.i(TAG, "not task root and already in a protected flow, finish")
                finish()
                return
            }
            isHotStart = true
            Log.i(TAG, "not task root, treat as hot start")
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

        // 热启动不是冷启，别把 APP_COLD_BOOT 的基数冲掉
        if (!isHotStart) {
            SpTracker.log(this, SpTracker.APP_COLD_BOOT)
        }
        maxTimeoutMs = SpBootViewModel.DEFAULT_BOOT_MS
        binding.bootProgress.max = maxTimeoutMs.toInt().coerceAtLeast(1)
        binding.bootProgress.progress = 0

        handleNotificationEntry()

        SpTracker.log(
            this,
            SpTracker.SHARK_AD_CHANCE,
            Bundle().apply {
                putString(KEY_SCENE, entrySceneCode)
                putString(KEY_TYPE, entrySceneType)
            },
        )

        // PDF26 顺序：通知权限 → UMP → 加载 → 等待/展示。
        // 权限回答后由 onNotifyPermissionAnswered() 先跑 UMP，再进入 beginBootAdsFlow()。
        notifyPermissionFlow.checkAndRequest()
    }

    /**
     * 启动页触发开屏比价预加载（`ac_launch` + `ac_launch_int`）。
     *
     * 配置通常在启动页之后才到：首次调用可能因为还没有 `ac_launch` 配置而直接返回，
     * 所以页面存活期间每次配置下发都检查一次。页面销毁后 `lifecycleScope` 取消，
     * 后台进程（没有启动页）不会走到这里。
     */
    private fun preloadLaunchBidAdsForBoot() {
        val hub = SpAdsHub.getOrNull() ?: return
        hub.preloadLaunchBidAds("boot")
        lifecycleScope.launch {
            // 配置可能在启动页之后才到，也可能先在缓存里没有 ac_launch、远端才补上；
            // 每次配置下发都检查一次，只有页面还活着且真的有开屏位时才补加载。
            hub.configGeneration.collect {
                if (!isFinishing && !isDestroyed &&
                    hub.config().adInfoList(SpAdPosition.AC_LAUNCH) != null
                ) {
                    hub.preloadLaunchBidAds("boot-config")
                }
            }
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
        val fromType = SpNotificationEntry.fromType(intent)
        entryJumpType = SpNotificationEntry.route(intent)
        entrySceneType = SpNotificationEntry.sceneType(intent)
        entrySceneCode = fromTypeToSceneCode(fromType)
        if (intent.getBooleanExtra(SpNotificationEntry.A_NO_REPORT, false)) return
        val params = Bundle().apply { putString(KEY_TYPE, entrySceneType) }
        when (fromType) {
            SpNotifyCreator.FROM_SYS ->
                SpTracker.log(this, SpTracker.SYSTEM_NOTIFICATION_CLICK, params)

            SpNotifyCreator.FROM_MEDIA -> {
                SpTracker.log(this, SpTracker.MEDIA_NOTIFICATION_CLICK, params)
                runCatching {
                    HintMt.removeNOtify()
                    if (intent.hasExtra(SpNotificationEntry.A_SHOW_TYPE)) {
                        NotificationManagerCompat.from(this).cancel(
                            intent.getIntExtra(SpNotificationEntry.A_SHOW_DATA, 8562),
                        )
                    }
                }.onFailure { Log.w(TAG, "cancel media notification failed", it) }
            }

            SpNotifyCreator.FROM_PERSISTENT ->
                SpTracker.log(this, SpTracker.ALWAYS_NOTIFICATION_CLICK)
        }
        // 规范 E17 notification_click + E19 reactivation_landing（通知拉活落地）
        val posId = when (fromType) {
            SpNotifyCreator.FROM_PERSISTENT -> "always"
            SpNotifyCreator.FROM_SYS, SpNotifyCreator.FROM_MEDIA, SpNotifyCreator.FROM_MEDIA_TWO -> entrySceneType
            else -> null
        }
        if (posId != null) {
            val triggerSource = if (fromType == SpNotifyCreator.FROM_PERSISTENT) {
                "resident"
            } else {
                notificationTriggerSource(entrySceneType)
            }
            SpTrackEvents.notificationClick(this, posId, triggerSource)
            SpTrackEvents.reactivationLanding("notification", entryJumpType ?: "home", posId)
        }
        // 冷启动时停止通知发送方的重复发送任务。
        if (!isHotStart) {
            HintUtil.stopCalling()
            HintMt.stopCalling()
        }
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
                viewModel.progress.collect { value ->
                    binding.bootProgress.setProgressCompat(
                        value.coerceIn(0, binding.bootProgress.max),
                        true,
                    )
                }
            }
        }
    }

    /**
     * 开屏时序（对齐 PDF26）：
     * 启动进度 → UMP（仅欧盟）→ 加载 → 最多等 `launchWaitMs`（默认 15s）→ 就绪立即展示
     * → 关闭后至少停留 200ms → 500ms 补满进度 → 跳转。
     *
     * 进度条只负责视觉，不再作为导航依据（PDF26 的进度也不触发导航）。
     * 无广告超时后直接进入收尾，不再 arm 首页补展示。
     */
    private fun beginBootAdsFlow() {
        if (adsFlowStarted || finished || isFinishing) return
        adsFlowStarted = true
        bootFlowStartedAt = SystemClock.elapsedRealtime()

        val hub = SpAdsHub.getOrNull()
        val showConfig = hub?.showConfig() ?: SpAdShowConfig()
        // 热启动是「每次回前台」都会走一遍的路径，不能按冷启的十几秒去等。
        maxTimeoutMs = if (isHotStart) {
            HOT_START_MAX_WAIT_MS + BOOT_SLACK_MS
        } else {
            maxOf(
                SpBootViewModel.DEFAULT_BOOT_MS,
                showConfig.launchWaitMs + BOOT_SLACK_MS,
            )
        }
        binding.bootProgress.max = maxTimeoutMs.toInt().coerceAtLeast(1)
        viewModel.runBootProgress(maxTimeoutMs)

        lifecycleScope.launch {
            if (hub == null) {
                onLoadingFinished()
                return@launch
            }
            // PDF26：进度已启动，UMP 之后才 load。
            hub.requestStartupConsent(this@SpBootActivity)
            if (finished || isFinishing || isDestroyed) return@launch
            preloadLaunchBidAdsForBoot()

            val waitLimit = if (isHotStart) {
                minOf(showConfig.launchWaitMs, HOT_START_MAX_WAIT_MS)
            } else {
                showConfig.launchWaitMs
            }
            // 开屏比价：并发加载两个位，轮询就绪，eCPM 高者展示（照 SmartPdfScan_luow runTasks）。
            runTasks(waitLimit)
        }
    }

    /**
     * PDF26 `SplashActivity.onLoadingFinished`：至少停留 200ms，再用 500ms 补满进度，最后跳转。
     */
    private fun onLoadingFinished() {
        if (bootFinishStarted || finished || isFinishing) return
        bootFinishStarted = true
        val elapsed = SystemClock.elapsedRealtime() - bootFlowStartedAt
        val remainMin = (MIN_SHOW_MS - elapsed).coerceAtLeast(0L)
        lifecycleScope.launch {
            if (remainMin > 0L) delay(remainMin)
            viewModel.finishBootProgress(FILL_DURATION_MS)
            continueToShell()
        }
    }

    // region 开屏比价（照 SmartPdfScan_luow SpBootActivity 的 runTasks 结构）

    /** 加载单个候选位（照 A `tast`）。preload 阶段已加载则幂等跳过。 */
    private fun tast(taskName: String) {
        ImpressionUtils.load(taskName)
    }

    /** 判断候选位是否就绪（照 A `tastResult`）。 */
    private fun tastResult(taskName: String): Boolean =
        ImpressionUtils.loadSuccess(taskName)

    /** 两个候选位都就绪：比 eCPM，单价高者展示（照 A `stepnext1_ecpm`）。 */
    private fun stepnext1_ecpm(first: String, second: String) {
        // 优先用 TradPlus 比价 SDK（recursiveComparePrice）探测真实 eCPM；失败回退到广告对象自带 eCPM。
        val ecpm1 = ImpressionUtils.getComparePrice(first).takeIf { it > 0 } ?: ImpressionUtils.getEcpm(first)
        val ecpm2 = ImpressionUtils.getComparePrice(second).takeIf { it > 0 } ?: ImpressionUtils.getEcpm(second)
        if (ecpm1 >= ecpm2) {
            Log.i(TAG, "compare ecpm : $ecpm1 >= $ecpm2")
            showAd(first)
        } else {
            Log.i(TAG, "compare ecpm : $ecpm1 < $ecpm2")
            showAd(second)
        }
    }

    /** 只有一个候选位就绪：直接展示（照 A `stepnext2`）。 */
    private fun stepnext2(first: String) {
        showAd(first)
    }

    /** 超时无广告：跳过开屏进入收尾（照 A `stepnextFoce`）。 */
    private fun stepnextFoce() {
        onLoadingFinished()
    }

    /** 展示胜出广告位（照 A `showAd`），关闭后推进 [onLoadingFinished]。 */
    private fun showAd(place: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (finished || openAdStarted || isFinishing) return@launch
            val hub = SpAdsHub.getOrNull() ?: run { onLoadingFinished(); return@launch }
            openAdStarted = true
            hub.presentLaunchAd(this@SpBootActivity, place, entrySceneCode) { onLoadingFinished() }
        }
    }

    /** 开屏比价主流程（照 A `runTasks`）。 */
    private fun runTasks(waitLimitMs: Long) {
        val placeArr = arrayOf(SpAdPosition.AC_LAUNCH, SpAdPosition.AC_LAUNCH_INT)
        val successChannel = Channel<String>(Channel.UNLIMITED)
        val loadStartedAt = SystemClock.elapsedRealtime()

        // 1. 依次启动加载（耗时任务放 IO）。
        val taskJob: Job = lifecycleScope.launch(Dispatchers.IO) {
            for (taskName in placeArr) {
                tast(taskName)
            }
        }

        // 2. 持续检测就绪，去重后发到 channel。
        val monitorJob: Job = lifecycleScope.launch(Dispatchers.IO) {
            val checked: MutableSet<String> = mutableSetOf()
            while (isActive && checked.size < placeArr.size) {
                for (taskName in placeArr) {
                    if (taskName !in checked && tastResult(taskName)) {
                        checked.add(taskName)
                        successChannel.send(taskName)
                    }
                }
                delay(50)
            }
            successChannel.close()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 3. 最多等 waitLimitMs，等第一个就绪。
                val first = withTimeoutOrNull(waitLimitMs) {
                    successChannel.receiveCatching().getOrNull()
                }
                if (first == null) {
                    // 规范 E13：等兜底广告超时后放行。
                    SpTrackEvents.adShowTimeout(
                        AdTrackingHelper.AdType.OPEN,
                        SpAdPosition.AC_LAUNCH,
                        SystemClock.elapsedRealtime() - loadStartedAt,
                    )
                    stepnextFoce()
                    return@launch
                }
                Log.i(TAG, "request first ad -> $first")

                // 4. 再最多等 1500ms，看第二个是否也绪。
                val second = withTimeoutOrNull(SECOND_AD_WAIT_MS) {
                    successChannel.receiveCatching().getOrNull()
                }
                if (second != null) {
                    Log.i(TAG, "request second -> $second")
                    stepnext1_ecpm(first, second)
                } else {
                    Log.i(TAG, "wait $SECOND_AD_WAIT_MS ms, no second ad")
                    stepnext2(first)
                }
            } finally {
                monitorJob.cancel()
                taskJob.cancel()
                successChannel.cancel()
            }
        }
    }

    // endregion

    // region Notification permission

    /**
     * 两处问的第一处（启动页），由 [SpNotifyPermissionFlow] 发起；`showGuide = false`，
     * 系统不再弹框时这里不打扰用户，交给 [CamShellActivity] 的二次引导。
     *
     * 与 PDF26 `SplashActivity` 一致：通知权限回答之后进入 [beginBootAdsFlow]，
     * 由它先启动进度，再跑 UMP（仅欧盟、无超时、失败也继续），UMP 结束才加载/等待/展示。
     * 系统授权框没回答之前不做任何 UMP 或广告请求。
     */
    private fun onNotifyPermissionAnswered() {
        beginBootAdsFlow()
    }
    // endregion

    /**
     * 开屏结束（展示完 / 超时）后的下一页。对照 Apollo `SplashActivity.nextPage`：
     * 卸载入口优先，其次首次语言页，否则首页。
     */
    private fun continueToShell() {
        if (finished || isFinishing) return
        finished = true
        // 热启动：启动页只是叠在原页面上的一层，广告结束（或超时）直接退出，用户回到原来那一页
        if (isHotStart) {
            Log.i(TAG, "hot start finish, back to previous page")
            finish()
            return
        }
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

    override fun onDestroy() {
        super.onDestroy()
        // 启动页离场：通知 SpHotStartGate 放行冷却，否则原页面 onStart 时会再叠一层
        SpHotStartGate.onSplashDestroyed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::notifyPermissionFlow.isInitialized) {
            notifyPermissionFlow.onWindowFocusChanged(hasFocus)
        }
    }

    companion object {
        private const val TAG = "SpBootGate"

        /** 热启动标记：由 [SpHotStartGate] 拉起时带上，表示这次是叠在当前页面上的开屏。 */
        const val EXTRA_HOT_START = "sp_hot_start"

        /** 热启动等开屏广告的上限。每次回前台都要走一遍，不能按冷启的等待时长来。 */
        private const val HOT_START_MAX_WAIT_MS = 3_000L

        /** 开屏比价：第一个就绪后再等第二个的时长（照 SmartPdfScan_luow 的 1500ms）。 */
        private const val SECOND_AD_WAIT_MS = 1500L

        /** 开屏兜底总时长在「等待」之外的余量。 */
        private const val BOOT_SLACK_MS = 1_000L

        /** PDF26 `MIN_WAIT_MS`：开屏页至少停留 200ms。 */
        private const val MIN_SHOW_MS = 200L

        /** PDF26 `FILL_DURATION_MS`：最后 500ms 把进度补满。 */
        private const val FILL_DURATION_MS = 500L

        /** Apollo `scene_type` 缺省值。 */
        private const val DEFAULT_SCENE_TYPE = "start"

        /** 埋点里的触发场景参数名。 */
        private const val KEY_TYPE = "type"

        /** `Shark_ad_chance` 里的场景码参数名（Apollo `reportAdChance(scene, type)`）。 */
        private const val KEY_SCENE = "scene"
    }
}
