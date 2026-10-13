package com.scanpro.cam.pdf.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivityCamFavoritesBinding
import com.scanpro.cam.pdf.home.CamListState
import com.scanpro.cam.pdf.home.CamFileListAdapter
import com.scanpro.cam.pdf.home.CamFileItem
import com.scanpro.cam.pdf.viewer.CamDocRouter
import kotlinx.coroutines.launch

class CamFavoritesActivity : SpActivity<ActivityCamFavoritesBinding>() {

    override val applySystemBarsPadding: Boolean = false
    override val lightStatusBarIcons: Boolean = true

    private val viewModel: CamFavoritesViewModel by viewModels()

    private val fileAdapter = CamFileListAdapter(
        onItemClick = { item -> CamDocRouter.open(this, item) },
        onMoreClick = { item, _ -> confirmRemove(item) },
    )

    override fun inflateBinding(inflater: LayoutInflater): ActivityCamFavoritesBinding {
        return ActivityCamFavoritesBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        applyStatusBarInset()
        binding.btnBack.setOnClickListener { finish() }
        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = fileAdapter
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderContent(state.contentState)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun applyStatusBarInset() {
        val baseMargin = resources.getDimensionPixelSize(R.dimen.home_page_padding) / 2
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnBack) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val params = view.layoutParams as ConstraintLayout.LayoutParams
            params.topMargin = baseMargin + bars.top
            view.layoutParams = params
            insets
        }
        ViewCompat.requestApplyInsets(binding.btnBack)
    }

    private fun renderContent(state: CamListState) {
        binding.rvFiles.isVisible = state is CamListState.ListFiles
        binding.emptyState.isVisible = state is CamListState.Empty
        if (state is CamListState.ListFiles) {
            fileAdapter.submitList(state.files)
        }
    }

    private fun confirmRemove(item: CamFileItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.file_action_unfavorite)
            .setMessage(item.name)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                viewModel.removeFavorite(item)
            }
            .show()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CamFavoritesActivity::class.java))
        }
    }
}
