package com.scanpro.cam.pdf.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.cam.pdf.data.CamFavStore
import com.scanpro.cam.pdf.home.CamListState
import com.scanpro.cam.pdf.home.CamFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CamFavoritesUiState(
    val isLoading: Boolean = false,
    val files: List<CamFileItem> = emptyList(),
) {
    val contentState: CamListState
        get() = if (files.isEmpty()) {
            CamListState.Empty
        } else {
            CamListState.ListFiles(files)
        }
}

class CamFavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val favoritesStore = CamFavStore(application)

    private val _uiState = MutableStateFlow(CamFavoritesUiState())
    val uiState: StateFlow<CamFavoritesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val files = withContext(Dispatchers.IO) { favoritesStore.list() }
            _uiState.update { it.copy(files = files, isLoading = false) }
        }
    }

    fun removeFavorite(item: CamFileItem) {
        favoritesStore.remove(item.id)
        refresh()
    }
}
