package com.resqmesh.ui.experiment

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.MeshRepository
import com.resqmesh.domain.model.SosKind
import com.resqmesh.experiment.ExperimentLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ExperimentUiState(
    val runId: String = "RUN_01",
    val scenario: String = "Line_5_Nodes",
    val countText: String = "5",
    val intervalText: String = "10",
    val isRunning: Boolean = false,
    val progress: String = "",
    val files: List<File> = emptyList(),
    val error: String? = null,
)

class ExperimentViewModel(
    private val repository: MeshRepository,
    private val logger: ExperimentLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExperimentUiState())
    val uiState: StateFlow<ExperimentUiState> = _uiState.asStateFlow()

    private var runnerJob: Job? = null

    init {
        refreshFiles()
    }

    fun setRunId(value: String) {
        _uiState.update { it.copy(runId = value) }
    }

    fun setScenario(value: String) {
        _uiState.update { it.copy(scenario = value) }
    }

    fun setCountText(value: String) {
        _uiState.update { it.copy(countText = value) }
    }

    fun setIntervalText(value: String) {
        _uiState.update { it.copy(intervalText = value) }
    }

    fun refreshFiles() {
        _uiState.update { it.copy(files = logger.getLogFiles()) }
    }

    fun startExperiment() {
        if (_uiState.value.isRunning) return

        val state = _uiState.value
        val count = state.countText.toIntOrNull() ?: 5
        val intervalSec = state.intervalText.toLongOrNull() ?: 10

        logger.activeRunId = state.runId
        logger.activeScenario = state.scenario

        _uiState.update {
            it.copy(
                isRunning = true,
                progress = "Memulai eksperimen $count SOS...",
                error = null,
            )
        }

        runnerJob = viewModelScope.launch {
            runCatching {
                for (i in 1..count) {
                    if (!_uiState.value.isRunning) break
                    _uiState.update { it.copy(progress = "Mengirim SOS $i dari $count...") }

                    repository.broadcastSos(
                        text = "Uji Eksperimen #$i [${state.runId}]",
                        ttl = MeshConfig.SOS_DEFAULT_TTL,
                    )

                    if (i < count) {
                        delay(intervalSec * 1000L)
                    }
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        progress = "Eksperimen $count SOS selesai!",
                    )
                }
                refreshFiles()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        error = err.message,
                        progress = "",
                    )
                }
            }
        }
    }

    fun stopExperiment() {
        runnerJob?.cancel()
        runnerJob = null
        _uiState.update { it.copy(isRunning = false, progress = "Eksperimen dihentikan.") }
        refreshFiles()
    }

    fun shareFile(context: Context, file: File) {
        runCatching {
            val uri = logger.getShareUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Ekspor Log CSV Eksperimen"))
        }
    }

    fun clearLogs() {
        logger.clearLogs()
        refreshFiles()
    }

    companion object {
        fun factory(
            repository: MeshRepository,
            logger: ExperimentLogger,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExperimentViewModel(repository, logger) as T
            }
    }
}
