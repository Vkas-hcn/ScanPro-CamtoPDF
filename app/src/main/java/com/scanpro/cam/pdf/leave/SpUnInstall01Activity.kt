package com.scanpro.cam.pdf.leave

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import com.scanpro.cam.pdf.CamShellActivity
import com.scanpro.cam.pdf.ads.SpAdPosition
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.ads.manager.SpNativeAdManager
import com.scanpro.cam.pdf.ads.manager.SpNativeAdStyle
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivitySpUninstall01Binding

/**
 * 卸载挽留第 1 步。照抄 ApolloPDF `ui/delete/UnInstall01Activity.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ui/delete/UnInstall01Activity.java`
 * 版式：`data/apks/apollo/apktool_out/res/layout/activity_uninstall_01.xml`
 *
 * | 本页控件 | Apollo 的 id | 动作 |
 * |---|---|---|
 * | `btn_back` | `backImage` | 回首页 |
 * | `tv_action_01` | `textAction01` | 回首页 |
 * | `tv_action_02` | `textAction02` | 回首页 |
 * | `btn_uninstall` | `uninstall` | 先插屏（`ac_uninstall1_int`），关闭后进第 2 步 |
 * | `btn_stay` | `stay` | 回首页 |
 * | `ad_layout` | `adLayout` | 原生（`ac_uninstall1_nat`），进页立即加载 |
 *
 * 两个问题卡（`card_issue_01` / `card_issue_02`）**整卡不可点**：Apollo 只给里面的
 * `textAction0x` 挂了点击，卡片本身没有监听。照原样。
 *
 * 进页就 `checkAndLoadAd(ac_uninstall1_int)` 预加载插屏，点卸载时走
 * [com.scanpro.cam.pdf.ads.manager.SpFullScreenAdManager.showInterstitialAdNoWait]：**不等**，
 * 有缓存就展示、没有就直接进第 2 步。
 */
class SpUnInstall01Activity : SpActivity<ActivitySpUninstall01Binding>() {

    private var nativeAdManager: SpNativeAdManager? = null

    override fun inflateBinding(inflater: LayoutInflater): ActivitySpUninstall01Binding {
        return ActivitySpUninstall01Binding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        // Apollo `onCreate`：正文两个按钮先回首页
        binding.btnBack.setOnClickListener { toHome() }
        binding.tvAction01.setOnClickListener { toHome() }
        binding.tvAction02.setOnClickListener { toHome() }
        binding.btnStay.setOnClickListener { toHome() }
        binding.btnUninstall.setOnClickListener { showInterstitialThenLeave() }

        SpAdsHub.getOrNull()?.fullScreen(this)?.checkAndLoadAd(
            position = SpAdPosition.AC_UNINSTALL1_INT,
            scene = SpAdPosition.AC_UNINSTALL1_INT,
        )
        loadNativeAd()
    }

    override fun onDestroy() {
        nativeAdManager?.release()
        nativeAdManager = null
        super.onDestroy()
    }

    /** Apollo `loadNativeAd()`：一个 manager 只建一次，位置 `ac_uninstall1_nat`，版式 small。 */
    private fun loadNativeAd() {
        if (nativeAdManager != null) return
        val hub = SpAdsHub.getOrNull() ?: return
        nativeAdManager = hub.native(this, binding.adLayout).also {
            it.checkLoadAndShowAd(SpAdPosition.AC_UNINSTALL1_NAT, SpNativeAdStyle.small)
        }
    }

    /** Apollo：`showInterstitialAdNoWait(ac_uninstall1_int) { toUninstall() }`。 */
    private fun showInterstitialThenLeave() {
        val manager = SpAdsHub.getOrNull()?.fullScreen(this)
        if (manager == null) {
            toUninstall()
            return
        }
        manager.showInterstitialAdNoWait(SpAdPosition.AC_UNINSTALL1_INT) { toUninstall() }
    }

    /** Apollo `toUninstall()`：进第 2 步，自己结束。 */
    private fun toUninstall() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this, SpUnInstall02Activity::class.java))
        finish()
    }

    /** Apollo `toHome()`：`HomeActivity` + `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP`。 */
    private fun toHome() {
        if (isFinishing || isDestroyed) return
        Log.i(TAG, "back to home")
        startActivity(
            Intent(this, CamShellActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        finish()
    }

    companion object {
        private const val TAG = "SpUnInstall01"

        fun start(context: Context) {
            context.startActivity(Intent(context, SpUnInstall01Activity::class.java))
        }
    }
}
