package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.codec.SosCodec
import com.resqmesh.domain.model.Fragmenter
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MsgFlag
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosKind
import com.resqmesh.mesh.fake.InMemoryMessageDao
import com.resqmesh.mesh.fake.InMemoryMessageHopDao
import com.resqmesh.mesh.fake.InMemoryNodeDao
import com.resqmesh.mesh.fake.InMemorySeenFrameDao
import com.resqmesh.mesh.fake.InMemorySeenMessageDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

@OptIn(ExperimentalCoroutinesApi::class)
class FragmentAssemblerAndQueueTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout(10, TimeUnit.SECONDS)

    private val originId = NodeId(0x111111)
    private val relayId = NodeId(0x222222)

    private val clock = object : TimeProvider {
        var time = 10_000L
        override fun now(): Long = time
    }

    private val fix = LocationFix(
        point = LatLon(latitude = -6.17539, longitude = 106.82715),
        accuracyMeters = 10,
        ageSeconds = 2,
        batteryPct = 80,
    )

    @Test
    fun `perakitan_fragmen_dengan_kehilangan_fragmen_acak_dan_retransmisi`() = runTest {
        val messages = InMemoryMessageDao()
        val seenFrames = InMemorySeenFrameDao()
        val duplicateGuard = DuplicateGuard(InMemorySeenMessageDao(), seenFrames, clock)
        val assembler = FragmentAssembler(clock)

        val relayEngine = RelayEngine(
            selfId = relayId,
            duplicateGuard = duplicateGuard,
            ttlPolicy = TtlPolicy(),
            assembler = assembler,
            ackTracker = AckTracker(relayId, messages, InMemoryMessageHopDao(), clock),
            messageDao = messages,
            hopDao = InMemoryMessageHopDao(),
            nodeDao = InMemoryNodeDao(),
            clock = clock,
        )

        val incidentId = MessageId.of(originId, 100)
        val text180 = "A".repeat(180)
        val payload = SosCodec.encodeDetail(incidentId, SosKind.TRAPPED, text180)

        val baseFrame = MeshFrame(
            messageId = incidentId,
            destination = NodeId.BROADCAST_ID,
            ttl = 10,
            hopCount = 0,
            flags = MsgFlag.SOS or MsgFlag.SOS_PAYLOAD,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = payload.size,
            payloadChunk = ByteArray(0),
        )

        val allFragments = Fragmenter.fragmentsOf(baseFrame, payload)
        assertTrue("Text 180 byte harus terpecah menjadi banyak fragmen", allFragments.size > 15)

        // Gelombang 1: Simulasi kehilangan 3 fragmen acak di tengah
        val lostIndices = setOf(3, 7, 12)
        val wave1 = allFragments.filterIndexed { idx, _ -> idx !in lostIndices }

        wave1.forEach { frame ->
            relayEngine.onFrame(frame, Peer.ADVERTISING, -60)
        }

        // Belum selesai karena ada 3 fragmen yang hilang
        assertNotNull(messages.findByKey(incidentId.value))
        assertTrue("Reassembly harus ditandai belum selesai", messages.findByKey(incidentId.value)?.isReassembly == true)

        // Gelombang 2: Retransmisi fragmen yang sempat hilang
        val missingFrames = allFragments.filterIndexed { idx, _ -> idx in lostIndices }
        missingFrames.forEach { frame ->
            relayEngine.onFrame(frame, Peer.ADVERTISING, -60)
        }

        // Setelah retransmisi fragmen yang hilang sampai, perakitan lengkap selesai!
        val completedInDb = messages.findByKey(incidentId.value)
        assertNotNull(completedInDb)
        assertTrue("Pesan harus berhasil dirakit penuh", completedInDb?.isReassembly == false)
        assertEquals(text180, completedInDb?.payload)
    }

    @Test
    fun `penghapusan_seen_frame_saat_perakitan_kedaluwarsa`() = runTest {
        val seenFrames = InMemorySeenFrameDao()
        val duplicateGuard = DuplicateGuard(InMemorySeenMessageDao(), seenFrames, clock)
        val assembler = FragmentAssembler(clock)

        val incidentId = MessageId.of(originId, 200)
        val frame0 = MeshFrame(
            messageId = incidentId,
            destination = NodeId.BROADCAST_ID,
            ttl = 10,
            hopCount = 0,
            flags = MsgFlag.SOS or MsgFlag.SOS_PAYLOAD,
            fragIndex = 0,
            fragCount = 5,
            totalPayloadLen = 45,
            payloadChunk = ByteArray(9),
        )

        // Fragmen 0 didaftarkan
        duplicateGuard.registerFrame(incidentId, 0, 0)
        assembler.accept(frame0)
        assertEquals(1, seenFrames.count())

        // Simulasi waktu berlalu melebihi timeout perakitan
        clock.time += MeshConfig.ASSEMBLY_TIMEOUT_MS + 20_000L
        val evictedKeys = assembler.evictExpiredKeys(clock.now())
        assertEquals(listOf(incidentId.value), evictedKeys)

        // Hapus seen-frame milik pesan yang kedaluwarsa
        evictedKeys.forEach { key -> duplicateGuard.clearSeenFrames(key) }
        assertEquals(0, seenFrames.count())

        // Saat fragmen 0 dikirim ulang nanti, ia diperlakukan sebagai Fresh (bukan Repeated)
        val verdictRetransmit = duplicateGuard.registerFrame(incidentId, 0, 0)
        assertEquals(FrameVerdict.Fresh, verdictRetransmit)
    }

    @Test
    fun `tes_anggaran_waktu_pancar_SOS_0_60_180_byte`() {
        // Durasi 1 repeat SOS (150ms interval + jitter rata-rata ~100ms)
        val avgJitterMs = MeshConfig.SCHEDULER_JITTER_MS / 2
        val timePerRepeatMs = MeshConfig.BEACON_INTERVAL_SOS_MS + avgJitterMs
        val timePerFragMs = MeshConfig.REPEAT_COUNT_URGENT * timePerRepeatMs // 3x repeat per fragmen

        // 1. SOS tanpa teks (0 byte)
        val locFrags = ceil(MeshConfig.SOS_LOC_BLOB_BYTES.toDouble() / MeshConfig.PAYLOAD_PER_FRAME).toInt() // 2 fragmen
        val dur0Ms = locFrags * timePerFragMs
        val timeout0Ms = assemblerTimeoutFor(locFrags)
        assertTrue("Durasi SOS 0 byte ($dur0Ms ms) harus < timeout ($timeout0Ms ms)", dur0Ms < timeout0Ms)

        // 2. SOS 60 byte
        val detail60Len = MeshConfig.SOS_DETAIL_HEADER_BYTES + 60
        val detail60Frags = ceil(detail60Len.toDouble() / MeshConfig.PAYLOAD_PER_FRAME).toInt()
        val total60Frags = locFrags + detail60Frags
        val dur60Ms = total60Frags * timePerFragMs
        val timeout60Ms = assemblerTimeoutFor(total60Frags)
        assertTrue("Durasi SOS 60 byte ($dur60Ms ms) harus < timeout ($timeout60Ms ms)", dur60Ms < timeout60Ms)

        // 3. SOS 180 byte
        val detail180Len = MeshConfig.SOS_DETAIL_HEADER_BYTES + MeshConfig.SOS_TEXT_MAX_BYTES
        val detail180Frags = ceil(detail180Len.toDouble() / MeshConfig.PAYLOAD_PER_FRAME).toInt()
        val total180Frags = locFrags + detail180Frags
        val dur180Ms = total180Frags * timePerFragMs
        val timeout180Ms = assemblerTimeoutFor(total180Frags)
        assertTrue("Durasi SOS 180 byte ($dur180Ms ms) harus < timeout ($timeout180Ms ms)", dur180Ms < timeout180Ms)
    }

    private fun assemblerTimeoutFor(fragCount: Int): Long =
        maxOf(MeshConfig.ASSEMBLY_TIMEOUT_MS, fragCount * MeshConfig.ASSEMBLY_TIMEOUT_PER_FRAG_MS)
}
