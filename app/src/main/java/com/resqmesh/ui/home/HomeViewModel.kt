package com.resqmesh.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resqmesh.core.MeshConfig
import com.resqmesh.crash.CrashReporter
import com.resqmesh.data.location.LocationResult
import com.resqmesh.data.location.LocationSource
import com.resqmesh.domain.MeshRepository
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MeshState
import com.resqmesh.domain.model.Node
import com.resqmesh.domain.model.SosKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class LocationNote {
    NONE,
    SEARCHING,
    PERMISSION_MISSING,
    APPROXIMATE_ONLY,
    PROVIDER_DISABLED,
    NO_FIX_AFTER_TIMEOUT,
}

/** Isi dialog SOS. */
data class SosComposer(
    val kind: SosKind = SosKind.MEDICAL,
    val hazards: Int = 0,
    val victimCount: Int = 0,
    val text: String = "",
    val fix: LocationFix? = null,
    val isResolvingLocation: Boolean = false,
    val note: LocationNote = LocationNote.NONE,
    val isSending: Boolean = false,
)

data class HomeUiState(
    val self: Node? = null,
    val neighborCount: Int = 0,
    val pendingCount: Int = 0,
    val phase: MeshState.Phase = MeshState.Phase.STOPPED,
    val isSosActive: Boolean = false,
    val isResponder: Boolean = false,
    val ttl: Int = MeshConfig.DEFAULT_TTL,
    val isSosDialogVisible: Boolean = false,
    val error: String? = null,
    val sos: SosComposer = SosComposer(),
    val isLocationDisabledOnLegacy: Boolean = false,
    val hasCrashReport: Boolean = false,
    val crashFile: File? = null,
) {
    val locationAvailable: Boolean get() = sos.fix != null
}

