package com.scanpro.cam.pdf.util

import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object CamFileFormat {

    private val sizeFormat = DecimalFormat("#.#")

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return "${sizeFormat.format(value)} ${units[digitGroups]}"
    }

    fun formatDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            .format(Date(epochMillis))
    }

    fun displayName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }
}
