package com.scanpro.cam.pdf.viewer

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Prepares a document URI for AndroidDocViewer (WebView + PDF.js).
 *
 * Huawei / some OEM WebViews fail to fetch raw `content://` from
 * `viewer.html?file=content://...`. Copy to app cache and pass an
 * encoded `file://` URL so [DocViewerFragment] can intercept & read it.
 */
object CamPreviewUri {

    private const val TAG = "CamPreviewUri"
    private const val CACHE_DIR = "doc_preview"

    fun prepare(context: Context, source: String, displayName: String): String {
        val local = toLocalFile(context.applicationContext, source, displayName)
        val fileUrl = Uri.fromFile(local).toString()
        val encoded = Uri.encode(fileUrl, /* allow */ null)
        Log.i(TAG, "preview source=$source -> $fileUrl (encoded)")
        return encoded
    }

    private fun toLocalFile(context: Context, source: String, displayName: String): File {
        val uri = Uri.parse(source)
        when (uri.scheme?.lowercase()) {
            "file" -> {
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    if (file.exists() && file.canRead()) return file
                }
            }
            null -> {
                val file = File(source)
                if (file.exists() && file.canRead()) return file
            }
        }

        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val ext = displayName.substringAfterLast('.', missingDelimiterValue = "bin")
            .lowercase()
            .ifBlank { "bin" }
        val key = Integer.toHexString(source.hashCode())
        val out = File(dir, "${key}.$ext")
        if (out.exists() && out.length() > 0L) {
            return out
        }

        val input = when (uri.scheme?.lowercase()) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> uri.path?.let { File(it).inputStream() }
            else -> runCatching { File(source).inputStream() }.getOrNull()
                ?: context.contentResolver.openInputStream(uri)
        } ?: error("Cannot open document: $source")

        input.use { inputStream ->
            out.outputStream().use { output ->
                inputStream.copyTo(output)
            }
        }
        Log.i(TAG, "copied to cache: ${out.absolutePath} size=${out.length()}")
        return out
    }
}
