package com.mymy.mymy

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.databinding.DialogReviewBinding

object DialogUtil {

    fun review(mActivity: AppCompatActivity, call:(Int)->Unit){
        val contents: Array<Int> = arrayOf(R.string.rev1,R.string.rev2,R.string.rev3,R.string.rev4,R.string.rev5)

        val btns: MutableList<ImageView> = mutableListOf<ImageView>()

        val binding: DialogReviewBinding = DialogReviewBinding.inflate(mActivity.layoutInflater)
        val build = MaterialAlertDialogBuilder(mActivity)
        build.setView(binding.root)




        val dialog: AlertDialog = build.create()

        binding.no.setOnClickListener { dialog.dismiss() }
        binding.yes.setOnClickListener {
            call.invoke(5)
        }
        dialog.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val attr: WindowManager.LayoutParams = it.attributes
            attr.width = -1
            it.attributes = attr;
        }
        dialog.show()
    }


}