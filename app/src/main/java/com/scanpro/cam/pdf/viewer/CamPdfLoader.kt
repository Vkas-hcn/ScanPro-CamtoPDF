package com.scanpro.cam.pdf.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.scanpro.cam.pdf.util.CamUriAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CamPdfLoader(context: Context) {

    private val appContext = context.applicationContext
    private val renderLock = Any()
    private var pdfRenderer: PdfRenderer? = null
    private var pdfPfd: ParcelFileDescriptor? = null

    suspend fun open(uri: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            close()
            val pfd = CamUriAccess.openFileDescriptor(appContext, uri)
                ?: return@runCatching Result.Error
            pdfPfd = pfd
            val renderer = PdfRenderer(pfd)
            pdfRenderer = renderer
            if (renderer.pageCount <= 0) {
                close()
                Result.Error
            } else {
                Result.Ok(renderer.pageCount)
            }
        }.getOrElse {
            close()
            Result.Error
        }
    }

    fun renderPage(index: Int, targetWidth: Int): Bitmap? {
        synchronized(renderLock) {
            val renderer = pdfRenderer ?: return null
            if (index !in 0 until renderer.pageCount) return null
            return renderer.openPage(index).use { page ->
                val width = targetWidth.coerceAtLeast(1)
                val height = (page.height.toFloat() / page.width.toFloat() * width)
                    .toInt()
                    .coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    fun close() {
        synchronized(renderLock) {
            runCatching { pdfRenderer?.close() }
            runCatching { pdfPfd?.close() }
            pdfRenderer = null
            pdfPfd = null
        }
    }

    sealed class Result {
        data class Ok(val pageCount: Int) : Result()
        data object Error : Result()
    }
}
