package com.scanpro.cam.pdf.guide

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpAdMobInit
import com.scanpro.cam.pdf.ads.SpAdPosition
import com.scanpro.cam.pdf.ads.SpAdsHub
import com.scanpro.cam.pdf.ads.SpBlockingLogic
import com.scanpro.cam.pdf.ads.manager.SpNativeAdManager
import com.scanpro.cam.pdf.ads.manager.SpNativeAdStyle
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivitySpGuideLanguageBinding
import com.scanpro.cam.pdf.databinding.ItemCamLanguageBinding
import com.scanpro.cam.pdf.notice.SpNotifyExtras
import com.scanpro.cam.pdf.settings.CamLanguageOption
import com.scanpro.cam.pdf.util.SpLocale

/**
 * 首次语言页。照抄 Apollo `ui/guide/GuideLanguageActivity.java`。
 *
 * 底部原生 `ac_language_nat`（进页先隐藏）；进页预加载 `ac_language_int`；
 * 点 Next 或倒计时结束走 `showInterstitialAdNoWait`，无缓存不等待，进引导页。
 * 返回键空处理，不结束首次引导。
 */
class SpGuideLanguageActivity : SpActivity<ActivitySpGuideLanguageBinding>() {

    override val applySystemBarsPadding: Boolean = false

    private val languages = listOf(
        CamLanguageOption("hi", R.string.language_hindi),
        CamLanguageOption("en", R.string.language_english),
        CamLanguageOption("fr", R.string.language_french),
        CamLanguageOption("de", R.string.language_german),
        CamLanguageOption("pt", R.string.language_portuguese),
        CamLanguageOption("es", R.string.language_spanish),
        CamLanguageOption("id", R.string.language_indonesia),
        CamLanguageOption("ru", R.string.language_russian),
    )

    private var nativeAdManager: SpNativeAdManager? = null
    private var timer: CountDownTimer? = null
    private var jumped = false

    override fun inflateBinding(inflater: LayoutInflater): ActivitySpGuideLanguageBinding {
        return ActivitySpGuideLanguageBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        applyInsets()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        val adapter = GuideLanguageAdapter(languages, SpLocale.tag(this)) { option ->
            SpLocale.set(this, option.tag)
        }
        binding.rvLanguages.layoutManager = LinearLayoutManager(this)
        binding.rvLanguages.adapter = adapter
        binding.tvNext.setOnClickListener { onNextClicked() }

        SpAdsHub.getOrNull()?.fullScreen(this)?.checkAndLoadAd(
            position = SpAdPosition.AC_LANGUAGE_INT,
            scene = SpAdPosition.AC_LANGUAGE_INT,
        )
        loadNativeAd()
        if (isStartTimer(SpAdPosition.AC_LANGUAGE_NAT)) {
            startNextCountdown()
        }
    }

    override fun onStop() {
        super.onStop()
        cancelTimer()
    }

    override fun onDestroy() {
        cancelTimer()
        nativeAdManager?.release()
        nativeAdManager = null
        super.onDestroy()
    }

    private fun loadNativeAd() {
        if (nativeAdManager != null) return
        binding.adLayout.visibility = View.GONE
        val hub = SpAdsHub.getOrNull() ?: return
        nativeAdManager = hub.native(this, binding.adLayout).also {
            it.checkLoadAndShowAd(SpAdPosition.AC_LANGUAGE_NAT, SpNativeAdStyle.small)
        }
    }

    private fun onNextClicked() {
        cancelTimer()
        showLanguageIntThenJump()
    }

    private fun showLanguageIntThenJump() {
        val manager = SpAdsHub.getOrNull()?.fullScreen(this)
        if (manager == null) {
            jumpPage()
            return
        }
        manager.showInterstitialAdNoWait(SpAdPosition.AC_LANGUAGE_INT) { jumpPage() }
    }

    private fun jumpPage() {
        if (jumped || isFinishing || isDestroyed) return
        jumped = true
        startActivity(
            Intent(this, SpGuideInfoActivity::class.java).apply {
                copyLaunchExtras(intent, this)
            },
        )
        finish()
    }

    /**
     * Apollo `isStartTimer(ac_language_nat)`：SDK 已初始化且总闸放行才开 5 秒倒计时。
     */
    private fun isStartTimer(position: String): Boolean {
        if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) return false
        val blocking = SpBlockingLogic.get(this)
        return !blocking.isShowPartialAdsOnly(position) &&
            !blocking.isVPNBlock() &&
            blocking.canShowAdByShowCountLimit() &&
            blocking.canShowAllAd()
    }

    private fun startNextCountdown() {
        cancelTimer()
        timer = object : CountDownTimer(COUNTDOWN_SECONDS * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000L).toInt() + 1
                binding.tvNext.text = getString(R.string.guide_next) + "(" + seconds + "S)"
            }

            override fun onFinish() {
                binding.tvNext.setText(R.string.guide_next)
                showLanguageIntThenJump()
            }
        }.start()
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    private fun applyInsets() {
        val titleTop = binding.tvTitle.paddingTop
        val nextBottom = binding.tvNext.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.guideLanguageRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.tvTitle.updatePadding(top = titleTop + bars.top)
            binding.tvNext.updatePadding(bottom = nextBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.guideLanguageRoot)
    }

    companion object {
        private const val COUNTDOWN_SECONDS = 5

        fun start(context: Context, extras: Intent? = null) {
            context.startActivity(
                Intent(context, SpGuideLanguageActivity::class.java).apply {
                    copyLaunchExtras(extras, this)
                },
            )
        }
    }
}

internal fun copyLaunchExtras(from: Intent?, to: Intent) {
    from?.getStringExtra(SpNotifyExtras.JUMP_TYPE)?.let {
        to.putExtra(SpNotifyExtras.JUMP_TYPE, it)
    }
}

private class GuideLanguageAdapter(
    private val items: List<CamLanguageOption>,
    selectedTag: String,
    private val onSelect: (CamLanguageOption) -> Unit,
) : RecyclerView.Adapter<GuideLanguageAdapter.VH>() {

    private var selected = SpLocale.normalize(selectedTag)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCamLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(
        private val binding: ItemCamLanguageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CamLanguageOption) {
            binding.tvLanguage.setText(item.labelRes)
            val isSelected = SpLocale.normalize(item.tag) == selected
            binding.ivCheck.setImageResource(
                if (isSelected) R.drawable.ic_language_selected else R.drawable.ic_language_unselected,
            )
            binding.root.setOnClickListener {
                val old = selected
                selected = SpLocale.normalize(item.tag)
                val oldIndex = items.indexOfFirst { SpLocale.normalize(it.tag) == old }
                val newIndex = items.indexOfFirst { SpLocale.normalize(it.tag) == selected }
                if (oldIndex >= 0) notifyItemChanged(oldIndex)
                if (newIndex >= 0) notifyItemChanged(newIndex)
                onSelect(item)
            }
        }
    }
}
