package com.scanpro.cam.pdf.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivityCamLanguageBinding
import com.scanpro.cam.pdf.databinding.ItemCamLanguageBinding
import com.scanpro.cam.pdf.util.SpLocale

class CamLanguageActivity : SpActivity<ActivityCamLanguageBinding>() {

    override val applySystemBarsPadding: Boolean = false
    override val lightStatusBarIcons: Boolean = true

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

    private lateinit var adapter: LanguageAdapter
    private var changed = false

    override fun inflateBinding(inflater: LayoutInflater): ActivityCamLanguageBinding {
        return ActivityCamLanguageBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        applyInsets()
        binding.btnBack.setOnClickListener { finishWithResult() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithResult()
                }
            },
        )

        adapter = LanguageAdapter(languages, SpLocale.tag(this)) { option ->
            val next = SpLocale.normalize(option.tag)
            if (next == SpLocale.normalize(SpLocale.tag(this))) {
                adapter.select(option.tag)
                return@LanguageAdapter
            }
            // Persist only — do NOT call setApplicationLocales (that recreates the whole stack).
            SpLocale.set(this, option.tag)
            adapter.select(option.tag)
            binding.tvTitle.text = SpLocale.wrap(this).getString(R.string.settings_language)
            changed = true
        }
        binding.rvLanguages.layoutManager = LinearLayoutManager(this)
        binding.rvLanguages.adapter = adapter
    }

    private fun finishWithResult() {
        setResult(if (changed) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun applyInsets() {
        val baseMargin = resources.getDimensionPixelSize(R.dimen.home_page_padding) / 2
        val listBottom = binding.rvLanguages.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.languageRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = binding.btnBack.layoutParams as ConstraintLayout.LayoutParams
            params.topMargin = baseMargin + bars.top
            binding.btnBack.layoutParams = params
            binding.rvLanguages.setPadding(
                binding.rvLanguages.paddingLeft,
                binding.rvLanguages.paddingTop,
                binding.rvLanguages.paddingRight,
                listBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.languageRoot)
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CamLanguageActivity::class.java))
        }

        fun createIntent(context: Context): Intent {
            return Intent(context, CamLanguageActivity::class.java)
        }
    }
}

data class CamLanguageOption(
    val tag: String,
    val labelRes: Int,
)

private class LanguageAdapter(
    private val items: List<CamLanguageOption>,
    selectedTag: String,
    private val onSelect: (CamLanguageOption) -> Unit,
) : RecyclerView.Adapter<LanguageAdapter.VH>() {

    private var selected = SpLocale.normalize(selectedTag)

    fun select(tag: String) {
        val old = selected
        selected = SpLocale.normalize(tag)
        val oldIndex = items.indexOfFirst { SpLocale.normalize(it.tag) == old }
        val newIndex = items.indexOfFirst { SpLocale.normalize(it.tag) == selected }
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

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
            binding.root.setOnClickListener { onSelect(item) }
        }
    }
}
