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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.buncead.common.util.NetUtil
import com.mymy.mymy.ads.count.AdCountNati
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivityCamSettingsBinding
import com.scanpro.cam.pdf.databinding.ItemCamSettingsRowBinding
import com.scanpro.cam.pdf.util.SpLocale
import com.zppko.pdfplus.nor.ads.ImpressionUtils
import com.zppko.pdfplus.nor.ads.helper.CodeKeyAndEvent
import com.zppko.pdfplus.nor.ads.mob.NatAdsLoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CamSettingsActivity : SpActivity<ActivityCamSettingsBinding>() {

    override val applySystemBarsPadding: Boolean = false
    override val lightStatusBarIcons: Boolean = true


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
    }

    override fun onResume() {
        super.onResume()
        nativeNetCheck()
    }

    override fun onDestroy() {
        onCleared()
        super.onDestroy()
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

        startActivity(Intent.createChooser(intent, ctx.getString(R.string.settings_share)))
    }

    private fun openUrl(urlRes: Int) {
        val url = SpLocale.wrap(this).getString(urlRes)
        // 跳浏览器（隐私政策等）再回来不该弹开屏
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun nativeNetCheck(){
        // 网络是否允许加载和显示广告
        val allow = NetUtil.isUseNet_noVpn()
        val canShow = AdCountNati.natiCanShow()
        if (allow && canShow){
            prepareLoadNative()
        }else{

        }
    }
    fun prepareLoadNative(){
        lifecycleScope.launch(Dispatchers.IO) {
            delay(100)

            if (lifecycle.currentState == Lifecycle.State.RESUMED){
                loadNative(CodeKeyAndEvent.zx_PLACE_NATIVE_MAIN, CodeKeyAndEvent.bb_ACTION_NAV_MAIN)
            }
        }
    }

    var mNatAdsLoad: NatAdsLoad?=null
    private var job:Job?=null

    fun loadNative(place:String,point:String){

        job?.cancel()
        ImpressionUtils.load(place)
        job = lifecycleScope.launch(Dispatchers.IO) {
            while (true){
                if (ImpressionUtils.loadSuccess(place)){
                    mNatAdsLoad?.zAdsDestory()
                    break
                }
                delay(500)
            }
            mNatAdsLoad=ImpressionUtils.fireAnative(place)

            pShow(point)
        }
    }

    fun onCleared() {
        job?.cancel()
        mNatAdsLoad?.zAdsDestory()
    }

    fun pShow(point:String){
        val natAdsLoad = mNatAdsLoad
        if (natAdsLoad ==null){
            return
        }

        natAdsLoad.getAdView(point)?.let {
            lifecycleScope.launch(Dispatchers.Main) {
                if (lifecycle.currentState == Lifecycle.State.RESUMED){
                    it.showAd(binding.flNati, R.layout.tp_native_ad_list_item, "");
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CamSettingsActivity::class.java))
        }
    }
}
