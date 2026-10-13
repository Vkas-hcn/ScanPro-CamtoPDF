package com.scanpro.cam.pdf.made

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.cam.pdf.data.CamMadeDocs
import com.scanpro.cam.pdf.data.CamFavStore
import com.scanpro.cam.pdf.data.CamRecentStore
import com.scanpro.cam.pdf.home.CamListState
import com.scanpro.cam.pdf.home.CamFileItem
import com.scanpro.cam.pdf.util.CamFileOps
import com.scanpro.cam.pdf.util.CamRenameOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CamMadeUiState(
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

class CamMadeViewModel(application: Application) : AndroidViewModel(application) {

    private val favoritesStore = CamFavStore(application)
    private val recentStore = CamRecentStore(application)

    private val _uiState = MutableStateFlow(CamMadeUiState())
    val uiState: StateFlow<CamMadeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val files = runCatching { CamMadeDocs.list(getApplication()) }
                .getOrDefault(emptyList())
            val favorites = files
                .map { it.id }
                .filter { favoritesStore.isFavorite(it) }
                .toSet()
            _uiState.update {
                it.copy(
                    files = files,
                    favoritePaths = favorites,
                    isLoading = false,
                )
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
        val file = CamMadeDocs.resolveFile(getApplication(), item)
            ?: return CamRenameResult.Failed
        return when (val outcome = CamFileOps.rename(file, newName)) {
            is CamRenameOutcome.Success -> {
                val updated = CamMadeDocs.toItem(outcome.file)
                if (updated != null) {
                    favoritesStore.replace(item.id, updated)
                    recentStore.replace(item.id, updated)
                } else {
                    favoritesStore.remove(item.id)
                    recentStore.remove(item.id)
                }
                refresh()
                CamRenameResult.Success
            }
            CamRenameOutcome.Empty -> CamRenameResult.Empty
            CamRenameOutcome.Exists -> CamRenameResult.Exists
            CamRenameOutcome.Failed -> CamRenameResult.Failed
        }
    }

    fun delete(item: CamFileItem): Boolean {
        val file = CamMadeDocs.resolveFile(getApplication(), item) ?: return false
        val ok = CamFileOps.delete(file)
        if (ok) {
            favoritesStore.remove(item.id)
            recentStore.remove(item.id)
            refresh()
        }
        return ok
    }

    fun resolveFile(item: CamFileItem): File? {
        return CamMadeDocs.resolveFile(getApplication(), item)
    }
}

enum class CamRenameResult {
    Success, Empty, Exists, Failed
}
