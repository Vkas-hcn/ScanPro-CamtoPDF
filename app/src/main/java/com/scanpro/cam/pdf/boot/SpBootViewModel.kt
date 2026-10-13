package com.scanpro.cam.pdf.boot

import androidx.lifecycle.viewModelScope
import com.scanpro.cam.pdf.base.SpViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpBootViewModel : SpViewModel() {

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private var loadingJob: Job? = null

    /** Progress bar advances over [durationMs]; emits finished when complete. */
    fun runBootProgress(durationMs: Long = DEFAULT_BOOT_MS) {
        if (loadingJob?.isActive == true || _finished.value) return
        val total = durationMs.coerceAtLeast(1L)
        loadingJob = viewModelScope.launch {
            val steps = 60
            val stepDelay = total / steps
            for (step in 1..steps) {
                delay(stepDelay)
                _progress.value = ((step * total) / steps).toInt().coerceAtLeast(1)
            }
            _progress.value = total.toInt()
            _finished.value = true
        }
    }

    companion object {
        const val DEFAULT_BOOT_MS = 10_000L
        const val DEFAULT_BOOT_MIN_MS = 3_000L
    }
}
