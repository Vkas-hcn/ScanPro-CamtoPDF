package com.buncead.common.util

import android.content.res.Resources
import android.util.Log
import android.util.TypedValue
import android.widget.Toast
import com.scanpro.cam.pdf.CamPdfApp
import java.util.Calendar

object Util {


    // TODO: xxx 关闭日志
    // v 黑色 d 蓝色 i 绿色 w 棕色
    inline fun vvv(call:()-> String){
//        Log.v("hhhh",call.invoke())
    }
    inline fun ddd(call:()-> String){
//        Log.d("hhhh",call.invoke())
    }
    inline fun iii(call:()-> String){
        Log.i("hhhh",call.invoke())
    }
    inline fun www(call:()-> String){
//        Log.w("hhhh",call.invoke())
    }
    inline fun www(call:()-> String,callTh: () -> Throwable){
//        Log.w("hhhh",call.invoke(),callTh.invoke())
    }
    inline fun eee(call:()-> String,){
        Log.e("hhhh",call.invoke())
    }

    fun toast(msg:String){
        Toast.makeText(CamPdfApp.Companion.app,msg, Toast.LENGTH_SHORT).show();
    }

    fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), Resources.getSystem().displayMetrics).toInt()
    }
    fun dp2pxF(dp: Int): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), Resources.getSystem().displayMetrics)
    }

    fun sp2px(sp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), Resources.getSystem().displayMetrics).toInt()
    }

    fun str(res:Int): String {
        return CamPdfApp.Companion.app.getString(res)
    }

//    fun openFile(mActivity: AppCompatActivity, bean: FileItem) {
//        if (!bean.file.exists()) {
//            Toast.makeText(mActivity, R.string.file_not_found, Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        MiddleData.fileEntity = bean
//        if (bean.type == FileType.TXT){
//            mActivity. startActivity(Intent(mActivity, FileViewerActivity::class.java).apply {
//                putExtra(FileViewerActivity.EXTRA_PATH, bean.path)
//                putExtra(FileViewerActivity.EXTRA_TYPE, bean.type.name)
//                putExtra(FileViewerActivity.EXTRA_NAME, bean.name)
//            })
//        }else{
//            DocsViewActivity.start(mActivity,bean.path)
//        }
//    }

    fun isSameDayAsToday(timestamp: Long): Boolean {
        // 获取今天的日期（时分秒清零）
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        // 设置给定时间戳的日期
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return today.timeInMillis == calendar.timeInMillis
    }


}