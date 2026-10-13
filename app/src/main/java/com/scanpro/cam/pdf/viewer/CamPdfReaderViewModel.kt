package com.scanpro.cam.pdf.viewer

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CamPdfReaderUiState(
    val title: String = "",
    val isLoading: Boolean = true,
    val pageCount: Int = 0,
    val openFailed: Boolean = false,
)

class CamPdfReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val loader = CamPdfLoader(application)

    private val _uiState = MutableStateFlow(CamPdfReaderUiState())
    val uiState: StateFlow<CamPdfReaderUiState> = _uiState.asStateFlow()

    fun open(uri: String, title: String) {
        _uiState.value = CamPdfReaderUiState(title = title, isLoading = true)
        viewModelScope.launch {
            when (val result = loader.open(uri)) {
                is CamPdfLoader.Result.Ok -> {
                    _uiState.value = CamPdfReaderUiState(
                        title = title,
                        isLoading = false,
                        pageCount = result.pageCount,
                    )
                }
                CamPdfLoader.Result.Error -> {
                    _uiState.value = CamPdfReaderUiState(
                        title = title,
                        isLoading = false,
                        openFailed = true,
                    )
                }
            }
        }
    }

    fun renderPage(index: Int, widthPx: Int): Bitmap? = loader.renderPage(index, widthPx)

    override fun onCleared() {
        loader.close()
        super.onCleared()
    }
}
