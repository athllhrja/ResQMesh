package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.data.db.message.MessageDao
import com.resqmesh.data.db.seen.SeenMessageDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manajer Store-and-Forward (Requirement F12):
 * - Menyimpan paket SOS yang belum sempat terkirim/terusan ke buffer sementara (Room DB status PENDING_FORWARD) saat tidak ada peer aktif.
 * - Memicu re-forward otomatis saat peer baru didiscover oleh BLE scanner (F2).
 * - Mematuhi aturan Duplicate Detection (F6) dan TTL (F7) agar tidak terjadi flood tak terbatas.
 * - Membersihkan antrean outbox setelah berhasil di-relay atau mencapai kedaluwarsa (retention policy).
 */
class StoreAndForwardQueue(
    private val messageDao: MessageDao,
    private val seenDao: SeenMessageDao,
    private val meshManager: MeshManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

    /**
     * Dipanggil saat event BLE peer baru ditemukan (F2).
     * Memicu pengiriman ulang paket SOS yang tertunda di antrean (store-and-forward flush).
     */
    fun onNewPeerDiscovered() {
        scope.launch {
            flushPendingOutbox()
        }
    }

    /**
     * Memproses antrean pending outbox (store-and-forward).
     */
    suspend fun flushPendingOutbox(): Int {
        val pending = messageDao.pendingForwards(limit = 16)
        var replayed = 0

        for (message in pending) {
            // F7: Cek TTL expired
            if (message.ttl <= 0) {
                messageDao.markExpired(message.messageKey)
                continue
            }

            // F6: Cek Duplicate Detection / forward limit
            val entry = seenDao.find(message.messageKey)
            val limit = if (message.isSos) {
                MeshConfig.SOS_FLOOD_FORWARD_LIMIT
            } else {
                MeshConfig.NORMAL_FORWARD_LIMIT
            }

            if (entry != null && entry.forwardCount >= limit) {
                messageDao.markExpired(message.messageKey)
                continue
            }

            // Re-broadcast / forward paket yang tertunda
            meshManager.replay(message)
            replayed++
        }

        return replayed
    }
}
