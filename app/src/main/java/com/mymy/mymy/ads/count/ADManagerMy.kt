package com.mymy.mymy.ads.count


import android.content.Context
import com.bockcock.adjust.KvUtil
import com.buncead.common.util.Util

object ADManagerMy {

    val TAG = "ADManagerMy"

    fun start(context: Context){
        getCount()

    }


    var mSaveTime = 0L
    val KEY_SAVE_TIME = "KEY_SAVE_TIME"
    private var mLoaded = false

    /**
     * 本地时间戳只读一次：读过后不再回填。
     * 原来放到 IO 线程异步回填，可能在 [isToday] 刚写入“今天”之后把旧值写回来，
     * 导致同一天被清零两次。
     */
    @Synchronized
    fun getCount(){
        if (mLoaded) return
        mSaveTime = KvUtil.getLong(KEY_SAVE_TIME)
        mLoaded = true
    }

    @Synchronized
    fun isToday(): Boolean {
        getCount()
        if (Util.isSameDayAsToday(mSaveTime)) {
            Util.iii { "isToday() yes same day" }
            return true
        }
        Util.iii { "isToday() no same day, reset data" }
        mSaveTime = System.currentTimeMillis()
        KvUtil.setLong(KEY_SAVE_TIME, mSaveTime)

        AdIdCount.reset()
        return false
    }
}
