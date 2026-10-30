package com.scanpro.cam.pdf.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.scanpro.cam.pdf.ads.SpHotStartGate
import com.scanpro.cam.pdf.home.FileType
import java.io.File

sealed class CamRenameOutcome {
    data class Success(val file: File) : CamRenameOutcome()
    data object Empty : CamRenameOutcome()
    data object Exists : CamRenameOutcome()
    data object Failed : CamRenameOutcome()
}

object CamFileOps {

    private const val TAG = "CamFileOps"
    private const val SHARE_CACHE_DIR = "share_cache"

    fun rename(file: File, newDisplayName: String): CamRenameOutcome {
        val trimmed = newDisplayName.trim()
        if (trimmed.isEmpty()) return CamRenameOutcome.Empty
        val ext = file.extension
        val targetName = if (ext.isNotEmpty()) "$trimmed.$ext" else trimmed
        if (targetName == file.name) return CamRenameOutcome.Success(file)
        val target = File(file.parentFile, targetName)
        if (target.exists()) return CamRenameOutcome.Exists
        return if (file.renameTo(target)) {
            CamRenameOutcome.Success(target)
        } else {
            CamRenameOutcome.Failed
        }
    }

    fun delete(file: File): Boolean {
        return file.exists() && file.delete()
    }

    fun share(context: Context, file: File, type: FileType): Boolean {
        if (!file.exists()) return false
        return runCatching {
            val uri = uriForFile(context, file)
            launchShare(context, uri, type, file.name)
            true
        }.onFailure {
            Log.e(TAG, "share file failed: ${file.absolutePath}", it)
        }.getOrDefault(false)
    }

    fun shareUri(context: Context, uriString: String, type: FileType, fileName: String): Boolean {
        return runCatching {
            val source = Uri.parse(uriString)
            val shareUri = when (source.scheme?.lowercase()) {
                "content" -> copyContentToShareCache(context, source, fileName)
                "file" -> {
                    val path = source.path ?: error("empty file path")
                    uriForFile(context, File(path))
                }
                else -> {
                    // raw absolute path
                    val file = File(uriString)
                    if (file.isFile) uriForFile(context, file) else error("unsupported uri: $uriString")
                }
            }
            launchShare(context, shareUri, type, fileName)
            true
        }.onFailure {
            Log.e(TAG, "share uri failed: $uriString", it)
        }.getOrDefault(false)
    }

    private fun launchShare(context: Context, uri: Uri, type: FileType, fileName: String) {
        val mime = mimeType(type, fileName)
        val send = Intent(Intent.ACTION_SEND).apply {
            this.type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            // ViewModel passes Application context — required for startActivity.
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // 分享面板会把自己送到后台；用户回来时不该弹开屏（阈值 0 秒时必须显式排除）
        SpHotStartGate.suppressNext()
        context.startActivity(chooser)
    }

    private fun uriForFile(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (_: IllegalArgumentException) {
            // File is outside configured FileProvider paths — copy then share.
            copyFileToShareCache(context, file, file.name)
        }
    }

    private fun copyContentToShareCache(context: Context, uri: Uri, fileName: String): Uri {
        val dir = File(context.cacheDir, SHARE_CACHE_DIR).apply { mkdirs() }
        val safeName = fileName.ifBlank { "shared.bin" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val out = File(dir, safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: error("cannot open content uri: $uri")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
    }

    private fun copyFileToShareCache(context: Context, file: File, fileName: String): Uri {
        val dir = File(context.cacheDir, SHARE_CACHE_DIR).apply { mkdirs() }
        val safeName = fileName.ifBlank { file.name }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val out = File(dir, safeName)
        file.inputStream().use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
    }

    fun mimeType(type: FileType, fileName: String): String {
        val fromExt = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
        if (!fromExt.isNullOrBlank()) return fromExt
        return when (type) {
            FileType.PDF -> "application/pdf"
            FileType.TXT -> "text/plain"
            FileType.PPT -> "application/vnd.ms-powerpoint"
            FileType.XLS -> "application/vnd.ms-excel"
            FileType.DOC -> "application/msword"
        }
    }
}
