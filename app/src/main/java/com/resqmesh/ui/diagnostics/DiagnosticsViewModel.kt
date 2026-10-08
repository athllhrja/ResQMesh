package com.resqmesh.ui.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resqmesh.data.location.LocationSource
import com.resqmesh.diagnostics.DiagnosticsCollector
import com.resqmesh.diagnostics.DiagnosticsInfo
import com.resqmesh.domain.MeshRepository
import com.resqmesh.mesh.ble.BleStats
import com.resqmesh.mesh.ble.BleTestInterpreter
import com.resqmesh.service.MeshService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    private val repository: MeshRepository,
    private val locationSource: LocationSource,
) : ViewModel() {

    private val _info = MutableStateFlow<DiagnosticsInfo?>(null)
    val info: StateFlow<DiagnosticsInfo?> = _info.asStateFlow()

    private val _isTestRunning = MutableStateFlow(false)
    val isTestRunning: StateFlow<Boolean> = _isTestRunning.asStateFlow()

    private val _testCountdownSeconds = MutableStateFlow(30)
    val testCountdownSeconds: StateFlow<Int> = _testCountdownSeconds.asStateFlow()

    private val _testInterpretationResult = MutableStateFlow<String?>(null)
    val testInterpretationResult: StateFlow<String?> = _testInterpretationResult.asStateFlow()

    private var testJob: Job? = null

    fun refresh(context: Context) {
        viewModelScope.launch {
            val meshState = repository.observeMeshState().first()
            val isServiceRunning = DiagnosticsCollector.isServiceRunning(context, MeshService::class.java)
            val collected = DiagnosticsCollector.collect(
                context = context,
                locationSource = locationSource,
                meshPhase = meshState.phase.name,
                meshLastError = meshState.lastError,
                isMeshServiceRunning = isServiceRunning,
            )
            _info.value = collected
        }
    }

    fun resetStats(context: Context) {
        BleStats.reset()
        _testInterpretationResult.value = null
        refresh(context)
    }

    fun start30SecBleTest(context: Context) {
        if (_isTestRunning.value) return
        _isTestRunning.value = true
        _testCountdownSeconds.value = 30
        _testInterpretationResult.value = null

        // Pastikan service/mesh berjalan saat pengujian
        MeshService.start(context)

        testJob = viewModelScope.launch {
            for (sec in 30 downTo 1) {
                _testCountdownSeconds.value = sec
                refresh(context)
                delay(1000L)
            }
            _testCountdownSeconds.value = 0
            refresh(context)

            val snapshot = BleStats.snapshot()
            val interpretation = BleTestInterpreter.interpretTestResult(snapshot)
            _testInterpretationResult.value = interpretation
            _isTestRunning.value = false
        }
    }

    companion object {
        fun factory(
            repository: MeshRepository,
            locationSource: LocationSource,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DiagnosticsViewModel(repository, locationSource) as T
            }
    }
}
