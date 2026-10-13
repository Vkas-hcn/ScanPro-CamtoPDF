package com.scanpro.cam.pdf.recent

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.base.SpFragment
import com.scanpro.cam.pdf.made.CamRenameResult
import com.scanpro.cam.pdf.databinding.FragmentCamRecentBinding
import com.scanpro.cam.pdf.databinding.PopupCamFileMenuBinding
import com.scanpro.cam.pdf.home.CamListState
import com.scanpro.cam.pdf.home.CamFileListAdapter
import com.scanpro.cam.pdf.home.CamFileItem
import com.scanpro.cam.pdf.settings.CamSettingsActivity
import com.scanpro.cam.pdf.util.CamFileAlerts
import com.scanpro.cam.pdf.util.CamPopupAlign
import com.scanpro.cam.pdf.viewer.CamDocRouter
import kotlinx.coroutines.launch

class CamRecentFragment : SpFragment<FragmentCamRecentBinding>() {

    private val viewModel: CamRecentViewModel by viewModels()

    private val fileAdapter = CamFileListAdapter(
        onItemClick = { item -> CamDocRouter.open(requireActivity(), item) },
        onMoreClick = { item, anchor -> showFileMenu(anchor, item) },
    )

    private var popupWindow: PopupWindow? = null

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentCamRecentBinding {
        return FragmentCamRecentBinding.inflate(inflater, container, false)
    }

    override fun initView(savedInstanceState: Bundle?) {
        applyStatusBarInset()
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = fileAdapter
        binding.btnSettings.setOnClickListener {
            CamSettingsActivity.start(requireContext())
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null) viewModel.refresh()
    }

    override fun onDestroyView() {
        popupWindow?.dismiss()
        popupWindow = null
        super.onDestroyView()
    }

    private fun applyStatusBarInset() {
        val baseMargin = resources.getDimensionPixelSize(R.dimen.home_page_padding) / 2
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnSettings) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val params = view.layoutParams as ConstraintLayout.LayoutParams
            params.topMargin = baseMargin + bars.top
            view.layoutParams = params
            insets
        }
        ViewCompat.requestApplyInsets(binding.btnSettings)
    }

    private fun renderContent(state: CamListState) {
        binding.rvFiles.isVisible = state is CamListState.ListFiles
        binding.emptyState.isVisible = state is CamListState.Empty
        if (state is CamListState.ListFiles) {
            fileAdapter.submitList(state.files)
        }
    }

    private fun showFileMenu(anchor: View, item: CamFileItem) {
        popupWindow?.dismiss()
        val menuBinding = PopupCamFileMenuBinding.inflate(layoutInflater)
        val favorite = viewModel.isFavorite(item)
        menuBinding.tvFavorite.setText(
            if (favorite) R.string.file_action_unfavorite else R.string.file_action_favorite,
        )
        menuBinding.ivFavorite.setImageResource(
            if (favorite) R.drawable.ic_like else R.drawable.ic_favorite_nor,
        )

        val popup = PopupWindow(
            menuBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        popupWindow = popup

        menuBinding.actionRename.setOnClickListener {
            popup.dismiss()
            showRenameDialog(item)
        }
        menuBinding.actionShare.setOnClickListener {
            popup.dismiss()
            if (!viewModel.share(item)) {
                toast(R.string.file_share_failed)
            }
        }
        menuBinding.actionDelete.setOnClickListener {
            popup.dismiss()
            confirmDelete(item)
        }
        menuBinding.actionFavorite.setOnClickListener {
            popup.dismiss()
            viewModel.toggleFavorite(item)
        }

        CamPopupAlign.showBelowAlignEnd(popup, anchor)
    }

    private fun showRenameDialog(item: CamFileItem) {
        CamFileAlerts.showRename(requireContext(), item.name) { name ->
            when (viewModel.rename(item, name)) {
                CamRenameResult.Success -> Unit
                CamRenameResult.Empty -> toast(R.string.file_rename_empty)
                CamRenameResult.Exists -> toast(R.string.file_rename_exists)
                CamRenameResult.Failed -> toast(R.string.file_rename_failed)
            }
        }
    }

    private fun confirmDelete(item: CamFileItem) {
        CamFileAlerts.showDelete(requireContext()) {
            viewModel.delete(item)
        }
    }

    private fun toast(res: Int) {
        Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()
    }
}
