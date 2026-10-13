package com.scanpro.cam.pdf.data

import android.content.Context
import android.net.Uri
import com.scanpro.cam.pdf.home.FileType
import com.scanpro.cam.pdf.home.CamFileItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CamRecentStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(item: CamFileItem) {
        val now = System.currentTimeMillis()
        val list = loadRaw().toMutableList()
        list.removeAll { it.item.id == item.id || it.item.uri == item.uri }
        list.add(0, Entry(item, now))
        while (list.size > MAX_ITEMS) {
            list.removeAt(list.lastIndex)
        }
        save(list)
    }

    fun list(): List<CamFileItem> {
        val raw = loadRaw()
        val keep = raw.filter { exists(it.item) }
        if (keep.size != raw.size) {
            save(keep)
        }
        return keep.map { it.item }
    }

    fun remove(id: String) {
        val next = loadRaw().filterNot { it.item.id == id }
        save(next)
    }

    fun replace(oldId: String, newItem: CamFileItem) {
        val list = loadRaw().toMutableList()
        val index = list.indexOfFirst { it.item.id == oldId }
        if (index >= 0) {
            val openedAt = list[index].openedAt
            list[index] = Entry(newItem, openedAt)
            save(list)
        }
    }

    private fun exists(item: CamFileItem): Boolean {
        val uri = Uri.parse(item.uri)
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return false
                File(path).isFile
            }
            "content" -> {
                runCatching {
                    appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true }
                        ?: false
                }.getOrDefault(false)
            }
            else -> {
                val byId = File(item.id)
                byId.isFile
            }
        }
    }

    private fun loadRaw(): List<Entry> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val type = FileType.valueOf(obj.getString("type"))
                    val item = CamFileItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        fileName = obj.getString("fileName"),
                        sizeLabel = obj.getString("sizeLabel"),
                        dateLabel = obj.getString("dateLabel"),
                        type = type,
                        uri = obj.getString("uri"),
                        sizeBytes = obj.optLong("sizeBytes"),
                        modifiedAt = obj.optLong("modifiedAt"),
                    )
                    add(Entry(item, obj.optLong("openedAt")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.item.id)
                    .put("name", entry.item.name)
                    .put("fileName", entry.item.fileName)
                    .put("sizeLabel", entry.item.sizeLabel)
                    .put("dateLabel", entry.item.dateLabel)
                    .put("type", entry.item.type.name)
                    .put("uri", entry.item.uri)
                    .put("sizeBytes", entry.item.sizeBytes)
                    .put("modifiedAt", entry.item.modifiedAt)
                    .put("openedAt", entry.openedAt),
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private data class Entry(
        val item: CamFileItem,
        val openedAt: Long,
    )

    companion object {
        private const val PREFS = "cam_recents"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 50
    }
}
