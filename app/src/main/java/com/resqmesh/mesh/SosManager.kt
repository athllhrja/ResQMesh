package com.resqmesh.mesh

import android.content.Context
import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.location.LocationProvider
import com.resqmesh.data.prefs.NodeIdentityStore
import com.resqmesh.domain.MeshRepository
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manager untuk pembuatan pesan SOS (Requirement F4).
 * Mengintegrasikan:
 * - F1: Node ID / Sender ID dari [NodeIdentityStore]
 * - F3: Snapshot lokasi satu kali dari [LocationProvider]
 * - F4: Unik message_id, status awal, penyimpanan/persistensi lokal, dan pencegahan duplikasi (debounce).
 */
class SosManager(
    private val context: Context,
    private val repository: MeshRepository,
    private val identityStore: NodeIdentityStore,
    private val locationProvider: LocationProvider,
    private val clock: TimeProvider,
) {
    private val mutex = Mutex()
    private var lastSosTimestamp = 0L
    private val debounceIntervalMs = 2_000L // 2 detik debounce untuk cegah spam/duplicate SOS

    val senderId: NodeId
        get() = identityStore.nodeId

    /**
     * Membuat dan mengirim SOS baru dengan validasi debounce,
     * pengambilan snapshot lokasi F3, identitas F1, pembuatan message_id unik,
     * dan penyimpanan lokal.
     */
    suspend fun triggerSos(
        kind: SosKind = SosKind.TRAPPED,
        text: String = "",
        victimCount: Int = 1,
        hazards: Int = 0,
        ttl: Int = MeshConfig.SOS_DEFAULT_TTL,
    ): Result<MessageId> = mutex.withLock {
        val now = clock.now()
        if (now - lastSosTimestamp < debounceIntervalMs) {
            return Result.failure(IllegalStateException("SOS trigger dibounce (terlalu cepat)"))
        }
        lastSosTimestamp = now

        // 1. Ambil snapshot lokasi satu kali dari F3 (dengan safe default jika GPS/izin gagal)
        val fix = locationProvider.fetchSnapshot() ?: LocationFix(
            point = LatLon(0.0, 0.0),
            accuracyMeters = null,
            ageSeconds = 0,
            batteryPct = null
        )

        // 2. Pastikan unique message_id dan sender_id (F1) siap
        identityStore.nextMessageId()

        // 3. Simpan dan submit SOS ke repository / database lokal agar tersimpan
        return runCatching {
            repository.submitSos(
                kind = kind,
                fix = fix,
                victimCount = victimCount,
                hazards = hazards,
                text = text,
                ttl = ttl,
            )
        }
    }
}
