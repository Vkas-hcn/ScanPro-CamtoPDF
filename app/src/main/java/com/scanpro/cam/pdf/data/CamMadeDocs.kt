package com.scanpro.cam.pdf.data

import android.content.Context
import com.scanpro.cam.pdf.home.FileType
import com.scanpro.cam.pdf.home.CamFileItem
import com.scanpro.cam.pdf.util.CamFileFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CamMadeDocs {

    const val DIR_NAME = "scan_pdf"

    fun directory(context: Context): File {
        return File(context.getExternalFilesDir(null), DIR_NAME).apply { mkdirs() }
    }

    suspend fun list(context: Context): List<CamFileItem> = withContext(Dispatchers.IO) {
        val dir = directory(context)
        dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0L }
            ?.mapNotNull { file -> toItem(file) }
            ?.sortedByDescending { it.modifiedAt }
            ?.toList()
            .orEmpty()
    }

    fun toItem(file: File): CamFileItem? {
        val type = FileType.fromFileName(file.name) ?: return null
        val modifiedAt = file.lastModified()
        return CamFileItem(
            id = file.absolutePath,
            name = CamFileFormat.displayName(file.name),
            fileName = file.name,
            sizeLabel = CamFileFormat.formatSize(file.length()),
            dateLabel = CamFileFormat.formatDate(modifiedAt),
            type = type,
            uri = file.toURI().toString(),
            sizeBytes = file.length(),
            modifiedAt = modifiedAt,
        )
    }

    fun resolveFile(context: Context, item: CamFileItem): File? {
        val byId = File(item.id)
        if (byId.isFile) return byId
        val byName = File(directory(context), item.fileName)
        return byName.takeIf { it.isFile }
    }
}
