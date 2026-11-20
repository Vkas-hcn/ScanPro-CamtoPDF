package com.mymy.mymy.ads.count


import android.content.Context
import com.bockcock.adjust.KvUtil
import com.buncead.common.util.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ADManagerMy {

    val TAG = "ADManagerMy"

    fun start(context: Context){
        getCount()

    }


    var mSaveTime = 0L
    val KEY_SAVE_TIME = "KEY_SAVE_TIME"

    fun getCount(){
        CoroutineScope(Dispatchers.IO).launch {
            mSaveTime = KvUtil.getLong(KEY_SAVE_TIME)
        }
    }

    fun isToday(): Boolean {
        val saveTime: Long = mSaveTime
        if (Util.isSameDayAsToday(saveTime)) {
            Util.iii { "isToday() yes same day" }
            return true
        } else {
            Util.iii { "isToday() no same day, reset data" }
            mSaveTime = System.currentTimeMillis()
            KvUtil.setLong(KEY_SAVE_TIME, mSaveTime)

            AdIdCount.reset()
            return false
        }
    }




}