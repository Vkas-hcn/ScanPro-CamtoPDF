package com.scanpro.cam.pdf.data

import android.content.Context
import android.net.Uri
import com.scanpro.cam.pdf.home.FileType
import com.scanpro.cam.pdf.home.CamFileItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CamFavStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isFavorite(id: String): Boolean {
        return loadRaw().any { it.id == id }
    }

    fun toggle(item: CamFileItem): Boolean {
        val list = loadRaw().toMutableList()
        val index = list.indexOfFirst { it.id == item.id }
        return if (index >= 0) {
            list.removeAt(index)
            save(list)
            false
        } else {
            list.add(0, item)
            save(list)
            true
        }
    }

    fun remove(id: String) {
        val next = loadRaw().filterNot { it.id == id }
        save(next)
    }

    fun replace(oldId: String, newItem: CamFileItem) {
        val list = loadRaw().toMutableList()
        val index = list.indexOfFirst { it.id == oldId }
        if (index >= 0) {
            list[index] = newItem
            save(list)
        }
    }

    fun list(): List<CamFileItem> {
        val raw = loadRaw()
        val keep = raw.filter { exists(it) }
        if (keep.size != raw.size) {
            save(keep)
        }
        return keep
    }

    /** @deprecated Use [list] / [isFavorite] with id */
    fun allPaths(): Set<String> = loadRaw().map { it.id }.toSet()

    private fun exists(item: CamFileItem): Boolean {
        val uri = Uri.parse(item.uri)
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return false
                File(path).isFile
            }
            "content" -> {
                runCatching {
                    appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                }.getOrDefault(false)
            }
            else -> File(item.id).isFile
        }
    }

    private fun loadRaw(): List<CamFileItem> {
        migrateLegacyPathsIfNeeded()
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        CamFileItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            fileName = obj.getString("fileName"),
                            sizeLabel = obj.getString("sizeLabel"),
                            dateLabel = obj.getString("dateLabel"),
                            type = FileType.valueOf(obj.getString("type")),
                            uri = obj.getString("uri"),
                            sizeBytes = obj.optLong("sizeBytes"),
                            modifiedAt = obj.optLong("modifiedAt"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(items: List<CamFileItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("fileName", item.fileName)
                    .put("sizeLabel", item.sizeLabel)
                    .put("dateLabel", item.dateLabel)
                    .put("type", item.type.name)
                    .put("uri", item.uri)
                    .put("sizeBytes", item.sizeBytes)
                    .put("modifiedAt", item.modifiedAt),
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun migrateLegacyPathsIfNeeded() {
        if (prefs.contains(KEY_ITEMS)) return
        val legacy = prefs.getStringSet(KEY_PATHS, null) ?: return
        val items = legacy.mapNotNull { path ->
            val file = File(path)
            if (file.isFile) CamMadeDocs.toItem(file) else null
        }
        save(items)
        prefs.edit().remove(KEY_PATHS).apply()
    }

    companion object {
        private const val PREFS = "cam_favs"
        private const val KEY_PATHS = "paths"
        private const val KEY_ITEMS = "items"
    }
}
