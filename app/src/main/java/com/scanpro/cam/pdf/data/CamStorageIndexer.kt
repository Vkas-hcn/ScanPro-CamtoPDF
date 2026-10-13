package com.scanpro.cam.pdf.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.scanpro.cam.pdf.home.FileType
import com.scanpro.cam.pdf.home.CamFileItem
import com.scanpro.cam.pdf.util.CamFileFormat
import com.scanpro.cam.pdf.util.CamStoragePermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

class CamStorageIndexer(context: Context) {

    private val appContext = context.applicationContext
    private val creationDirPath: String =
        CamMadeDocs.directory(appContext).absolutePath.trimEnd('/')

    suspend fun scanDocuments(): List<CamFileItem> = withContext(Dispatchers.IO) {
        val results = LinkedHashMap<String, CamFileItem>()
        // App-created files live under getExternalFilesDir()/scan_pdf and must be
        // merged explicitly — Home previously only scanned MediaStore/public dirs,
        // so rename in Creation made the Home entry vanish.
        val creationNameKeys = mergeCreationDocuments(results)
        scanMediaStore(results, creationNameKeys)
        if (CamStoragePermit.hasFileAccess(appContext)) {
            scanPublicFolders(results, creationNameKeys)
        }
        results.values.sortedByDescending { it.modifiedAt }
    }

    private suspend fun mergeCreationDocuments(
        results: MutableMap<String, CamFileItem>,
    ): Set<String> {
        val nameKeys = mutableSetOf<String>()
        CamMadeDocs.list(appContext).forEach { item ->
            results["creation:${item.id}"] = item
            nameKeys += nameSizeKey(item.fileName, item.sizeBytes)
        }
        return nameKeys
    }

    private fun scanMediaStore(
        results: MutableMap<String, CamFileItem>,
        creationNameKeys: Set<String>,
    ) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        // DATA is deprecated but still useful to skip our private creation folder.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            projection.add(MediaStore.Files.FileColumns.DATA)
        } else {
            projection.add(MediaStore.Files.FileColumns.DATA)
        }

        val extensionClauses = ALL_EXTENSIONS.joinToString(" OR ") {
            "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE ?"
        }
        val selection = "($extensionClauses) AND ${MediaStore.Files.FileColumns.SIZE} > 0"
        val selectionArgs = ALL_EXTENSIONS.map { "%.$it" }.toTypedArray()
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        appContext.contentResolver.query(
            collection,
            projection.toTypedArray(),
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)?.trim().orEmpty()
                if (name.isEmpty()) continue
                val type = FileType.fromFileName(name) ?: continue
                val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                val dedupeKey = nameSizeKey(name, size)
                if (dedupeKey in creationNameKeys) continue

                if (dataCol >= 0) {
                    val dataPath = cursor.getString(dataCol)
                    if (!dataPath.isNullOrBlank() && isUnderCreationDir(dataPath)) continue
                }

                val id = cursor.getLong(idCol)
                val modifiedAt = normalizeEpochMillis(cursor.getLong(modifiedCol))
                val uri = android.content.ContentUris.withAppendedId(collection, id).toString()
                putIfAbsent(
                    results = results,
                    dedupeKey = dedupeKey,
                    item = CamFileItem(
                        id = "media-$id",
                        name = CamFileFormat.displayName(name),
                        fileName = name,
                        sizeLabel = CamFileFormat.formatSize(size),
                        dateLabel = CamFileFormat.formatDate(modifiedAt),
                        type = type,
                        uri = uri,
                        sizeBytes = size,
                        modifiedAt = modifiedAt,
                    ),
                )
            }
        }
    }

    private suspend fun scanPublicFolders(
        results: MutableMap<String, CamFileItem>,
        creationNameKeys: Set<String>,
    ) {
        val roots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        ).filter { it.exists() && it.canRead() }

        for (root in roots) {
            walkDirectory(root, results, creationNameKeys, depth = 0)
        }
    }

    private suspend fun walkDirectory(
        dir: File,
        results: MutableMap<String, CamFileItem>,
        creationNameKeys: Set<String>,
        depth: Int,
    ) {
        if (depth > MAX_WALK_DEPTH) return
        coroutineContext.ensureActive()
        val children = dir.listFiles() ?: return
        for (child in children) {
            coroutineContext.ensureActive()
            if (child.isDirectory) {
                if (child.name.startsWith(".")) continue
                walkDirectory(child, results, creationNameKeys, depth + 1)
            } else if (child.isFile) {
                addFileItem(child, results, creationNameKeys)
            }
        }
    }

    private fun addFileItem(
        file: File,
        results: MutableMap<String, CamFileItem>,
        creationNameKeys: Set<String>,
    ) {
        val type = FileType.fromFileName(file.name) ?: return
        if (file.length() <= 0L) return
        if (isUnderCreationDir(file.absolutePath)) return
        val size = file.length()
        val dedupeKey = nameSizeKey(file.name, size)
        if (dedupeKey in creationNameKeys) return
        val modifiedAt = file.lastModified()
        putIfAbsent(
            results = results,
            dedupeKey = dedupeKey,
            item = CamFileItem(
                id = "file-${file.absolutePath.hashCode()}",
                name = CamFileFormat.displayName(file.name),
                fileName = file.name,
                sizeLabel = CamFileFormat.formatSize(size),
                dateLabel = CamFileFormat.formatDate(modifiedAt),
                type = type,
                uri = android.net.Uri.fromFile(file).toString(),
                sizeBytes = size,
                modifiedAt = modifiedAt,
            ),
        )
    }

    private fun putIfAbsent(
        results: MutableMap<String, CamFileItem>,
        dedupeKey: String,
        item: CamFileItem,
    ) {
        val existing = results[dedupeKey]
        if (existing == null || item.modifiedAt > existing.modifiedAt) {
            results[dedupeKey] = item
        }
    }

    private fun isUnderCreationDir(path: String): Boolean {
        val normalized = path.trimEnd('/')
        return normalized == creationDirPath || normalized.startsWith("$creationDirPath/")
    }

    private fun nameSizeKey(fileName: String, sizeBytes: Long): String {
        return "${fileName.lowercase()}_$sizeBytes"
    }

    private fun normalizeEpochMillis(value: Long): Long {
        return if (value > 0L && value < 10_000_000_000L) value * 1000L else value
    }

    companion object {
        private const val MAX_WALK_DEPTH = 6
        private val ALL_EXTENSIONS: List<String> =
            FileType.entries.flatMap { it.extensions }.distinct()
    }
}
