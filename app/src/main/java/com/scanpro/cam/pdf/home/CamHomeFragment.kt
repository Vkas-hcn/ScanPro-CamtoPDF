package com.scanpro.cam.pdf.home

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.ads.SpAdPosition
import com.scanpro.cam.pdf.ads.SpPageAdTriggers
import com.scanpro.cam.pdf.base.SpFragment
import com.scanpro.cam.pdf.made.CamRenameResult
import com.scanpro.cam.pdf.databinding.FragmentCamHomeBinding
import com.scanpro.cam.pdf.databinding.PopupCamFileMenuBinding
import com.scanpro.cam.pdf.settings.CamSettingsActivity
import com.scanpro.cam.pdf.util.CamFileAlerts
import com.scanpro.cam.pdf.util.CamPopupAlign
import com.scanpro.cam.pdf.util.CamStoragePermit
import com.scanpro.cam.pdf.viewer.CamDocRouter
import kotlinx.coroutines.launch

class CamHomeFragment : SpFragment<FragmentCamHomeBinding>() {

    private val viewModel: CamHomeViewModel by viewModels()
    private val fileAdapter = CamFileListAdapter(
        onItemClick = { item -> openCamFile(item) },
        onMoreClick = { item, anchor -> showFileMenu(anchor, item) },
    )

    private var popupWindow: PopupWindow? = null

    private val manageStorageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshStorageAccess()
        }

    private val legacyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshStorageAccess()
        }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentCamHomeBinding {
        return FragmentCamHomeBinding.inflate(inflater, container, false)
    }

    override fun initView(savedInstanceState: Bundle?) {
        applyStatusBarInset()
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = fileAdapter

        binding.typeTxt.setOnClickListener { selectTypeAndMaybeAd(FileType.TXT) }
        binding.typePdf.setOnClickListener { selectTypeAndMaybeAd(FileType.PDF) }
        binding.typePpt.setOnClickListener { selectTypeAndMaybeAd(FileType.PPT) }
        binding.typeXls.setOnClickListener { selectTypeAndMaybeAd(FileType.XLS) }
        binding.typeDoc.setOnClickListener { selectTypeAndMaybeAd(FileType.DOC) }

        binding.btnGrantPermission.setOnClickListener { requestStorageAccess() }
        binding.btnSettings.setOnClickListener {
            CamSettingsActivity.start(requireContext())
        }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateQuery(s?.toString().orEmpty())
            }
        })

        refreshStorageAccess()
        // 默认 PDF 算第一次选中，之后用户点类型才弹 `ac_top_int`
        typeAdArmed = true
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderTypeSelection(state.selectedType)
                    renderContent(state.contentState)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStorageAccess()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Tabs stay resumed under hide/show, so onResume does not run again.
        if (!hidden && view != null) refreshStorageAccess()
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

    private fun refreshStorageAccess() {
        val granted = CamStoragePermit.hasFileAccess(requireContext())
        viewModel.onPermissionResult(granted)
    }

    /**
     * Apollo `HomeActivity.initTabs` 的 `isJudgeTimeAndLoadTabAd`：
     * 第一次选中只记账，之后的类型切换才弹 `ac_top_int`。
     */
    private var typeAdArmed = false

    private fun selectTypeAndMaybeAd(type: FileType) {
        viewModel.selectType(type)
        if (!typeAdArmed) {
            typeAdArmed = true
            return
        }
        activity?.let { SpPageAdTriggers.checkLoadAdByTab(it, SpAdPosition.AC_TOP_INT) }
    }

    private fun openCamFile(item: CamFileItem) {
        if (isAdded) {
            CamDocRouter.open(requireActivity(), item)
        }
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manageStorageLauncher.launch(
                CamStoragePermit.manageAllFilesIntent(requireContext()),
            )
        } else {
            legacyPermissionLauncher.launch(CamStoragePermit.legacyReadPermission())
        }
    }

    private fun renderTypeSelection(selected: FileType) {
        styleTypeChip(
            binding.typePdf,
            binding.ivTypePdf,
            binding.tvTypePdf,
            FileType.PDF,
            selected == FileType.PDF,
        )
        styleTypeChip(
            binding.typeTxt,
            binding.ivTypeTxt,
            binding.tvTypeTxt,
            FileType.TXT,
            selected == FileType.TXT,
        )
        styleTypeChip(
            binding.typePpt,
            binding.ivTypePpt,
            binding.tvTypePpt,
            FileType.PPT,
            selected == FileType.PPT,
        )
        styleTypeChip(
            binding.typeXls,
            binding.ivTypeXls,
            binding.tvTypeXls,
            FileType.XLS,
            selected == FileType.XLS,
        )
        styleTypeChip(
            binding.typeDoc,
            binding.ivTypeDoc,
            binding.tvTypeDoc,
            FileType.DOC,
            selected == FileType.DOC,
        )
    }

    private fun styleTypeChip(
        chip: View,
        icon: ImageView,
        label: TextView,
        type: FileType,
        selected: Boolean,
    ) {
        chip.isSelected = selected
        icon.setImageResource(if (selected) type.topIconSelectedRes else type.topIconRes)
        label.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.white else R.color.text_primary,
            ),
        )
    }

    private fun renderContent(state: CamListState) {
        binding.rvFiles.isVisible = state is CamListState.ListFiles
        binding.emptyState.isVisible = state is CamListState.Empty
        binding.permissionState.isVisible = state is CamListState.NeedPermission

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
