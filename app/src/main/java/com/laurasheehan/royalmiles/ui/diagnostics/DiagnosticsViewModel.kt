package com.laurasheehan.royalmiles.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laurasheehan.royalmiles.data.health.HealthDiagnostics
import com.laurasheehan.royalmiles.data.health.ProbeResult
import com.laurasheehan.royalmiles.data.health.WorkoutProvenance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val loading: Boolean = true,
    val probes: List<ProbeResult> = emptyList(),
    val workouts: List<WorkoutProvenance> = emptyList(),
)

class DiagnosticsViewModel(private val diagnostics: HealthDiagnostics) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DiagnosticsUiState(loading = true)
            val report = runCatching { diagnostics.run() }.getOrNull()
            _uiState.value = DiagnosticsUiState(
                loading = false,
                probes = report?.probes.orEmpty(),
                workouts = report?.workouts.orEmpty(),
            )
        }
    }
}
