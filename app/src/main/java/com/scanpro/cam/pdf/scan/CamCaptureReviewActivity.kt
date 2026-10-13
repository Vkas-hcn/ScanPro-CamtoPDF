package com.scanpro.cam.pdf.scan

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.data.CamImageToPdf
import com.scanpro.cam.pdf.databinding.ActivityCamCaptureReviewBinding
import com.scanpro.cam.pdf.databinding.ItemCamCapturePageBinding
import com.scanpro.cam.pdf.rate.SpRateFlow
import com.scanpro.cam.pdf.analytics.SpTracker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CamCaptureReviewActivity : SpActivity<ActivityCamCaptureReviewBinding>() {

    override val applySystemBarsPadding: Boolean = false
    override val lightStatusBarIcons: Boolean = true

    private val adapter = CamCapturePageAdapter(
        onDelete = { index ->
            CamCaptureSession.removeAt(index)
            refresh()
            if (CamCaptureSession.size() == 0) {
                finish()
            }
        },
    )

    override fun inflateBinding(inflater: LayoutInflater): ActivityCamCaptureReviewBinding {
        return ActivityCamCaptureReviewBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        applyInsets()
        binding.rvPages.layoutManager = GridLayoutManager(this, 2)
        binding.rvPages.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAdd.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { savePdf() }
        val title = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        binding.tvTitle.text = title
        refresh()
    }

    private fun applyInsets() {
        val baseMargin = resources.getDimensionPixelSize(R.dimen.home_page_padding) / 2
        val save = binding.btnSave
        val bottomMargin =
            (save.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.reviewRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val backLp = binding.btnBack.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            backLp.topMargin = baseMargin + bars.top
            binding.btnBack.layoutParams = backLp
            val lp = save.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lp.bottomMargin = bottomMargin + bars.bottom
            save.layoutParams = lp
            insets
        }
        ViewCompat.requestApplyInsets(binding.reviewRoot)
    }

    private fun refresh() {
        adapter.submit(CamCaptureSession.paths())
    }

    private fun savePdf() {
        val paths = CamCaptureSession.paths()
        if (paths.isEmpty()) {
            Toast.makeText(this, R.string.scan_no_photos, Toast.LENGTH_SHORT).show()
            return
        }
        binding.progress.isVisible = true
        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            val result = CamImageToPdf.convert(
                context = this@CamCaptureReviewActivity,
                imagePaths = paths,
                fileName = binding.tvTitle.text?.toString(),
            )
            binding.progress.isVisible = false
            binding.btnSave.isEnabled = true
            result.onSuccess {
                SpTracker.log(this@CamCaptureReviewActivity, SpTracker.CONVERT_SUCCESS)
                Toast.makeText(this@CamCaptureReviewActivity, R.string.scan_saved, Toast.LENGTH_SHORT).show()
                CamCaptureSession.clear()
                setResult(RESULT_OK)
                val shown = SpRateFlow.tryPromptAfterPdfSave(
                    activity = this@CamCaptureReviewActivity,
                    onDismissed = { finish() },
                )
                if (!shown) {
                    finish()
                }
            }.onFailure {
                Toast.makeText(this@CamCaptureReviewActivity, R.string.scan_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private class CamCapturePageAdapter(
    private val onDelete: (Int) -> Unit,
) : RecyclerView.Adapter<CamCapturePageAdapter.VH>() {

    private val items = mutableListOf<String>()

    fun submit(paths: List<String>) {
        items.clear()
        items.addAll(paths)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCamCapturePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class VH(
        private val binding: ItemCamCapturePageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(path: String, index: Int) {
            binding.ivPage.setImageBitmap(BitmapFactory.decodeFile(path))
            binding.btnDelete.setOnClickListener { onDelete(index) }
        }
    }
}
