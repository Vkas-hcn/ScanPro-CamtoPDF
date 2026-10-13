package com.scanpro.cam.pdf.guide

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.scanpro.cam.pdf.CamShellActivity
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpAdPosition
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.ads.manager.SpNativeAdManager
import com.scanpro.cam.pdf.ads.manager.SpNativeAdStyle
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivitySpGuideInfoBinding
import kotlin.math.abs

/**
 * 首次引导页。照抄 Apollo `ui/guide/GuideInfoActivity.java`。
 *
 * 大原生 `ac_guide_nat`（进页先隐藏）；进页预加载 `ac_guide_int`；
 * Skip 走 `showInterstitialAdNoWait`，关掉后写 [SpFirstLaunch] 并进首页。
 * 返回键空处理。
 */
class SpGuideInfoActivity : SpActivity<ActivitySpGuideInfoBinding>() {

    override val applySystemBarsPadding: Boolean = false

    private val pages = listOf(
        R.string.guide_page_1,
        R.string.guide_page_2,
        R.string.guide_page_3,
    )

    private var nativeAdManager: SpNativeAdManager? = null
    private var jumped = false
    private var downX = 0f

    override fun inflateBinding(inflater: LayoutInflater): ActivitySpGuideInfoBinding {
        return ActivitySpGuideInfoBinding.inflate(inflater)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initView(savedInstanceState: Bundle?) {
        applyInsets()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        SpAdsHub.getOrNull()?.fullScreen(this)?.checkAndLoadAd(
            position = SpAdPosition.AC_GUIDE_INT,
            scene = SpAdPosition.AC_GUIDE_INT,
        )
        binding.tvSkip.setOnClickListener { showGuideIntThenJump() }
        binding.guideFlipper.setOnTouchListener { _, event -> handleSwipe(event) }
        renderPage(0)
        loadNativeAd()
    }

    override fun onDestroy() {
        nativeAdManager?.release()
        nativeAdManager = null
        super.onDestroy()
    }

    private fun loadNativeAd() {
        if (nativeAdManager != null) return
        binding.adLayout.visibility = View.GONE
        val hub = SpAdsHub.getOrNull() ?: return
        nativeAdManager = hub.native(this, binding.adLayout).also {
            it.checkLoadAndShowAd(SpAdPosition.AC_GUIDE_NAT, SpNativeAdStyle.big)
        }
    }

    private fun handleSwipe(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> downX = event.x
            MotionEvent.ACTION_UP -> {
                val delta = event.x - downX
                if (abs(delta) > SWIPE_THRESHOLD) {
                    if (delta < 0) showPage(binding.guideFlipper.displayedChild + 1)
                    else showPage(binding.guideFlipper.displayedChild - 1)
                }
            }
        }
        return true
    }

    private fun showPage(index: Int) {
        val next = index.coerceIn(0, pages.lastIndex)
        if (next == binding.guideFlipper.displayedChild) {
            renderPage(next)
            return
        }
        binding.guideFlipper.displayedChild = next
        renderPage(next)
    }

    private fun renderPage(index: Int) {
        val message = pages.getOrNull(index) ?: return
        binding.tvDes.setText(message)
        val dots = listOf(binding.oneView, binding.twoView, binding.threeView)
        dots.forEachIndexed { i, view ->
            view.setBackgroundResource(
                if (i == index) R.drawable.shape_guide_dot_on else R.drawable.shape_guide_dot_off,
            )
        }
    }

    private fun showGuideIntThenJump() {
        val manager = SpAdsHub.getOrNull()?.fullScreen(this)
        if (manager == null) {
            jumpPage()
            return
        }
        manager.showInterstitialAdNoWait(SpAdPosition.AC_GUIDE_INT) { jumpPage() }
    }

    private fun jumpPage() {
        if (jumped || isFinishing || isDestroyed) return
        jumped = true
        SpFirstLaunch.markOpened(this)
        startActivity(
            Intent(this, CamShellActivity::class.java).apply {
                copyLaunchExtras(intent, this)
            },
        )
        finish()
    }

    private fun applyInsets() {
        val skipTop = binding.tvSkip.paddingTop
        val adBottom = binding.adLayout.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.guideInfoRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.tvSkip.updatePadding(top = skipTop + bars.top)
            binding.adLayout.updatePadding(bottom = adBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.guideInfoRoot)
    }

    companion object {
        private const val SWIPE_THRESHOLD = 64f
    }
}
