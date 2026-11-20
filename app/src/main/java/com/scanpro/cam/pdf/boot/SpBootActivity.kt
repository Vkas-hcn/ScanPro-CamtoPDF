package com.scanpro.cam.pdf.boot

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bockcock.adjust.Pin
import com.bockcock.adjust.Pt
import com.buncead.common.util.NetUtil
import com.buncead.common.util.PageMgr
import com.buncead.common.util.PermNotifyUtil
import com.buncead.common.util.Util
import com.mymy.mymy.ads.AdLog
import com.mymy.mymy.ads.count.AdCountOpen
import com.mymy.mymy.ads.poi.AdTrackingHelperMy
import com.mymy.mymy.ads.poi.TrackAd
import com.scanpro.cam.pdf.CamShellActivity
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivitySpBootBinding
import com.scanpro.cam.pdf.leave.SpLeaveEntry

import com.zppko.pdfplus.nor.RAction
import com.zppko.pdfplus.nor.ads.AdWait
import com.zppko.pdfplus.nor.ads.ImpressExt
import com.zppko.pdfplus.nor.ads.ImpressionUtils
import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent
import com.zppko.pdfplus.nor.horus.HintMt
import com.zppko.pdfplus.nor.horus.HintUtil
import com.zppko.pdfplus.nor.remote.ConfigHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Cold start: consent → preload App Open → show (or timeout) → [CamShellActivity].
 */
class SpBootActivity : SpActivity<ActivitySpBootBinding>() {
    companion object {
        /** 插屏：启动页持续前台且无广告遮挡的最大等待，超时判定广告没展示 */
        private const val NO_SHOW_TIMEOUT_MS = 8_000L
        /** 开屏在容器内展示，Activity 不会失去前台；放宽到能覆盖正常开屏时长 */
        private const val OPEN_NO_SHOW_TIMEOUT_MS = 15_000L
        /** 已曝光后的最大展示时长（主要兜底容器内开屏的关闭回调丢失） */
        private const val SHOWN_MAX_MS = 20_000L
        /** 广告盖过启动页后回到前台，给正常关闭回调的宽限 */
        private const val RESUME_GRACE_MS = 1_000L

        /** 启动页候选位：开屏 + 两个插屏位（按 eCPM 选一个展示） */
        private val SPLASH_PLACES = listOf(
            CodeKeyAndEvent.zx_PLACE_OPEN,
            CodeKeyAndEvent.zx_PLACE_INTER_SCAN,
            CodeKeyAndEvent.zx_PLACE_INTER_DONE
        )
    }

    private val TAG = "SpBootGate"

    private val viewModel: SpBootViewModel by viewModels()
    private var finished = false
    private var adsFlowStarted = false
    private var splashStartedAtMs = 0L
    private var maxTimeoutMs = AdWait.openMaxMs()
    private var minDelayMs = AdWait.openMinMs()

    // ---- 展示看门狗：只在启动页前台且有焦点（即广告不在遮挡）时兜底放行 ----
    private var adRequestedPlace: String? = null
    private var adCovered = false
    private var adDone = false
    private var adShown = false
    private var userLeft = false
    private var foregroundSince = 0L
    private var noShowTimeoutMs = NO_SHOW_TIMEOUT_MS
    private var noShowJob: Job? = null
    private var shownCapJob: Job? = null
    private var graceJob: Job? = null

    override val applySystemBarsPadding: Boolean = false

