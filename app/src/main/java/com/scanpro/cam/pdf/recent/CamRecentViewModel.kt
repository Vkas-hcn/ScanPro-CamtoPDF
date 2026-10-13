package com.scanpro.cam.pdf.recent

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.cam.pdf.made.CamRenameResult
import com.scanpro.cam.pdf.data.CamMadeDocs
import com.scanpro.cam.pdf.data.CamFavStore
import com.scanpro.cam.pdf.data.CamRecentStore
import com.scanpro.cam.pdf.home.CamListState
import com.scanpro.cam.pdf.home.CamFileItem
import com.scanpro.cam.pdf.util.CamFileOps
import com.scanpro.cam.pdf.util.CamRenameOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CamRecentUiState(
    val isLoading: Boolean = false,
    val files: List<CamFileItem> = emptyList(),
    val favoritePaths: Set<String> = emptySet(),
) {
    val contentState: CamListState
        get() = if (files.isEmpty()) {
            CamListState.Empty
        } else {
            CamListState.ListFiles(files)
        }
}

class CamRecentViewModel(application: Application) : AndroidViewModel(application) {

    private val recentStore = CamRecentStore(application)
    private val favoritesStore = CamFavStore(application)

    private val _uiState = MutableStateFlow(CamRecentUiState())
    val uiState: StateFlow<CamRecentUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val files = withContext(Dispatchers.IO) {
                recentStore.list()
            }
            val favorites = files
                .map { it.id }
                .filter { favoritesStore.isFavorite(it) }
                .toSet()
            _uiState.update {
                it.copy(files = files, favoritePaths = favorites, isLoading = false)
            }
        }
    }

    fun isFavorite(item: CamFileItem): Boolean {
        return favoritesStore.isFavorite(item.id) || item.id in _uiState.value.favoritePaths
    }

    fun toggleFavorite(item: CamFileItem): Boolean {
        val added = favoritesStore.toggle(item)
        _uiState.update { state ->
            val next = state.favoritePaths.toMutableSet()
            if (added) next.add(item.id) else next.remove(item.id)
            state.copy(favoritePaths = next)
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
        // Remove from recent even if physical delete fails (e.g. no permission)
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
        val byId = File(item.id)
        return byId.takeIf { it.isFile }
    }
}
