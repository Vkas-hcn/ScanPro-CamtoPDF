package com.buncead.common.util

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scanpro.cam.pdf.databinding.FlotPermBinding

object DialogUtils {



    fun showPerm(mActivity: AppCompatActivity,imgRes:Int, title: String, content: String, callYes:()->Unit, callNo:()-> Unit): AlertDialog {
        val binding: FlotPermBinding = FlotPermBinding.inflate(mActivity.layoutInflater)
        val build = MaterialAlertDialogBuilder(mActivity)
        build.setView(binding.root)
        val dialog: AlertDialog = build.create()

        binding.image.setImageResource(imgRes)
        binding.title.text = title
        binding.content.text = content
        binding.ok.setOnClickListener {
            dialog.dismiss()
            callYes.invoke()
        }
        binding.close.setOnClickListener {
            dialog.dismiss()
            callNo.invoke()
        }
        dialog.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val attr: WindowManager.LayoutParams = it.attributes
            attr.width = WindowManager.LayoutParams.MATCH_PARENT
            it.attributes = attr;
            val paddingPx: Int = Util.dp2px(30)
            it.decorView.setPadding(paddingPx, 0, paddingPx, 0)
        }

        dialog.show()
        return dialog
    }

}