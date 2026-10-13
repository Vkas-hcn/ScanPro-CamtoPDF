package com.scanpro.cam.pdf.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object CamUriAccess {

    fun openFileDescriptor(context: Context, uriString: String): ParcelFileDescriptor? {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "content" -> context.contentResolver.openFileDescriptor(uri, "r")
            "file" -> {
                val path = uri.path ?: return null
                val file = File(path)
                if (!file.exists()) return null
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            else -> null
        }
    }

    fun openInputStream(context: Context, uriString: String): InputStream? {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> {
                val path = uri.path ?: return null
                val file = File(path)
                if (!file.exists()) return null
                FileInputStream(file)
            }
            else -> null
        }
    }
}