class HomeViewModel(
    private val repository: MeshRepository,
    private val locationSource: LocationSource,
    private val readBattery: suspend () -> Int?,
) : ViewModel() {

    private val ttl = MutableStateFlow(MeshConfig.DEFAULT_TTL)
    private val sosDialog = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val sos = MutableStateFlow(SosComposer())
    private val isResponderState = MutableStateFlow(repository.isResponder())
    private val hasCrashReport = MutableStateFlow(false)
    private val crashFile = MutableStateFlow<File?>(null)

    private val header = combine(
        repository.observeSelf(),
        repository.observeMeshState(),
        repository.observePendingForwardCount(),
        isResponderState,
    ) { self, mesh, pending, responder ->
        Tuple4(self, mesh, pending, responder)
    }

    val uiState: StateFlow<HomeUiState> =
        combine(header, ttl, sosDialog, error, sos, hasCrashReport, crashFile) { flows ->
            @Suppress("UNCHECKED_CAST")
            val headerTuple = flows[0] as Tuple4<Node?, MeshState, Int, Boolean>
            val ttlValue = flows[1] as Int
            val dialog = flows[2] as Boolean
            val errorValue = flows[3] as String?
            val composer = flows[4] as SosComposer
            val crashAvailable = flows[5] as Boolean
            val crashFileObj = flows[6] as File?

            val isLocationDisabledOnLegacy = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S && !locationSource.isProviderEnabled()
            HomeUiState(
                self = headerTuple.self,
                neighborCount = headerTuple.mesh.neighborCount,
                pendingCount = headerTuple.pending,
                phase = headerTuple.mesh.phase,
                isSosActive = headerTuple.mesh.isSosActive,
                isResponder = headerTuple.responder,
                ttl = ttlValue,
                isSosDialogVisible = dialog,
                error = errorValue,
                sos = composer,
                isLocationDisabledOnLegacy = isLocationDisabledOnLegacy,
                hasCrashReport = crashAvailable,
                crashFile = crashFileObj,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun startPassiveLocationUpdates() {
        runCatching {
            locationSource.startPassiveLocationUpdates { fix ->
                sos.update { it.copy(fix = fix, note = LocationNote.NONE) }
            }
        }
    }

    fun stopPassiveLocationUpdates() {
        runCatching {
            locationSource.stopPassiveLocationUpdates()
        }
    }

    override fun onCleared() {
        stopPassiveLocationUpdates()
        super.onCleared()
    }

    fun checkForCrashReports(context: Context) {
        val files = CrashReporter.listCrashFiles(context)
        if (files.isNotEmpty()) {
            crashFile.value = files.first()
            hasCrashReport.value = true
        } else {
            hasCrashReport.value = false
            crashFile.value = null
        }
    }

    fun clearCrashReports(context: Context) {
        CrashReporter.clearCrashFiles(context)
        hasCrashReport.value = false
        crashFile.value = null
    }

    fun toggleResponderMode() {
        val next = !isResponderState.value
        repository.setResponder(next)
        isResponderState.value = next
    }

    fun setTtl(value: Int) {
        ttl.value = value.coerceIn(1, MeshConfig.MAX_TTL)
    }

    fun showSosDialog() {
        sosDialog.value = true
        resolveLocation(forceRefresh = true)
    }

    fun dismissSosDialog() {
        sosDialog.value = false
        sos.update { it.copy(isResolvingLocation = false) }
    }

    fun selectSosKind(kind: SosKind) {
        sos.update { it.copy(kind = kind) }
    }

    fun toggleHazard(bit: Int) {
        sos.update { it.copy(hazards = it.hazards xor bit) }
    }

    fun changeVictimCount(delta: Int) {
        sos.update { it.copy(victimCount = (it.victimCount + delta).coerceIn(0, 255)) }
    }

    fun setSosText(text: String) {
        sos.update { it.copy(text = text) }
    }

    /** Minta aktif lokasi baru dengan [forceRefresh] = true. */
    fun resolveLocation(forceRefresh: Boolean = false) {
        if (sos.value.isResolvingLocation && !forceRefresh) return
        sos.update { it.copy(isResolvingLocation = true, note = LocationNote.SEARCHING) }
        viewModelScope.launch {
            val note = when (val result = locationSource.currentFix(readBattery(), forceRefresh = forceRefresh)) {
                is LocationResult.Ready -> {
                    sos.update { it.copy(fix = result.fix, note = LocationNote.NONE, isResolvingLocation = false) }
                    return@launch
                }

                LocationResult.Searching -> LocationNote.SEARCHING
                LocationResult.PermissionMissing -> LocationNote.PERMISSION_MISSING
                LocationResult.ApproximateOnly -> LocationNote.APPROXIMATE_ONLY
                LocationResult.ProviderDisabled -> LocationNote.PROVIDER_DISABLED
                LocationResult.NoRecentFix,
                LocationResult.NoFixAfterTimeout -> LocationNote.NO_FIX_AFTER_TIMEOUT
            }
            sos.update { it.copy(fix = null, note = note, isResolvingLocation = false) }
        }
    }

    /**
     * Mengirim insiden. Lokasi tidak pernah menghalangi pengiriman: kalau fix
     * tidak ada, SOS tetap dikirim sebagai teks supaya penolong tahu ada
     * darurat, hanya tanpa pin.
     */
    fun sendSos() {
        if (sos.value.isSending) return
        val form = sos.value
        sos.update { it.copy(isSending = true) }
        viewModelScope.launch {
            runCatching {
                val fix = form.fix
                if (fix != null) {
                    repository.submitSos(
                        kind = form.kind,
                        fix = fix,
                        victimCount = form.victimCount,
                        hazards = form.hazards,
                        text = form.text,
                        ttl = ttl.value,
                    )
                } else {
                    repository.broadcastSos(form.text.ifBlank { form.kind.label }, ttl.value)
                }
            }.onSuccess {
                sosDialog.value = false
                sos.value = SosComposer(kind = form.kind, isResolvingLocation = true)
                error.value = null
            }.onFailure {
                error.value = it.message
                sos.update { it.copy(isSending = false) }
            }
        }
    }

    fun cancelSos() {
        viewModelScope.launch {
            runCatching {
                repository.cancelSelfSos()
            }.onFailure {
                error.value = it.message
            }
        }
    }

    fun clearError() {
        error.value = null
    }

    companion object {
        fun factory(
            repository: MeshRepository,
            locationSource: LocationSource,
            readBattery: suspend () -> Int?,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(repository, locationSource, readBattery) as T
            }
    }
}

private data class Tuple4<A, B, C, D>(
    val self: A,
    val mesh: B,
    val pending: C,
    val responder: D,
)
