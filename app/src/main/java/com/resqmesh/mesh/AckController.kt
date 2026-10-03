package com.resqmesh.mesh

import com.resqmesh.core.TimeProvider
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MsgFlag
import com.resqmesh.domain.model.NodeId

/**
 * Controller khusus untuk penanganan dan propagasi ACK (Requirement F10).
 *
 * Fungsi utama:
 * - Membuat paket ACK yang merujuk ke message_id SOS tertentu.
 * - Broadcast dan propagasi (reverse relay) paket ACK melalui jaringan mesh.
 * - Meng-update status SOS di database lokal menjadi DELIVERED (ACKED) saat ACK diterima pengirim asli.
 * - Menegaskan bahwa ACK adalah konfirmasi penerimaan sinyal darurat secara elektronik,
 *   bukan konfirmasi penyelesaian penyelamatan fisik.
 */
class AckController(
    private val selfId: NodeId,
    private val ackTracker: AckTracker,
    private val ttlPolicy: TtlPolicy,
    private val clock: TimeProvider,
) {

    /**
     * Membuat frame ACK baru untuk merespons/mengonfirmasi penerimaan SOS [targetMessageId].
     */
    fun createAckFrame(targetMessageId: MessageId, ttl: Int): MeshFrame {
        return MeshFrame(
            messageId = targetMessageId,
            destination = NodeId.BROADCAST_ID,
            ttl = ttl,
            hopCount = 0,
            flags = MsgFlag.IS_ACK or MsgFlag.ACK_REQUESTED,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = 0,
            payloadChunk = ByteArray(0),
        )
    }

    /**
     * Menangani penerimaan frame ACK di node perantara atau node pengirim asli.
     * Mengembalikan true jika ACK milik sendiri dan sukses di-handle (status -> DELIVERED),
     * atau frame yang siap di-forward (propagasi reverse relay).
     */
    suspend fun handleIncomingAck(frame: MeshFrame): AckHandlingResult {
        if (!frame.isAck) return AckHandlingResult.NotAnAck

        // Jika ACK ditujukan ke pesan milik kita sendiri (original sender)
        if (frame.messageId.origin == selfId) {
            ackTracker.onAck(frame.messageId)
            return AckHandlingResult.DeliveredAndConsumed
        }

        // Propagasi / reverse relay oleh node perantara jika TTL masih valid
        if (!ttlPolicy.shouldForward(frame.ttl, frame.hopCount, frame.inferredInitialTtl())) {
            return AckHandlingResult.Expired
        }

        val nextTtl = ttlPolicy.nextTtl(frame.ttl)
        val nextHop = ttlPolicy.nextHop(frame.hopCount)
        val forwardedAck = frame.forwarded(nextTtl = nextTtl, nextHop = nextHop)

        return AckHandlingResult.Forward(forwardedAck)
    }
}

sealed interface AckHandlingResult {
    data object NotAnAck : AckHandlingResult
    data object DeliveredAndConsumed : AckHandlingResult
    data object Expired : AckHandlingResult
    data class Forward(val frame: MeshFrame) : AckHandlingResult
}
