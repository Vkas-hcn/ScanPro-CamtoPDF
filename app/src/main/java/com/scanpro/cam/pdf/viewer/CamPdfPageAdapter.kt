package com.scanpro.cam.pdf.viewer

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.scanpro.cam.pdf.databinding.ItemCamPdfPageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CamPdfPageAdapter(
    private val scope: CoroutineScope,
    private val pageCount: Int,
    private val renderPage: (index: Int, widthPx: Int) -> Bitmap?,
) : RecyclerView.Adapter<CamPdfPageAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCamPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = pageCount

    override fun onViewRecycled(holder: VH) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    inner class VH(
        private val binding: ItemCamPdfPageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var job: Job? = null

        fun bind(index: Int) {
            job?.cancel()
            binding.ivPage.setImageBitmap(null)
            job = scope.launch {
                val width = binding.root.width
                    .takeIf { it > 0 }
                    ?: binding.root.resources.displayMetrics.widthPixels
                val bitmap = withContext(Dispatchers.IO) {
                    renderPage(index, width)
                }
                if (bitmap != null) {
                    binding.ivPage.setImageBitmap(bitmap)
                }
            }
        }

        fun recycle() {
            job?.cancel()
            job = null
            binding.ivPage.setImageBitmap(null)
        }
    }
}
