package com.resqmesh.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resqmesh.core.MeshConfig
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
import kotlinx.coroutines.launch

enum class LocationNote {
    /** Fix dipakai, koordinat presisi siap dikirim. */
    NONE,

    /** Izin lokasi belum diberikan. SOS tetap bisa dikirim tanpa pin. */
    PERMISSION_MISSING,

    /** GPS dimatikan di pengaturan perangkat. */
    PROVIDER_DISABLED,

    /**
     * Ada izin tapi tidak ada fix yang cukup segar. Biasanya terjadi setelah lama
     * tidak memakai GPS, yang butuh beberapa detik untuk fix pertama.
     */
    NO_FRESH_FIX,
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
    val ttl: Int = MeshConfig.DEFAULT_TTL,
    val isSosDialogVisible: Boolean = false,
    val error: String? = null,
    val sos: SosComposer = SosComposer(),
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

    private val header = combine(
        repository.observeSelf(),
        repository.observeMeshState(),
        repository.observePendingForwardCount(),
    ) { self, mesh, pending ->
        Triple(self, mesh, pending)
    }

    val uiState: StateFlow<HomeUiState> =
        combine(header, ttl, sosDialog, error, sos) { header, ttlValue, dialog, errorValue, composer ->
            val (self, mesh, pending) = header
            HomeUiState(
                self = self,
                neighborCount = mesh.neighborCount,
                pendingCount = pending,
                phase = mesh.phase,
                ttl = ttlValue,
                isSosDialogVisible = dialog,
                error = errorValue,
                sos = composer,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun setTtl(value: Int) {
        ttl.value = value.coerceIn(1, MeshConfig.MAX_TTL)
    }

    fun showSosDialog() {
        sosDialog.value = true
        resolveLocation()
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

    /** Minta ulang lokasi, misalnya setelah operator menyalakan GPS. */
    fun resolveLocation() {
        if (sos.value.isResolvingLocation) return
        sos.update { it.copy(isResolvingLocation = true) }
        viewModelScope.launch {
            val note = when (val result = locationSource.currentFix(readBattery())) {
                is LocationResult.Ready -> {
                    sos.update { it.copy(fix = result.fix, note = LocationNote.NONE) }
                    return@launch
                }

                LocationResult.PermissionMissing -> LocationNote.PERMISSION_MISSING
                LocationResult.ProviderDisabled -> LocationNote.PROVIDER_DISABLED
                LocationResult.NoRecentFix -> LocationNote.NO_FRESH_FIX
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
