package com.buncead.common.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bockcock.adjust.Pin
import com.scanpro.cam.pdf.R
import com.zppko.pdfplus.nor.horus.HintExt01
import com.zppko.pdfplus.nor.tool.LifecycleUtil

object PermNotifyUtil {

    val TAG = "NotifyPermission"

    val mRequestCode = 2656
    var lastRequestTime = 0L


    fun check2requestNotificationPermission(mActivity: Activity, callYes: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Util.iii { "$TAG requestPermissions() POST_NOTIFICATIONS" }
                lastRequestTime = System.currentTimeMillis()
                ActivityCompat.requestPermissions(mActivity, arrayOf<String>(Manifest.permission.POST_NOTIFICATIONS), mRequestCode)
            } else {
                Util.iii { "$TAG POST_NOTIFICATIONS 111 YES" }
                callYes.invoke()
            }
        } else {
            Util.iii { "$TAG POST_NOTIFICATIONS 222 YES" }
            callYes.invoke()
        }
    }


    fun checkNotificationPermission(mContext: Context, callOk: () -> Unit, callNo:()-> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                callNo.invoke()
            } else {
                callOk.invoke()
            }
        } else {
            callOk.invoke()
        }
    }

    fun hasPermission(mContext: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false
            } else {
                return true
            }
        } else {
            return true
        }
    }




    fun openNotificationSettings(mActivity: Activity){
        LifecycleUtil.notLaunch()
        val intent = Intent()
        intent. action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        intent. putExtra(Settings.EXTRA_APP_PACKAGE, mActivity.packageName)
//        intent.  addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
//        intent.  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        intent.  addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK)
        mActivity.startActivityForResult(intent,mRequestCode)
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        mActivity: AppCompatActivity,
        call: (Boolean) -> Unit // isAllow
    ) {
        Util.iii { "$TAG onRequestPermissionsResult() requestCode=${requestCode}, permissions=$permissions, grantResults=$grantResults" }
//        for ((index, string) in permissions.withIndex()) {
//            Util.iii { "${string}->${grantResults.get(index)}" }
//        }
        if (requestCode == mRequestCode){
            if ((System.currentTimeMillis()- lastRequestTime)<500){
                val imgRes = R.drawable.ic_permission
                val title = mActivity.getString(R.string.perm_req_ntf_title)
                val msg = mActivity.getString(R.string.perm_req_ntf_msg)
                DialogUtils.showPerm(mActivity,imgRes,title,msg,{
                    openNotificationSettings(mActivity)
                },{
                    call.invoke(false)
                    Pin.event(Pin.ntf_perm_no)
                })
            }else{
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    call.invoke(true) // 用户允许了通知权限
                    HintExt01.launchFg()
                    Pin.event(Pin.ntf_perm_ok)
                }else{
                    call.invoke(false) // 用户拒绝了通知权限
                    Pin.event(Pin.ntf_perm_no)
                }
            }
        }

    }

    // call:isAllow
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?,mActivity: Activity, call: (Boolean) -> Unit) {
        Util.iii { "$TAG onActivityResult() requestCode=${requestCode}, resultCode=$resultCode, data=$data" }
        if (requestCode == mRequestCode){
            // check notify Permission
            PermNotifyUtil.checkNotificationPermission(mActivity,{
                call.invoke(true)
                HintExt01.launchFg()
                Pin.event(Pin.ntf_perm_ok)
            },{
                call.invoke(false)
                Pin.event(Pin.ntf_perm_no)
            })
        }
    }




    fun onRequestPermissionsResult2(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        mActivity: AppCompatActivity,
        call: (Boolean) -> Unit // isAllow
    ) {
        Util.iii { "$TAG onRequestPermissionsResult2() requestCode=${requestCode}, permissions=$permissions, grantResults=$grantResults" }
//        for ((index, string) in permissions.withIndex()) {
//            Util.iii { "${string}->${grantResults.get(index)}" }
//        }
        if (requestCode == mRequestCode){
            if ((System.currentTimeMillis()- lastRequestTime)<500){
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    call.invoke(true)      // 用户允许了通知权限
                    HintExt01.launchFg()
                }else{
                    call.invoke(false)     // 用户拒绝了通知权限
                }
            }else{
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    call.invoke(true)      // 用户允许了通知权限
                    HintExt01.launchFg()
                    Pin.event(Pin.ntf_perm_ok)
                }else{
                    call.invoke(false)   // 用户拒绝了通知权限
                    Pin.event(Pin.ntf_perm_no)
                }
            }
        }

    }

}