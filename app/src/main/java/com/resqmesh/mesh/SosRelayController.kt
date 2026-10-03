package com.resqmesh.mesh

import com.resqmesh.core.TimeProvider
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.NodeId

/**
 * Controller untuk penanganan SOS Relay, TTL Enforcement (F7), dan Hop Count Tracking (F8).
 */
class SosRelayController(
    private val selfId: NodeId,
    private val ttlPolicy: TtlPolicy,
    private val clock: TimeProvider,
) {

    /**
     * Mempersiapkan paket untuk di-relay dengan aturan F7 dan F8:
     * - TTL (F7): Divalidasi (`ttl > 0`), berkurang 1 (`ttl - 1`) setiap hop. Jika `ttl <= 0`, dibuang (expired).
     * - Hop Count (F8): Bertambah 1 (`hopCount + 1`) setiap hop, mencerminkan jarak dari sumber original.
     * - Echo suppression / split horizon: Mencegah relay kembali ke pengirim asli (self origin).
     */
    fun prepareRelay(frame: MeshFrame, peer: Peer): MeshFrame? {
        // Echo suppression / split horizon: Jangan relay paket dari diri sendiri
        if (frame.messageId.origin == selfId) return null

        val initialTtl = frame.inferredInitialTtl()

        // F7: Validasi apakah TTL masih valid (ttl > 0 dan belum melampaui initialTtl)
        if (frame.ttl <= 0 || !ttlPolicy.shouldForward(frame.ttl, frame.hopCount, initialTtl)) {
            return null
        }

        // F7: TTL berkurang 1 (ttl - 1)
        val nextTtl = ttlPolicy.nextTtl(frame.ttl)
        // F8: Hop Count bertambah 1 (hopCount + 1)
        val nextHop = ttlPolicy.nextHop(frame.hopCount)

        // Pastikan message_id dan sender_id (origin) tidak berubah, hanya TTL dan Hop yang diupdate
        return frame.forwarded(nextTtl = nextTtl, nextHop = nextHop)
    }
}