    override fun inflateBinding(inflater: LayoutInflater): ActivitySpBootBinding {
        return ActivitySpBootBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        val baseBottomMargin = resources.getDimensionPixelSize(R.dimen.boot_progress_margin_bottom)
        ViewCompat.setOnApplyWindowInsetsListener(binding.bootRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = binding.bootProgress.layoutParams as ConstraintLayout.LayoutParams
            params.bottomMargin = baseBottomMargin + bars.bottom
            binding.bootProgress.layoutParams = params
            binding.adPar.setPadding(0,bars.top,0,bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.bootRoot)

        splashStartedAtMs = System.currentTimeMillis()


        binding.bootProgress.max = maxTimeoutMs.toInt().coerceAtLeast(1)
        binding.bootProgress.progress = 0

//        if (adsFlowStarted && !openAdStarted && !finished) {
//            presentOpenAfterConsent()
//        }

        PermNotifyUtil.check2requestNotificationPermission(this,{
            step()
//            beginBootAdsFlow()
        })

        ana_ntf_click()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermNotifyUtil.onRequestPermissionsResult2(requestCode,permissions,grantResults,this,{bool ->
            step()
//            beginBootAdsFlow()
        })
    }

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

    // 进度开始
    private fun beginBootAdsFlow() {
        if (adsFlowStarted || finished || isFinishing) return
        adsFlowStarted = true
        splashStartedAtMs = System.currentTimeMillis()
        viewModel.runBootProgress(maxTimeoutMs)
    }

    // 进度结束
    private fun onBootTimeout() {
        if (finished || !adsFlowStarted) return
        if (isFinishing) return
        finished = true
//        SpAdsRuntime.getOrNull()?.markColdBootDone()
        navigateNext()
    }

    private fun elapsedMs(): Long = System.currentTimeMillis() - splashStartedAtMs


    //    fun ana_ntf_click(){
//        val notifyCLickType = intent.getIntExtra(HintUtil.CO_SHOW_DATA, -1)
//        val event = when (notifyCLickType) {
//            4856 -> Pin.ntf_top_click
//            8562 -> Pin.click_media_ntf
//            70101 -> Pin.click_scanpdf_ntf
//            70102 -> Pin.click_imgpdf_ntf
//            70103 -> Pin.click_depdf_ntf
//            else -> ""
//        }
//
//        if (event.isEmpty()){ }else{
//            HintMt.removeNOtify()
//            Pin.event(event)
//            Pin.event(Pin.reactivation_landing, Pt("source","notification").put("landing_page","home").put("landing_ok","true"))
//        }
//    }
    fun ana_ntf_click(){
        val notifyCLickType = intent.getIntExtra(HintUtil.CO_SHOW_TYPE, -1)

        val event = HintUtil.getTypeName(notifyCLickType)

        if (event.isEmpty()){ }else{
            HintMt.removeNOtify()
            Pin.event(Pin.notification_click, Pt("pos_id",event).put("trigger_source",event))
            Pin.event(Pin.reactivation_landing, Pt("source","notification").put("landing_page","home").put("landing_ok","true"))
        }

    }

    private fun navigateNext(){
        val action = intent.getStringExtra(RAction.Launch)
        if (RAction.LaunchShowOnceAds.equals(action)){
            finish()
        }else{
            val canShowAdsss_a2 = ConfigHelper.canShowAdsss_a2()
            Pin.anaMUSer(canShowAdsss_a2)

            val next = Intent(this, CamShellActivity::class.java)

            // 桌面「卸载」快捷方式：action 由中转页原样带到这里，缺了这一步主界面就不知道该进挽留页。
            val act_pageType: String? = intent.getStringExtra(PageMgr.key_st_launch)
            if (SpLeaveEntry.isLeaveLaunch(intent) ||
                PageMgr.nav_to_uninstall.equals(act_pageType)
            ) {
                SpLeaveEntry.markOpenLeave(next)
                Util.iii { "finishToMain11111111111" }
            }

            startActivity(next)
            finish()
        }
//        val next = Intent(this, CamShellActivity::class.java).apply {
//            if (SpLeaveEntry.isLeaveLaunch(intent)) {
//                SpLeaveEntry.markOpenLeave(this)
//            }
//        }
//        Log.i(TAG, "finishToMain")
//        startActivity(next)
//        finish()
    }

    fun mockWait(){
        lifecycleScope.launch {
            // 没有可展示的候选：也要至少停留 op_min_wt，避免启动页"闪一下"
            AdLog.log("WAIT_SPLASH", "-", null, "no_candidate min=${minDelayMs}ms")
            delay(minDelayMs)
            nextStep()
        }
    }
    fun nextStep(){
        viewModel.finishedProgress()
//        navigateNext()
    }

    fun step(){
        beginBootAdsFlow()
        initAds()
    }

    private fun initAds() {
        ImpressExt.init(this) {
//            val action = intent.getStringExtra(RAction.Launch)
//            when (action) {
//                Action.LaunchShowOnceAds -> viewModel.dispatch(IntentStart.ShowHotAds)
//                Action.LaunchNavToUninstall -> viewModel.dispatch(IntentStart.NavToUninstall)
//                else -> viewModel.dispatch(IntentStart.LoadAds(intent))
//            }

            netCheck()
        }
    }


    var startLoadTime = 0L
    fun netCheck(){
        // 网络是否允许加载和显示广告
        val allow = NetUtil.isUseNet_noVpn()
        // 启动页准入按“候选”判断：开屏封顶/为空时，插屏候选仍有额度也可以继续
        val eligible = eligibleSplashPlaces()
        // open_gap 只在开屏本身仍是候选时生效，避免开屏的间隔把插屏候选一起挡掉
        val gapOk = !eligible.contains(CodeKeyAndEvent.zx_PLACE_OPEN) || AdCountOpen.open_checkTimeDiff()
        val canShow = eligible.isNotEmpty() && gapOk
        AdLog.log(
            "GATE_SPLASH",
            "-",
            null,
            "allow=$allow eligible=$eligible gapOk=$gapOk show=${allow && canShow}"
        )
        if (allow && canShow){
            startLoadTime = System.currentTimeMillis()
            runTasks(eligible)
        }else{
            mockWait()
        }
    }

    /** 启动页可用候选：boxaxj 非空且该广告位当天还有额度 */
    private fun eligibleSplashPlaces(): List<String> {
        return SPLASH_PLACES.filter { place ->
            val setting = ImpressionUtils.currentSetting(place)
            setting != null && setting.zId.isNotEmpty() && ImpressionUtils.canShowAd(place)
        }
    }




    fun loadOther(){
        Util.iii { "$TAG loadOther()" }
        ImpressionUtils.load(CodeKeyAndEvent.zx_PLACE_INTER_ClEAN)
        ImpressionUtils.load(CodeKeyAndEvent.zx_PLACE_NATIVE_MAIN)
    }


    fun showAd(taskName: String){
        if (adDone || finished) return
        adRequestedPlace = taskName
        adCovered = false
        adDone = false
        adShown = false
        userLeft = false
        noShowTimeoutMs = if (ImpressionUtils.currentSetting(taskName)?.zType == CodeKeyAndEvent.xxdx_TYPE_OPEN) {
            OPEN_NO_SHOW_TIMEOUT_MS
        } else {
            NO_SHOW_TIMEOUT_MS
        }

        AdLog.log(
            "WAIT_SPLASH",
            taskName,
            ImpressionUtils.currentSetting(taskName)?.zId,
            "min=${minDelayMs}ms max=${maxTimeoutMs}ms elapsed=${elapsedMs()}ms"
        )
        lifecycleScope.launch(Dispatchers.Main){
            // op_min_wt：启动页最短停留。广告就绪得早也要等够 min 再展示，避免一闪而过
            val remainMs = minDelayMs - elapsedMs()
            if (remainMs > 0) {
                delay(remainMs)
                if (adDone || finished || isFinishing) return@launch
            }
            // 看门狗在真正发起展示时才启动：否则 min 配大了会被"没展示"超时提前判死
            foregroundSince = SystemClock.elapsedRealtime()
            startNoShowWatchdog()
            ImpressionUtils.show(
                mActivity,taskName,
                CodeKeyAndEvent.bb_ACTION_LAUNCH_COLD,
                binding.adCom,
                onShown = { onAdShown() },
            ) {
                finishAdFlow("ad_callback")
            }

            loadOther()
        }
    }

    /** 广告真正曝光（onAdShowed）：取消 no-show 计时，改用展示上限兜底 */
    private fun onAdShown() {
        runOnUiThread {
            if (adDone || finished) return@runOnUiThread
            adShown = true
            noShowJob?.cancel()
            startShownCapWatchdog()
        }
    }

    /**
     * 结束开屏广告流程（正常回调或看门狗）。
     * 只有启动页在前台且广告不在遮挡时，看门狗才会调用到这里。
     */
    private fun finishAdFlow(reason: String) {
        if (adDone || finished) return
        adDone = true
        noShowJob?.cancel()
        shownCapJob?.cancel()
        graceJob?.cancel()
        if (reason != "ad_callback") {
            AdLog.log("SHOW_WATCHDOG", adRequestedPlace ?: "-", null, "reason=$reason")
        }
        nextStep()
    }

    private fun isForegroundFocused(): Boolean {
        return lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && hasWindowFocus()
    }

    /** 兜底 1：启动页持续前台且有焦点，超时内广告还没上屏/没结束 → 判定没展示，放行 */
    private fun startNoShowWatchdog() {
        noShowJob?.cancel()
        noShowJob = lifecycleScope.launch {
            while (isActive && !adDone && !finished) {
                delay(200)
                if (adShown) return@launch
                if (adCovered) continue
                if (!isForegroundFocused()) {
                    foregroundSince = 0L
                    continue
                }
                if (foregroundSince == 0L) {
                    foregroundSince = SystemClock.elapsedRealtime()
                }
                if (SystemClock.elapsedRealtime() - foregroundSince >= noShowTimeoutMs) {
                    finishAdFlow("no_show_timeout")
                    return@launch
                }
            }
        }
    }

    /**
     * 兜底 1.5：广告已曝光后的最大展示时长。
     * 主要针对容器内开屏（Activity 一直前台+有焦点，onAdClosed 丢失时没有其它信号）。
     */
    private fun startShownCapWatchdog() {
        shownCapJob?.cancel()
        shownCapJob = lifecycleScope.launch {
            val shownAt = SystemClock.elapsedRealtime()
            while (isActive && !adDone && !finished) {
                delay(200)
                if (isForegroundFocused() && SystemClock.elapsedRealtime() - shownAt >= SHOWN_MAX_MS) {
                    finishAdFlow("shown_max_timeout")
                    return@launch
                }
            }
        }
    }

    /** 兜底 2：广告盖过启动页（onStop）后回到前台，给正常回调 [RESUME_GRACE_MS]，仍没结束则放行 */
    private fun scheduleDismissFallback() {
        graceJob?.cancel()
        graceJob = lifecycleScope.launch {
            delay(RESUME_GRACE_MS)
            if (!adDone && !finished && isForegroundFocused()) {
                finishAdFlow("dismiss_callback_lost")
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 用户按 Home/最近任务主动离开：这次 stop 不算“被广告盖住”
        if (adRequestedPlace != null) userLeft = true
    }

    override fun onResume() {
        super.onResume()
        userLeft = false
        if (adRequestedPlace == null || adDone) return
        foregroundSince = SystemClock.elapsedRealtime()
        if (adCovered) scheduleDismissFallback()
    }

    override fun onStop() {
        super.onStop()
        if (adRequestedPlace != null && !adDone && !userLeft) {
            // 非用户主动离开的 stop：广告 Activity 盖住了启动页
            adCovered = true
        }
        foregroundSince = 0L
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            foregroundSince = 0L
        } else if (adRequestedPlace != null && !adDone) {
            foregroundSince = SystemClock.elapsedRealtime()
            if (adCovered) scheduleDismissFallback()
        }
    }

    override fun onDestroy() {
        noShowJob?.cancel()
        shownCapJob?.cancel()
        graceJob?.cancel()
        super.onDestroy()
    }


    fun tast(taskName: String) {
        // 执行耗时任务
        ImpressionUtils.load(taskName)
    }

    fun tastResult(taskName: String): Boolean {
        // 判断任务是否成功
        return ImpressionUtils.loadSuccess(taskName)
    }

    /** 两个候选位都就绪：先排除已达 per-ID 上限的候选，再比 eCPM，单价高者展示（照 A `stepnext1_ecpm`）。 */
    private fun stepnext1_ecpm(first: String, second: String) {
        val firstOk = ImpressionUtils.canShowAd(first)
        val secondOk = ImpressionUtils.canShowAd(second)
        if (!firstOk) logCandidateSkip(first)
        if (!secondOk) logCandidateSkip(second)
        if (!firstOk && !secondOk) {
            stepnextFoce()
            return
        }
        if (!firstOk) {
            showAd(second)
            return
        }
        if (!secondOk) {
            showAd(first)
            return
        }
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
    fun stepnext2(first: String) {
        if (!ImpressionUtils.canShowAd(first)) {
            logCandidateSkip(first)
            stepnextFoce()
            return
        }
        showAd(first)
    }
    private fun logCandidateSkip(place: String) {
        AdLog.log("CANDIDATE_SKIP", place, ImpressionUtils.currentSetting(place)?.zId, "reason=daily_cap")
    }
    fun stepnextFoce(){
        nextStep()
    }

    fun runTasks(places: List<String>){
        // 任务成功通知
        val successChannel = Channel<String>(Channel.UNLIMITED)

        // 1. 依次启动任务，耗时任务放到 IO 线程执行
        val taskJob: Job = lifecycleScope.launch(Dispatchers.IO) {
            for (taskName in places) {
                tast(taskName)
            }
        }

        // 2. 持续检测任务是否成功
        val monitorJob: Job = lifecycleScope.launch(Dispatchers.IO) {
            // 避免同一个任务重复上报成功
            val checked: MutableSet<String> = mutableSetOf<String>()

            while (isActive && checked.size < places.size) {
                for (taskName in places) {
                    if (taskName !in checked && tastResult(taskName)) {
                        checked.add(taskName)
                        successChannel.send(taskName)
                    }
                }

                delay(50) // 每隔 50ms 检查一次
            }

            successChannel.close()
        }

        lifecycleScope.launch(Dispatchers.IO) {

            try {
                // 3. 最多等 op_max_wt（默认 16s），等待第一个候选就绪
                val first = withTimeoutOrNull(maxTimeoutMs.milliseconds) {
                    successChannel.receiveCatching().getOrNull()
                }
                if (first == null) {
                    // maxTimeoutMs 内没有任何候选就绪：放弃展示，直接进首页
                    AdLog.log("SHOW_TIMEOUT", places.firstOrNull() ?: CodeKeyAndEvent.zx_PLACE_OPEN, null, "waitMs=${System.currentTimeMillis() - startLoadTime} maxMs=${maxTimeoutMs}")
                    TrackAd.adShowTimeout(AdTrackingHelperMy.AdType.OPEN, CodeKeyAndEvent.zx_PLACE_OPEN,System.currentTimeMillis() - startLoadTime)
                    stepnextFoce()
                    return@launch
                }
                Util.iii { "$TAG request first ad -> $first" }

                // 收到第一个成功结果后，等待第二个成功结果
                val cmpWaitMs = AdWait.cmpMs()
                AdLog.log("WAIT_CMP", first, null, "cmp=${cmpWaitMs}ms")
                val second = withTimeoutOrNull(cmpWaitMs.milliseconds) {
                    successChannel.receiveCatching().getOrNull()
                }

                if (second != null) {
                    Util.iii { "$TAG request second ${cmpWaitMs}ms -> $first" }
                    stepnext1_ecpm(first,second)
                } else {
                    Util.iii { "$TAG wait ${cmpWaitMs}ms, no second ad" }
                    stepnext2(first)
                }

            } finally {
                monitorJob.cancel()
                taskJob.cancel()
                successChannel.cancel()
            }
        }

    }

}
