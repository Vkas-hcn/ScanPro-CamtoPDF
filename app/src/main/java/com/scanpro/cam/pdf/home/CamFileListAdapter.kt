package com.scanpro.cam.pdf.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.databinding.ItemCamFileBinding

class CamFileListAdapter(
    private val onMoreClick: (CamFileItem, View) -> Unit = { _, _ -> },
    private val onItemClick: (CamFileItem) -> Unit = {},
) : ListAdapter<CamFileItem, CamFileListAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<CamFileItem>() {
        override fun areItemsTheSame(oldItem: CamFileItem, newItem: CamFileItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CamFileItem, newItem: CamFileItem): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCamFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemCamFileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CamFileItem) {
            binding.ivFileType.setImageResource(item.type.listIconRes)
            binding.tvFileName.text = item.name
            binding.tvFileMeta.text = binding.root.context.getString(
                R.string.home_file_meta,
                item.sizeLabel,
                item.dateLabel,
            )
            binding.btnMore.setOnClickListener { onMoreClick(item, it) }
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
