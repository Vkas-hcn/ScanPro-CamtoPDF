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
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scanpro.cam.pdf.R

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


//        if (isStartTimer(SpAdPosition.AC_LANGUAGE_NAT)) {
            startNextCountdown()
//        }
    }

    override fun onStop() {
        super.onStop()
        cancelTimer()
    }

    override fun onDestroy() {
        cancelTimer()
        super.onDestroy()
    }


    private fun onNextClicked() {
        cancelTimer()
        showLanguageIntThenJump()
    }

    private fun showLanguageIntThenJump() {
        jumpPage()
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

//    /**
//     * Apollo `isStartTimer(ac_language_nat)`：SDK 已初始化且总闸放行才开 5 秒倒计时。
//     */
//    private fun isStartTimer(position: String): Boolean {
//        if (SpAdMobInit.getOrNull()?.isMobileAdsInitialize() != true) return false
//        val blocking = SpBlockingLogic.get(this)
//        return !blocking.isShowPartialAdsOnly(position) &&
//            !blocking.isVPNBlock() &&
//            blocking.canShowAdByShowCountLimit() &&
//            blocking.canShowAllAd()
//    }

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

    /**
     * 状态栏 inset 加到标题的上内边距；导航栏 inset 改成 Next 按钮的**下外边距**。
     *
     * 不能用 `updatePadding(bottom = …)`：`tv_next` 是固定 52dp 高，三键导航底部 inset 约 48dp，
     * 内容区会被压到 4dp，16sp 文字直接被裁掉（看起来就是「按钮没有文字」），而且 padding
     * 也不会把按钮抬离导航栏。调 margin 才能既上移按钮、又不动文字布局。
     */
    private fun applyInsets() {
        val titleTop = binding.tvTitle.paddingTop
        val nextBottomMargin =
            (binding.tvNext.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.guideLanguageRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.tvTitle.updatePadding(top = titleTop + bars.top)
            binding.tvNext.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = nextBottomMargin + bars.bottom
            }
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
