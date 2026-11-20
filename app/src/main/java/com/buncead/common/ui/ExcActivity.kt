package com.buncead.common.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.buncead.common.util.NetUtil

import com.mymy.mymy.ads.AdLog
import com.mymy.mymy.ads.count.AdCountInter
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.databinding.ActivityExcBinding
import com.scanpro.cam.pdf.scan.CamCaptureActivity
import com.zppko.pdfplus.nor.ads.AdWait
import com.zppko.pdfplus.nor.ads.ImpressionUtils
import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExcActivity : BaseActivity() {




    private lateinit var binding: ActivityExcBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExcBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.adPar.setPadding(0,bars.top,0,bars.bottom)
            insets
        }

        netCheck()
    }

    fun netCheck(){
        // 网络是否允许加载和显示广告
        val allow = NetUtil.isUseNet_noVpn()
        val setting = ImpressionUtils.currentSetting(CodeKeyAndEvent.zx_PLACE_INTER_ClEAN)
        val capOk = AdCountInter.inter_idCountCheck(setting)
        val gapOk = AdCountInter.inter_checkTimeDiff()
        val canShow = capOk && gapOk
        AdLog.log(
            "GATE_INTER",
            CodeKeyAndEvent.zx_PLACE_INTER_ClEAN,
            setting?.zId,
            "allow=$allow capOk=$capOk gapOk=$gapOk show=${allow && canShow} caps=${setting?.zDayShow}/${setting?.zDayClick}"
        )
        if (allow && canShow){
            binding.lyAd.icon.setImageResource(R.drawable.ic_scan_big)
            binding.lyAd.content.setText(R.string.op_camera_hiti)
            binding.lyAd.root.visibility = View.VISIBLE
            mockScan()
        }else{
//            binding.scanView.root.visibility = View.GONE
            nextStep()
        }
    }

    fun mockScan(){
        ImpressionUtils.load(CodeKeyAndEvent.zx_PLACE_INTER_ClEAN)

        lifecycleScope.launch(Dispatchers.IO) {
            // int_min_wt / int_max_wt：最多等到 max，就绪即返回；再补足最短停留 min
            AdWait.awaitInterstitial(CodeKeyAndEvent.zx_PLACE_INTER_ClEAN)

            withContext(Dispatchers.Main) {

                ImpressionUtils.show(
                    mActivity,
                    CodeKeyAndEvent.zx_PLACE_INTER_ClEAN,
                    CodeKeyAndEvent.bb_ACTION_INTER_CLEAN,
                    binding.adCom
                ) {
//                    navHostController?.navigate(Route.End + "/" + event.size) {
//                        popUpTo(Route.Home) { inclusive = false }
//                        launchSingleTop = true
//                    binding.scanView.root.visibility = View.GONE
                    nextStep()
                }
            }
        }

    }

    fun nextStep(){
        binding.lyAd.root.visibility = View.GONE

        CamCaptureActivity.Companion.start(this)
        finish()
    }
}