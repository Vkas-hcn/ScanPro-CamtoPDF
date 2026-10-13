package com.scanpro.cam.pdf.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CamImageToPdf {

    suspend fun convert(
        context: Context,
        imagePaths: List<String>,
        fileName: String? = null,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(imagePaths.isNotEmpty()) { "empty images" }
            val dir = CamMadeDocs.directory(context)
            val name = fileName ?: SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(dir, "$name.pdf")
            val document = PdfDocument()
            try {
                imagePaths.forEachIndexed { index, path ->
                    val bitmap = BitmapFactory.decodeFile(path)
                        ?: error("decode failed: $path")
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width,
                        bitmap.height,
                        index + 1,
                    ).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(page)
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                FileOutputStream(outFile).use { document.writeTo(it) }
            } finally {
                document.close()
            }
            outFile
        }
    }
}
