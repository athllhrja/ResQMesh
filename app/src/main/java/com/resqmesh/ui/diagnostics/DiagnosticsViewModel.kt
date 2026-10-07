package com.resqmesh.ui.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resqmesh.diagnostics.DiagnosticsCollector
import com.resqmesh.diagnostics.DiagnosticsInfo
import com.resqmesh.domain.MeshRepository
import com.resqmesh.service.MeshService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    private val repository: MeshRepository,
) : ViewModel() {

    private val _info = MutableStateFlow<DiagnosticsInfo?>(null)
    val info: StateFlow<DiagnosticsInfo?> = _info.asStateFlow()

    fun refresh(context: Context) {
        viewModelScope.launch {
            val meshState = repository.observeMeshState().first()
            val isServiceRunning = DiagnosticsCollector.isServiceRunning(context, MeshService::class.java)
            val collected = DiagnosticsCollector.collect(
                context = context,
                meshPhase = meshState.phase.name,
                meshLastError = meshState.lastError,
                isMeshServiceRunning = isServiceRunning,
            )
            _info.value = collected
        }
    }

    companion object {
        fun factory(repository: MeshRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DiagnosticsViewModel(repository) as T
            }
    }
}
