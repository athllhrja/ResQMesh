package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.NodeId
import com.resqmesh.mesh.fake.InMemorySeenFrameDao
import com.resqmesh.mesh.fake.InMemorySeenMessageDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DuplicateGuardTest {

    private val id = MessageId.of(NodeId(0xD1E2F3), 1)
    private var now = 1_000L
    private lateinit var seen: InMemorySeenMessageDao
    private lateinit var frames: InMemorySeenFrameDao
    private lateinit var guard: DuplicateGuard

    @Before
    fun setUp() {
        val clock = TimeProvider { now }
        seen = InMemorySeenMessageDao()
        frames = InMemorySeenFrameDao()
        guard = DuplicateGuard(seen, frames, clock)
    }

    @Test
    fun `fragmen_berbeda_dianggap_baru_walaupun_pesan_sama`() = runTest {
        assertEquals(FrameVerdict.Fresh, guard.registerFrame(id, fragIndex = 0, hop = 0))
        assertEquals(FrameVerdict.Fresh, guard.registerFrame(id, fragIndex = 1, hop = 0))
    }

    @Test
    fun `fragmen_identik_dianggap_duplikat_pada_kali_kedua`() = runTest {
        assertEquals(FrameVerdict.Fresh, guard.registerFrame(id, fragIndex = 0, hop = 0))
        assertEquals(FrameVerdict.Repeated, guard.registerFrame(id, fragIndex = 0, hop = 0))
    }

    @Test
    fun `pesan_baru_belum_pernah_diteruskan_tidak_disaring`() = runTest {
        guard.trackMessage(id, hop = 0)
        assertEquals(false, guard.shouldSuppress(id, incomingHop = 0))
    }

    @Test
    fun `jalur_lebih_panjang_ditolak_setelah_sudah_diteruskan_sekali`() = runTest {
        guard.trackMessage(id, hop = 0)
        guard.noteForward(id, hop = 0)

        assertEquals(true, guard.shouldSuppress(id, incomingHop = 2))
    }

    @Test
    fun `jalur_lebih_pendek_tetap_diteruskan_sampai_batas_kuat()` = runTest {
        guard.trackMessage(id, hop = 3)
        guard.noteForward(id, hop = 3)
        assertEquals(false, guard.shouldSuppress(id, incomingHop = 1))

        guard.noteForward(id, hop = 1)
        assertEquals(2, guard.forwardCountOf(id))
        assertEquals(true, guard.shouldSuppress(id, incomingHop = 1))
    }

    @Test
    fun `hop_terbaik_kecil_selalu_menang`() = runTest {
        guard.trackMessage(id, hop = 4)
        guard.noteForward(id, hop = 4)
        guard.noteForward(id, hop = 2)
        assertEquals(2, seen.rows[id.value]?.hopCount)
    }

    @Test
    fun `sos diteruskan tepat sekali lalu menyingkir`() = runTest {
        guard.trackMessage(id, hop = 0, isSos = true)

        assertEquals(false, guard.shouldSuppress(id, incomingHop = 0))
        guard.noteForward(id, hop = 0)

        assertEquals(true, guard.shouldSuppress(id, incomingHop = 0))
    }

    @Test
    fun `sos diabaikan bias hop sehingga tidak terkubur jalur panjang`() = runTest {
        // Node ini sudah meneruskan sekali lewat jalur terbaik.
        guard.trackMessage(id, hop = 0, isSos = true)
        guard.noteForward(id, hop = 0)

        // Pesan biasa akan ditolak di sini karena hop memburuk. SOS juga
        // ditolak, tapi karena forwardCount sudah mencapai batas satu, bukan
        // karena jalurnya jelek: itu pembeda yang disengaja.
        assertEquals(true, guard.shouldSuppress(id, incomingHop = 5))
    }

    @Test
    fun `chat biasa memakai bias hop dan batas dua kali`() = runTest {
        guard.trackMessage(id, hop = 3)
        guard.noteForward(id, hop = 3)

        // Hop memburuk, jadi pesan biasa tidak boleh maju lagi.
        assertEquals(true, guard.shouldSuppress(id, incomingHop = 4))
    }

    @Test
    fun `prune_menghapus_catatan_yang_kedaluwarsa`() = runTest {
        guard.registerFrame(id, fragIndex = 0, hop = 0)
        guard.trackMessage(id, hop = 0)

        now += MeshConfig.SEEN_RETENTION_MS + 1

        val purged = guard.pruneExpired()
        assertEquals(2, purged)
        assertEquals(0, frames.count())
        assertEquals(0, seen.count())
    }
}
