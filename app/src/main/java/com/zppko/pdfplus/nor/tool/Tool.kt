package com.zppko.pdfplus.nor.tool

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

object Tool{

    suspend fun <T> waitForCondition(
        timeout: Long,
        interval: Long = 500L,
        condition: () -> Boolean
    ): Boolean = withTimeoutOrNull(timeout) {
        while (!condition()) {
            delay(interval)
        }
        true
    } ?: false


    fun Long.isToday(): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()
        target.timeInMillis = this
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }
}
