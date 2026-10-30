package com.scanpro.cam.pdf.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpHotStartGate
import com.scanpro.cam.pdf.ads.SpPageAdTriggers
import com.scanpro.cam.pdf.ads.manager.SpNativeAdManager
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivityCamSettingsBinding
import com.scanpro.cam.pdf.databinding.ItemCamSettingsRowBinding
import com.scanpro.cam.pdf.util.SpLocale

class CamSettingsActivity : SpActivity<ActivityCamSettingsBinding>() {

    override val applySystemBarsPadding: Boolean = false
    override val lightStatusBarIcons: Boolean = true

    private var settingNativeAdManager: SpNativeAdManager? = null

    private val languageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Refresh copy from the new locale without recreating this Activity.
                refreshTexts()
            }
        }

    override fun inflateBinding(inflater: LayoutInflater): ActivityCamSettingsBinding {
        return ActivityCamSettingsBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        applyStatusBarInset()
        binding.btnBack.setOnClickListener { finish() }
        refreshTexts()

        binding.itemLanguage.root.setOnClickListener {
            languageLauncher.launch(CamLanguageActivity.createIntent(this))
            overridePendingTransition(0, 0)
        }
        binding.itemFavorite.root.setOnClickListener {
            CamFavoritesActivity.start(this)
        }
        binding.itemShare.root.setOnClickListener { shareApp() }
        binding.itemPrivacy.root.setOnClickListener { openUrl(R.string.settings_privacy_url) }
        binding.itemTerms.root.setOnClickListener { openUrl(R.string.settings_terms_url) }
        loadSettingNativeAd()
    }

    override fun onDestroy() {
        settingNativeAdManager?.release()
        settingNativeAdManager = null
        super.onDestroy()
    }

    private fun loadSettingNativeAd() {
        if (settingNativeAdManager != null) return
        settingNativeAdManager = SpPageAdTriggers.bindSettingNative(this, binding.adLayout)
    }

    private fun refreshTexts() {
        val ctx = SpLocale.wrap(this)
        binding.tvTitle.text = ctx.getString(R.string.settings)
        bindRow(binding.itemLanguage, R.drawable.ic_setting_language, ctx.getString(R.string.settings_language))
        bindRow(binding.itemFavorite, R.drawable.ic_setting_favorite, ctx.getString(R.string.settings_favorite))
        bindRow(binding.itemShare, R.drawable.ic_setting_share, ctx.getString(R.string.settings_share))
        bindRow(binding.itemPrivacy, R.drawable.ic_privacy_policy, ctx.getString(R.string.settings_privacy))
        bindRow(binding.itemTerms, R.drawable.ic_privacy_policy, ctx.getString(R.string.settings_terms))
    }

    private fun bindRow(
        row: ItemCamSettingsRowBinding,
        iconRes: Int,
        label: String,
    ) {
        row.ivIcon.setImageResource(iconRes)
        row.tvLabel.text = label
    }

    private fun applyStatusBarInset() {
        val baseMargin = resources.getDimensionPixelSize(R.dimen.home_page_padding) / 2
        val scrollBottom = binding.scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = binding.btnBack.layoutParams as ConstraintLayout.LayoutParams
            params.topMargin = baseMargin + bars.top
            binding.btnBack.layoutParams = params
            binding.scroll.setPadding(
                binding.scroll.paddingLeft,
                binding.scroll.paddingTop,
                binding.scroll.paddingRight,
                scrollBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.settingsRoot)
    }

    private fun shareApp() {
        val ctx = SpLocale.wrap(this)
        val text = ctx.getString(R.string.settings_share_text, packageName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        // 分享面板会把自己送到后台；用户回来时不该弹开屏（阈值 0 秒时必须显式排除）
        SpHotStartGate.suppressNext()
        startActivity(Intent.createChooser(intent, ctx.getString(R.string.settings_share)))
    }

    private fun openUrl(urlRes: Int) {
        val url = SpLocale.wrap(this).getString(urlRes)
        // 跳浏览器（隐私政策等）再回来不该弹开屏
        SpHotStartGate.suppressNext()
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CamSettingsActivity::class.java))
        }
    }
}
