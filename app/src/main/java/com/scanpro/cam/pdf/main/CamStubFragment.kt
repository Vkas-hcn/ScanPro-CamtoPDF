package com.scanpro.cam.pdf.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.scanpro.cam.pdf.base.SpFragment
import com.scanpro.cam.pdf.databinding.FragmentCamStubBinding

class CamStubFragment : SpFragment<FragmentCamStubBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentCamStubBinding {
        return FragmentCamStubBinding.inflate(inflater, container, false)
    }

    override fun initView(savedInstanceState: Bundle?) {
        binding.tvPlaceholder.text = requireArguments().getString(ARG_TITLE).orEmpty()
    }

    companion object {
        private const val ARG_TITLE = "title"

        fun newInstance(title: String): CamStubFragment {
            return CamStubFragment().apply {
                arguments = Bundle().apply { putString(ARG_TITLE, title) }
            }
        }
    }
}
