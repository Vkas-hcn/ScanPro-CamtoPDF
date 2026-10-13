package com.scanpro.cam.pdf.util

import android.util.Log

/** 统一日志。过滤 tag `spLog`，模块前缀与 EfileScan `AppLog` 相同。 */
object AppLog {
    const val TAG = "spLog"

    val app = Logger("app")
    val ads = Logger("ads")
    val tracking = Logger("tracking")
    val notification = Logger("notification")
    val network = Logger("network")

    class Logger(private val module: String) {
        fun i(message: String) {
            Log.i(TAG, format(module, message))
        }

        fun d(message: String) {
            Log.d(TAG, format(module, message))
        }

        fun w(message: String, error: Throwable? = null) {
            if (error == null) {
                Log.w(TAG, format(module, message))
            } else {
                Log.w(TAG, format(module, message), error)
            }
        }

        fun e(message: String, error: Throwable? = null) {
            if (error == null) {
                Log.e(TAG, format(module, message))
            } else {
                Log.e(TAG, format(module, message), error)
            }
        }
    }

    internal fun format(module: String, message: String): String =
        "[${module.trim().lowercase()}] ${message.trim()}"
}
