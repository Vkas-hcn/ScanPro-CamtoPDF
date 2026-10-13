package com.scanpro.cam.pdf.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivityCamPdfReaderBinding
import com.scanpro.cam.pdf.home.CamFileItem
import kotlinx.coroutines.launch

/** In-app PDF preview using system [android.graphics.pdf.PdfRenderer]. */
class CamPdfReaderActivity : SpActivity<ActivityCamPdfReaderBinding>() {

    override val applySystemBarsPadding: Boolean = false
    override val lightStatusBarIcons: Boolean = true

    private val viewModel: CamPdfReaderViewModel by viewModels()

    override fun inflateBinding(inflater: LayoutInflater): ActivityCamPdfReaderBinding {
        return ActivityCamPdfReaderBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        applyInsets()
        binding.btnBack.setOnClickListener { finish() }

        val uri = intent.getStringExtra(EXTRA_URI).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (uri.isBlank()) {
            binding.progress.isVisible = false
            binding.tvError.isVisible = true
            binding.tvError.setText(R.string.viewer_open_failed)
            return
        }

        binding.tvTitle.text = title
        if (savedInstanceState == null) {
            viewModel.open(uri, title)
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvTitle.text = state.title
                    binding.progress.isVisible = state.isLoading
                    binding.tvError.isVisible = state.openFailed && !state.isLoading
                    if (state.openFailed) {
                        binding.tvError.setText(R.string.viewer_open_failed)
                    }

                    binding.rvPdfPages.isVisible = state.pageCount > 0 && !state.isLoading
                    binding.tvPage.isVisible = state.pageCount > 0 && !state.isLoading

                    if (state.pageCount > 0 && !state.isLoading) {
                        binding.tvPage.text = getString(
                            R.string.viewer_page_indicator,
                            1,
                            state.pageCount,
                        )
                        if ((binding.rvPdfPages.adapter?.itemCount ?: -1) != state.pageCount) {
                            binding.rvPdfPages.layoutManager =
                                LinearLayoutManager(this@CamPdfReaderActivity)
                            binding.rvPdfPages.clearOnScrollListeners()
                            binding.rvPdfPages.adapter = CamPdfPageAdapter(
                                scope = lifecycleScope,
                                pageCount = state.pageCount,
                                renderPage = { index, width -> viewModel.renderPage(index, width) },
                            )
                            binding.rvPdfPages.addOnScrollListener(
                                object : RecyclerView.OnScrollListener() {
                                    override fun onScrolled(
                                        recyclerView: RecyclerView,
                                        dx: Int,
                                        dy: Int,
                                    ) {
                                        val lm = recyclerView.layoutManager as? LinearLayoutManager
                                            ?: return
                                        val first = lm.findFirstVisibleItemPosition()
                                        if (first >= 0) {
                                            binding.tvPage.text = getString(
                                                R.string.viewer_page_indicator,
                                                first + 1,
                                                state.pageCount,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyInsets() {
        val toolbar = binding.toolbar
        val initialTop = toolbar.paddingTop
        val content = binding.contentContainer
        val initialBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.viewerRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(top = initialTop + bars.top)
            content.updatePadding(bottom = initialBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.viewerRoot)
    }

    companion object {
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_TITLE = "extra_title"

        fun start(context: Context, item: CamFileItem) {
            context.startActivity(
                Intent(context, CamPdfReaderActivity::class.java)
                    .putExtra(EXTRA_URI, item.uri)
                    .putExtra(EXTRA_TITLE, item.name),
            )
        }
    }
}
