package com.scanpro.cam.pdf.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.databinding.DialogCamDeleteBinding
import com.scanpro.cam.pdf.databinding.DialogCamRenameBinding

object CamFileAlerts {

    fun showRename(
        context: Context,
        currentName: String,
        onSave: (String) -> Unit,
    ): Dialog {
        val binding = DialogCamRenameBinding.inflate(LayoutInflater.from(context))
        val dialog = createCenteredDialog(context)
        dialog.setContentView(binding.root)

        binding.etName.setText(currentName)
        binding.etName.setSelection(currentName.length)
        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnSave.setOnClickListener {
            onSave(binding.etName.text?.toString().orEmpty())
            dialog.dismiss()
        }
        dialog.show()
        return dialog
    }

    fun showDelete(
        context: Context,
        onConfirm: () -> Unit,
    ): Dialog {
        val binding = DialogCamDeleteBinding.inflate(LayoutInflater.from(context))
        val dialog = createCenteredDialog(context)
        dialog.setContentView(binding.root)

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnDelete.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        dialog.show()
        return dialog
    }

    private fun createCenteredDialog(context: Context): Dialog {
        return Dialog(context, R.style.Theme_CamPdf_Dialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setWindowAnimations(0)
            setCanceledOnTouchOutside(true)
            setOnShowListener {
                window?.setLayout(
                    (context.resources.displayMetrics.widthPixels * 0.82f).toInt(),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
            }
        }
    }
}
