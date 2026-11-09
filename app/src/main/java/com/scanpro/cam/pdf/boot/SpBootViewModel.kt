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

    private var loadingJob: Job? = null
    private var totalProgress = DEFAULT_BOOT_MS

    /** Progress bar advances over [durationMs]. Navigation is not driven by this animation. */
    fun runBootProgress(durationMs: Long = DEFAULT_BOOT_MS) {
        if (loadingJob?.isActive == true) return
        val total = durationMs.coerceAtLeast(1L)
        totalProgress = total
        loadingJob = viewModelScope.launch {
            val steps = 60
            val stepDelay = total / steps
            for (step in 1..steps) {
                delay(stepDelay)
                _progress.value = ((step * total) / steps).toInt().coerceAtLeast(1)
            }
            _progress.value = total.toInt()
        }
    }

    /** PDF26 `fillRemainingProgress`：用 [durationMs] 从当前值补满进度条，返回时已完成。 */
    suspend fun finishBootProgress(durationMs: Long = 500L) {
        loadingJob?.cancel()
        val from = _progress.value
        val to = totalProgress.toInt()
        if (from >= to) {
            _progress.value = to
            delay(durationMs.coerceAtLeast(0L))
            return
        }
        val steps = 10
        val stepDelay = (durationMs / steps).coerceAtLeast(1L)
        val delta = to - from
        for (step in 1..steps) {
            delay(stepDelay)
            _progress.value = from + (delta * step / steps)
        }
        _progress.value = to
    }

    companion object {
        const val DEFAULT_BOOT_MS = 10_000L
        const val DEFAULT_BOOT_MIN_MS = 3_000L
    }
}
