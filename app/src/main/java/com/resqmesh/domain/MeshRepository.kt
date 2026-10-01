package com.resqmesh.domain

import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MeshState
import com.resqmesh.domain.model.Message
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.Node
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosIncident
import com.resqmesh.domain.model.SosKind
import kotlinx.coroutines.flow.Flow

interface MeshRepository {
    fun observeSelf(): Flow<Node>
    fun observeNeighbors(): Flow<List<Node>>
    fun observeHistory(limit: Int = 200): Flow<List<Message>>
    fun observeConversation(peerId: NodeId, limit: Int = 100): Flow<List<Message>>
    fun observeMeshState(): Flow<MeshState>
    fun observePendingForwardCount(): Flow<Int>

    /** Insiden darurat, satu insiden per kartu walau dikirim sebagai dua pesan. */
    fun observeSosIncidents(limit: Int = 100): Flow<List<SosIncident>>

    suspend fun sendTo(
        destination: NodeId,
        text: String,
        ttl: Int,
        isSos: Boolean = false,
    ): MessageId

    /**
     * Darurat tanpa koordinat, untuk kasus GPS mati atau operator tidak
     * memberi izin lokasi. Tetap menyiarkan lokasi bila fix tersedia, jadi
     * pemanggil sebaiknya lewat [submitSos] saja.
     */
    suspend fun broadcastSos(
        text: String,
        ttl: Int = MeshConfig.SOS_DEFAULT_TTL,
    ): MessageId

    /**
     * Mengirim insiden darurat lengkap dengan titik koordinat.
     *
     * Mengembalikan `MessageId` pesan SOS_LOC, yang sekaligus menjadi
     * `incidentId` untuk menggabungkan detail yang menyusul.
     */
    suspend fun submitSos(
        kind: SosKind,
        fix: LocationFix,
        victimCount: Int,
        hazards: Int,
        text: String,
        ttl: Int = MeshConfig.SOS_DEFAULT_TTL,
    ): MessageId

    suspend fun rebroadcastPending(): Int
    suspend fun prune(): Int
}
