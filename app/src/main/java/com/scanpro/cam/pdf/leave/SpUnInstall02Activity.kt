package com.scanpro.cam.pdf.leave

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.scanpro.cam.pdf.CamShellActivity
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpAdPosition
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.ads.manager.SpNativeAdManager
import com.scanpro.cam.pdf.ads.manager.SpNativeAdStyle
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivitySpUninstall02Binding

/**
 * 卸载挽留第 2 步。照抄 ApolloPDF `ui/delete/UnInstall02Activity.java`。
 *
 * 出处：`data/apks/apollo/sources/sources/com/zj/apollopdf/ui/delete/UnInstall02Activity.java`
 * 版式：`data/apks/apollo/apktool_out/res/layout/activity_uninstall_02.xml`
 *
 * | 本页控件 | Apollo 的 id | 动作 |
 * |---|---|---|
 * | `btn_back` / `btn_stay` | `backImage` / `stay` | 回首页 |
 * | `content_layout_0x` + `check_image_0x` | `contentLayout0x` / `checkImage0x` | 切换勾选（`ic_selected` / `ic_unselected`） |
 * | `btn_uninstall` | `uninstall` | 弹底部确认框 |
 * | `ad_layout` | `adLayout` | 原生（`ac_uninstall2_nat`），**进页先 GONE，广告就绪才显示** |
 *
 * 确认框（版式 `dialog_sp_uninstall_confirm.xml`）：
 * - 点「卸载」→ 关框 → 插屏 `ac_uninstall2_int` → 关掉后跳系统应用详情页（`ACTION_APPLICATION_DETAILS_SETTINGS`）
 * - 点「继续使用」→ 关框 → 回首页
 *
 * 三个勾选项纯记录用户意图，Apollo 与本页都不读取、不上报，勾不勾都不影响后续流程。
 */
class SpUnInstall02Activity : SpActivity<ActivitySpUninstall02Binding>() {

    private var nativeAdManager: SpNativeAdManager? = null

    override fun inflateBinding(inflater: LayoutInflater): ActivitySpUninstall02Binding {
        return ActivitySpUninstall02Binding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { toHome() }
        binding.btnStay.setOnClickListener { toHome() }
        binding.btnUninstall.setOnClickListener { showUninstallConfirm() }

        bindReasonRows()

        SpAdsHub.getOrNull()?.fullScreen(this)?.checkAndLoadAd(
            position = SpAdPosition.AC_UNINSTALL2_INT,
            scene = SpAdPosition.AC_UNINSTALL2_INT,
        )
        loadNativeAd()
    }

    override fun onDestroy() {
        nativeAdManager?.release()
        nativeAdManager = null
        super.onDestroy()
    }

    /**
     * Apollo 给 `contentLayout0x` 和 `checkImage0x` 各挂了一次同一个监听，
     * 所以点整行和点方框都是切换。这里按 (行, 方框) 配对一次装完。
     */
    private fun bindReasonRows() {
        listOf(
            binding.contentLayout01 to binding.checkImage01,
            binding.contentLayout02 to binding.checkImage02,
            binding.contentLayout03 to binding.checkImage03,
        ).forEach { (row, check) ->
            row.setOnClickListener { toggleReason(check) }
            check.setOnClickListener { toggleReason(check) }
        }
    }

    /** Apollo `onClick`：翻 `isSelected`，再按新状态换 `choose` / `unchoose` 两张图。 */
    private fun toggleReason(check: ImageView) {
        check.isSelected = !check.isSelected
        check.setImageResource(
            if (check.isSelected) R.drawable.ic_selected else R.drawable.ic_unselected,
        )
    }

    /** Apollo `loadNativeAd()`：容器先 `GONE`，展示时由 manager 自己置为 `VISIBLE`。 */
    private fun loadNativeAd() {
        if (nativeAdManager != null) return
        binding.adLayout.visibility = View.GONE
        val hub = SpAdsHub.getOrNull() ?: return
        nativeAdManager = hub.native(this, binding.adLayout).also {
            it.checkLoadAndShowAd(SpAdPosition.AC_UNINSTALL2_NAT, SpNativeAdStyle.small)
        }
    }

    /** Apollo `showUninstallDialog()`：不可取消的底部弹窗，窗口参数照抄。 */
    private fun showUninstallConfirm() {
        if (isFinishing || isDestroyed) return
        runCatching {
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val view = layoutInflater.inflate(R.layout.dialog_sp_uninstall_confirm, null)
            dialog.setContentView(view)
            dialog.setCancelable(false)

            view.findViewById<TextView>(R.id.btn_confirm_uninstall).setOnClickListener {
                dialog.dismiss()
                showInterstitialThenToSetting()
            }
            view.findViewById<TextView>(R.id.btn_keep_using).setOnClickListener {
                dialog.dismiss()
                toHome()
            }

            dialog.show()
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.7f)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.BOTTOM)
            }
        }.onFailure { Log.w(TAG, "showUninstallDialog failed", it) }
    }

    /** Apollo：关框后 `showInterstitialAdNoWait(ac_uninstall2_int) { toSettingPage() }`。 */
    private fun showInterstitialThenToSetting() {
        val manager = SpAdsHub.getOrNull()?.fullScreen(this)
        if (manager == null) {
            toSettingPage()
            return
        }
        manager.showInterstitialAdNoWait(SpAdPosition.AC_UNINSTALL2_INT) { toSettingPage() }
    }

    /** Apollo `toSettingPage()`：跳系统「应用详情」，用户在那里点卸载。 */
    private fun toSettingPage() {
        if (isFinishing || isDestroyed) return
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                ),
            )
        }.onFailure { Log.w(TAG, "toSettingPage failed", it) }
        finish()
    }

    /** Apollo `toHome()`：与第 1 步同一个跳法。 */
    private fun toHome() {
        if (isFinishing || isDestroyed) return
        startActivity(
            Intent(this, CamShellActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        finish()
    }

    companion object {
        private const val TAG = "SpUnInstall02"

        fun start(context: Context) {
            context.startActivity(Intent(context, SpUnInstall02Activity::class.java))
        }
    }
}
