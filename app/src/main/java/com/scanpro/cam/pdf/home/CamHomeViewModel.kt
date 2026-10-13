package com.scanpro.cam.pdf.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.cam.pdf.made.CamRenameResult
import com.scanpro.cam.pdf.data.CamMadeDocs
import com.scanpro.cam.pdf.data.CamStorageIndexer
import com.scanpro.cam.pdf.data.CamFavStore
import com.scanpro.cam.pdf.data.CamRecentStore
import com.scanpro.cam.pdf.util.CamFileOps
import com.scanpro.cam.pdf.util.CamRenameOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CamHomeUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val selectedType: FileType = FileType.PDF,
    val query: String = "",
    val allFiles: List<CamFileItem> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
) {
    val contentState: CamListState
        get() {
            if (!hasPermission) return CamListState.NeedPermission
            val filtered = allFiles
                .filter { it.type == selectedType }
                .filter {
                    query.isBlank() || it.name.contains(query, ignoreCase = true)
                }
            return if (filtered.isEmpty()) {
                CamListState.Empty
            } else {
                CamListState.ListFiles(filtered)
            }
        }
}

class CamHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = CamStorageIndexer(application)
    private val favoritesStore = CamFavStore(application)
    private val recentStore = CamRecentStore(application)

    private val _uiState = MutableStateFlow(CamHomeUiState())
    val uiState: StateFlow<CamHomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            loadJob?.cancel()
            _uiState.update {
                it.copy(hasPermission = false, isLoading = false, allFiles = emptyList())
            }
            return
        }
        _uiState.update { it.copy(hasPermission = true) }
        loadFiles()
    }

    fun selectType(type: FileType) {
        _uiState.update { it.copy(selectedType = type) }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun refresh() {
        if (_uiState.value.hasPermission) {
            loadFiles()
        }
    }

    fun isFavorite(item: CamFileItem): Boolean {
        return favoritesStore.isFavorite(item.id) || item.id in _uiState.value.favoriteIds
    }

    fun toggleFavorite(item: CamFileItem): Boolean {
        val added = favoritesStore.toggle(item)
        _uiState.update { state ->
            val next = state.favoriteIds.toMutableSet()
            if (added) next.add(item.id) else next.remove(item.id)
            state.copy(favoriteIds = next)
        }
        return added
    }

    fun rename(item: CamFileItem, newName: String): CamRenameResult {
        val file = resolveLocalFile(item) ?: return CamRenameResult.Failed
        return when (val outcome = CamFileOps.rename(file, newName)) {
            is CamRenameOutcome.Success -> {
                val updated = CamMadeDocs.toItem(outcome.file) ?: return CamRenameResult.Failed
                favoritesStore.replace(item.id, updated)
                recentStore.replace(item.id, updated)
                refresh()
                CamRenameResult.Success
            }
            CamRenameOutcome.Empty -> CamRenameResult.Empty
            CamRenameOutcome.Exists -> CamRenameResult.Exists
            CamRenameOutcome.Failed -> CamRenameResult.Failed
        }
    }

    fun delete(item: CamFileItem): Boolean {
        when (Uri.parse(item.uri).scheme) {
            "file" -> resolveLocalFile(item)?.let { CamFileOps.delete(it) }
            "content" -> {
                runCatching {
                    getApplication<Application>().contentResolver
                        .delete(Uri.parse(item.uri), null, null)
                }
            }
            else -> resolveLocalFile(item)?.let { CamFileOps.delete(it) }
        }
        recentStore.remove(item.id)
        favoritesStore.remove(item.id)
        refresh()
        return true
    }

    fun share(item: CamFileItem): Boolean {
        val context = getApplication<Application>()
        resolveLocalFile(item)?.let { file ->
            return CamFileOps.share(context, file, item.type)
        }
        return CamFileOps.shareUri(context, item.uri, item.type, item.fileName)
    }

    private fun resolveLocalFile(item: CamFileItem): File? {
        CamMadeDocs.resolveFile(getApplication(), item)?.let { return it }
        val uri = Uri.parse(item.uri)
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            return File(path).takeIf { it.isFile }
        }
        return File(item.id).takeIf { it.isFile }
    }

    private fun loadFiles() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val files = runCatching { scanner.scanDocuments() }
                .getOrDefault(emptyList())
            val favorites = files
                .map { it.id }
                .filter { favoritesStore.isFavorite(it) }
                .toSet()
            _uiState.update {
                it.copy(allFiles = files, favoriteIds = favorites, isLoading = false)
            }
        }
    }
}
