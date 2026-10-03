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
 * Dua level dedupe plus policy penerusan (Requirement F6):
 *
 * 1. **Frame** — kunci (messageId, fragIndex). Inilah yang mencegah frame yang
 *    sama di-advertise berulang kali. Fragmen berbeda dari pesan yang sama
 *    tetap dianggap baru, sehingga re-assembly tidak pernah tersangkut.
 * 2. **Pesan** — `SeenMessageEntity` memantau message_id untuk mencegah duplikasi,
 *    penyimpanan rekaman ganda di DB, dan banjir relay (flood prevention).
 *
 * Menyediakan fungsi pengecekan cepat [isDuplicate] dan [markSeen],
 * serta pembersihan berkala [pruneExpired] berbasis waktu (time-based expiry).
 */
class DuplicateGuard(
    private val seenDao: SeenMessageDao,
    private val seenFrameDao: SeenFrameDao,
    private val clock: TimeProvider,
) {

    /**
     * Pengecekan cepat apakah message_id sudah pernah diterima/diproses sebelumnya (F6).
     */
    suspend fun isDuplicate(id: MessageId): Boolean {
        return seenDao.find(id.value) != null
    }

    /**
     * Menandai pesan sebagai telah dilihat/diproses secara cepat.
     * Mengembalikan true jika pesan baru (fresh), false jika duplikat (F6).
     */
    suspend fun markSeen(id: MessageId, hop: Int, isSos: Boolean = false): Boolean {
        val now = clock.now()
        val rowId = seenDao.tryInsert(
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
        return rowId != -1L
    }

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
        markSeen(id, hop, isSos)
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

    /** Membersihkan catatan frame maupun pesan yang sudah melewati retensi (time-based expiry). */
    suspend fun pruneExpired(): Int {
        val now = clock.now()
        return seenDao.purgeExpired(now) + seenFrameDao.purgeExpired(now)
    }
}
