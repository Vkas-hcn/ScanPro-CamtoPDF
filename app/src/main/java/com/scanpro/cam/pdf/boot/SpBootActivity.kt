package com.scanpro.cam.pdf.boot

import android.content.Intent
import android.os.Bundle
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
import com.mymy.mymy.ads.count.AdCountOpen
import com.mymy.mymy.ads.poi.AdTrackingHelperMy
import com.mymy.mymy.ads.poi.TrackAd
import com.scanpro.cam.pdf.CamShellActivity
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivitySpBootBinding
import com.scanpro.cam.pdf.leave.SpLeaveEntry

import com.zppko.pdfplus.nor.RAction
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

/**
 * Cold start: consent → preload App Open → show (or timeout) → [CamShellActivity].
 */
class SpBootActivity : SpActivity<ActivitySpBootBinding>() {
    companion object {
        private const val MIN_CREATIVE_WINDOW_MS = 6_000L
    }

    private val TAG = "SpBootGate"

    private val viewModel: SpBootViewModel by viewModels()
    private var finished = false
    private var openAdStarted = false
    private var adsFlowStarted = false
    private var splashStartedAtMs = 0L
    private var maxTimeoutMs = 15_000L
    private var minDelayMs = 3_000L

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
        if (finished || !adsFlowStarted || openAdStarted) return
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
            delay(1200L)
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
        val canShow = AdCountOpen.openCanShow()
        if (allow && canShow){
            startLoadTime = System.currentTimeMillis()
            runTasks()
        }else{
            mockWait()
        }
    }




    fun loadOther(){
        Util.iii { "$TAG loadOther()" }
        ImpressionUtils.load(CodeKeyAndEvent.zx_PLACE_INTER_ClEAN)
        ImpressionUtils.load(CodeKeyAndEvent.zx_PLACE_NATIVE_MAIN)
    }


    fun showAd(taskName: String){
        lifecycleScope.launch(Dispatchers.Main){
            ImpressionUtils.show(
                mActivity,taskName,
                CodeKeyAndEvent.bb_ACTION_LAUNCH_COLD,
                binding.adCom,
            ) {
                nextStep()
            }

            loadOther()
        }
    }


    fun tast(taskName: String) {
        // 执行耗时任务
        ImpressionUtils.load(taskName)
    }

    fun tastResult(taskName: String): Boolean {
        // 判断任务是否成功
        return ImpressionUtils.loadSuccess(taskName)
    }

    fun stepnext1_ecpm(first: String, second: String) {
        val ecpm1 = ImpressionUtils.getEcpm(first)
        val ecpm2 = ImpressionUtils.getEcpm(second)

        if (ecpm1>=ecpm2){
            Util.iii { "$TAG compare ecpm : ${ecpm1} >= ${ecpm2}" }
            showAd(first)
        }else{
            Util.iii { "$TAG compare ecpm : ${ecpm1} < ${ecpm2}" }
            showAd(second)
        }
    }
    fun stepnext2(first: String) {
        showAd(first)
    }
    fun stepnextFoce(){
        nextStep()
    }

    fun runTasks(){
        val placeArr = arrayOf(
            CodeKeyAndEvent.zx_PLACE_OPEN,
            CodeKeyAndEvent.zx_PLACE_INTER_SCAN,
            CodeKeyAndEvent.zx_PLACE_INTER_DONE
        )
        val pointArr = arrayOf(
            CodeKeyAndEvent.bb_ACTION_LAUNCH_COLD,
            CodeKeyAndEvent.bb_ACTION_INTER_CLEAN,
            CodeKeyAndEvent.bb_ACTION_INTER_CLEAN
        )

        // 任务成功通知
        val successChannel = Channel<String>(Channel.UNLIMITED)

        // 1. 依次启动任务，耗时任务放到 IO 线程执行
        val taskJob: Job = lifecycleScope.launch(Dispatchers.IO) {
            for (taskName in placeArr) {
                tast(taskName)
            }
        }

        // 2. 持续检测任务是否成功
        val monitorJob: Job = lifecycleScope.launch(Dispatchers.IO) {
            // 避免同一个任务重复上报成功
            val checked: MutableSet<String> = mutableSetOf<String>()

            while (isActive && checked.size < placeArr.size) {
                for (taskName in placeArr) {
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
                // 3. 最多等待 15000ms，等待第一个任务成功
                val first = withTimeoutOrNull(15000L) {
                    successChannel.receiveCatching().getOrNull()
                }
                if (first == null) {
                    // 15000ms 内没有任何任务成功
                    TrackAd.adShowTimeout(AdTrackingHelperMy.AdType.OPEN, CodeKeyAndEvent.zx_PLACE_OPEN,System.currentTimeMillis() - startLoadTime)
                    stepnextFoce()
                    return@launch
                }
                Util.iii { "$TAG request first ad -> $first" }

                // 收到第一个成功结果后，等待第二个成功结果，最多 1500ms
                val second = withTimeoutOrNull(1500L) {
                    successChannel.receiveCatching().getOrNull()
                }

                if (second != null) {
                    Util.iii { "$TAG request second 1500ms -> $first" }
                    stepnext1_ecpm(first,second)
                } else {
                    Util.iii { "$TAG wait 1500ms, no second ad" }
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
