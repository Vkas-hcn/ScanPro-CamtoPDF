package com.scanpro.cam.pdf.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivityCamOfficeReaderBinding
import com.scanpro.cam.pdf.home.FileType
import com.scanpro.cam.pdf.home.CamFileItem
import com.seapeak.docviewer.DocViewerFragment
import com.seapeak.docviewer.config.DocConfig
import com.seapeak.docviewer.config.DocType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CamOfficeReaderActivity : SpActivity<ActivityCamOfficeReaderBinding>() {

    override val applySystemBarsPadding: Boolean = false
    override val lightStatusBarIcons: Boolean = true

    override fun inflateBinding(inflater: LayoutInflater): ActivityCamOfficeReaderBinding {
        return ActivityCamOfficeReaderBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        applyToolbarInset()
        binding.btnBack.setOnClickListener { finish() }

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val uri = intent.getStringExtra(EXTRA_URI).orEmpty()
        val typeName = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val type = runCatching { FileType.valueOf(typeName) }.getOrNull()?.toDocType()
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty().ifBlank { title }

        binding.tvTitle.text = title
        if (uri.isBlank() || type == null) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            openDocument(uri, fileName, type)
        }
    }

    private fun openDocument(sourceUri: String, fileName: String, type: DocType) {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                runCatching {
                    CamPreviewUri.prepare(this@CamOfficeReaderActivity, sourceUri, fileName)
                }.onFailure {
                    Log.e(TAG, "prepare preview uri failed: $sourceUri", it)
                }.getOrNull()
            }
            binding.progress.visibility = View.GONE
            if (prepared.isNullOrBlank()) {
                Toast.makeText(
                    this@CamOfficeReaderActivity,
                    "Unable to open document",
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
                return@launch
            }
            val config = DocConfig(prepared, type)
            supportFragmentManager.commit {
                replace(binding.docContainer.id, DocViewerFragment.newInstance(config))
            }
        }
    }

    private fun applyToolbarInset() {
        val toolbar = binding.toolbar
        val initialTop = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.viewerRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(top = initialTop + bars.top)
            binding.docContainer.updatePadding(bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.viewerRoot)
    }

    companion object {
        private const val TAG = "AppDocViewer"
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TYPE = "extra_type"
        private const val EXTRA_FILE_NAME = "extra_file_name"

        fun start(context: Context, item: CamFileItem) {
            context.startActivity(
                Intent(context, CamOfficeReaderActivity::class.java)
                    .putExtra(EXTRA_URI, item.uri)
                    .putExtra(EXTRA_TITLE, item.name)
                    .putExtra(EXTRA_TYPE, item.type.name)
                    .putExtra(EXTRA_FILE_NAME, item.fileName),
            )
        }
    }
}

private fun FileType.toDocType(): DocType = when (this) {
    FileType.PDF -> DocType.PDF
    FileType.DOC -> DocType.WORD
    FileType.XLS -> DocType.EXCEL
    FileType.PPT -> DocType.PPT
    FileType.TXT -> DocType.TXT
}
