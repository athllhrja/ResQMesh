package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.db.seen.SeenFrameDao
import com.resqmesh.data.db.seen.SeenFrameEntity
import com.resqmesh.data.db.seen.SeenMessageDao
import com.resqmesh.data.db.seen.SeenMessageEntity
import com.resqmesh.domain.model.MessageId

sealed interface FrameVerdict {
    /** Frame ini belum pernah diterima pada node ini. */
    data object Fresh : FrameVerdict

    /** Frame identik sudah pernah diterima; pengulangan Advertising yang wajar. */
    data object Repeated : FrameVerdict
}

/**
 * Dua level dedupe plus policy penerusan:
 *
 * 1. **Frame** — kunci (messageId, fragIndex). Inilah yang mencegah frame yang
 *    sama di-advertise berulang kali. Fragmen berbeda dari pesan yang sama
 *    tetap dianggap baru, sehingga re-assembly tidak pernah tersangkut.
 * 2. **Pesan** — `SeenMessageEntity.forwardCount` membatasi berapa kali satu
 *    pesan boleh diteruskan.
 *
 * Batasnya berbeda menurut jenis. Chat biasa memakai bias "maju ke hop terkecil
 * saja" supaya tidak membanjiri duty cycle advertising. SOS memakai batas 1
 * tanpa bias hop: setiap node meneruskan sekali, lalu menyingkir. Tanpa bias
 * hop, SOS tetap menjangkau node yang hanya bisa dicapai lewat jalur panjang,
 * karena keselamatan lebih penting daripada hemat advertisement.
 *
 * Semua pemeriksaan memakai INSERT ... IGNORE sehingga tetap atomik ketika dua
 * frame tiba bersamaan pada dispatcher berbeda.
 */
class DuplicateGuard(
    private val seenDao: SeenMessageDao,
    private val seenFrameDao: SeenFrameDao,
    private val clock: TimeProvider,
) {

    suspend fun registerFrame(id: MessageId, fragIndex: Int, hop: Int): FrameVerdict {
        val now = clock.now()
        val rowId = seenFrameDao.tryInsert(
            SeenFrameEntity(
                messageKey = id.value,
                fragIndex = fragIndex,
                hopCount = hop,
                firstSeenAt = now,
                expiresAt = now + MeshConfig.SEEN_RETENTION_MS,
            ),
        )
        return if (rowId != -1L) FrameVerdict.Fresh else FrameVerdict.Repeated
    }

    /** Mendaftarkan pesan saat pertama kali diterima; dipakai untuk bookkeeping forward. */
    suspend fun trackMessage(id: MessageId, hop: Int, isSos: Boolean = false) {
        val now = clock.now()
        seenDao.tryInsert(
            SeenMessageEntity(
                messageKey = id.value,
                originNodeId = id.origin.value,
                msgSeq = id.seq,
                firstSeenAt = now,
                expiresAt = now + MeshConfig.SEEN_RETENTION_MS,
                hopCount = hop,
                forwardCount = 0,
                isSos = isSos,
            ),
        )
    }

    /**
     * Dipanggil setiap kali pesan benar-benar diteruskan. Hop terbaik
     * (terkecil) dicatat supaya node berikutnya bisa deciding untuk maju atau
     * menyingkirkan diri.
     */
    suspend fun noteForward(id: MessageId, hop: Int) {
        seenDao.incrementForwardCount(id.value)
        val best = seenDao.find(id.value)?.hopCount ?: Int.MAX_VALUE
        if (hop < best) {
            seenDao.lowerHopIfBetter(id.value, hop)
        }
    }

    /**
     * Aturan "maju" untuk pesan biasa: pesan hanya boleh diteruskan ulang kalau
     * frame yang masuk berasal dari jalur lebih pendek daripada jalur yang sudah
     * pernah dipakai. `forwardCount` menjadi batas keras supaya satu pesan tidak
     * berputar tanpa henti.
     *
     * Untuk SOS aturannya dibalik: begitu satu node meneruskan, node itu selesai.
     * Bias hop sengaja dimatikan karena node yang hanya bisa dicapai lewat jalur
     * panjang justru yang paling butuh diberi tahu.
     */
    suspend fun shouldSuppress(id: MessageId, incomingHop: Int): Boolean {
        val row = seenDao.find(id.value) ?: return false
        if (row.isSos) {
            return row.forwardCount >= MeshConfig.SOS_FLOOD_FORWARD_LIMIT
        }
        if (row.forwardCount >= MeshConfig.NORMAL_FORWARD_LIMIT) return true
        if (row.forwardCount == 0) return false
        return incomingHop >= row.hopCount
    }

    suspend fun forwardCountOf(id: MessageId): Int =
        seenDao.find(id.value)?.forwardCount ?: 0

    /** Membersihkan catatan frame maupun pesan yang sudah melewati retensi. */
    suspend fun pruneExpired(): Int {
        val now = clock.now()
        return seenDao.purgeExpired(now) + seenFrameDao.purgeExpired(now)
    }
}
