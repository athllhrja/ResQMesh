package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.codec.SosCodec
import com.resqmesh.data.db.message.MessageEntity
import com.resqmesh.data.db.toSosIncidents
import com.resqmesh.domain.model.Fragmenter
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MsgFlag
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosHazard
import com.resqmesh.domain.model.SosKind
import com.resqmesh.mesh.fake.InMemoryMessageDao
import com.resqmesh.mesh.fake.InMemoryMessageHopDao
import com.resqmesh.mesh.fake.InMemoryNodeDao
import com.resqmesh.mesh.fake.InMemorySeenFrameDao
import com.resqmesh.mesh.fake.InMemorySeenMessageDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Menguji jalur SOS dari frame masuk sampai kartu insiden.
 *
 * Yang dijaga di sini adalah urutan LOC lalu DETAIL. Kalau keduanya dibalik,
 * koordinat tetap terkirim, tetapi penolong harus menunggu seluruh catatan
 * selesai dirakit. Di lapangan itu berkali-kali lebih lambat daripada yang
 * bisa diterima.
 */
class SosRelayTest {

    private val self = NodeId(0xA83F2C)
    private val origin = NodeId(0xD1E2F3)

    private val fix = LocationFix(
        point = LatLon(latitude = -6.17539, longitude = 106.82715),
        accuracyMeters = 15,
        ageSeconds = 3,
        batteryPct = 64,
    )

    private val incidentId = MessageId.of(origin, 100)
    private val detailId = MessageId.of(origin, 101)

    private lateinit var nodes: InMemoryNodeDao
    private lateinit var messages: InMemoryMessageDao
    private lateinit var hops: InMemoryMessageHopDao
    private lateinit var seen: InMemorySeenMessageDao
    private lateinit var seenFrames: InMemorySeenFrameDao
    private lateinit var engine: RelayEngine

    @Before
    fun setUp() {
        val clock = TimeProvider { 5_000L }
        nodes = InMemoryNodeDao()
        messages = InMemoryMessageDao()
        hops = InMemoryMessageHopDao()
        seen = InMemorySeenMessageDao()
        seenFrames = InMemorySeenFrameDao()

        engine = RelayEngine(
            selfId = self,
            duplicateGuard = DuplicateGuard(seen, seenFrames, clock),
            ttlPolicy = TtlPolicy(),
            assembler = FragmentAssembler(clock),
            ackTracker = AckTracker(self, messages, hops, clock),
            messageDao = messages,
            hopDao = hops,
            nodeDao = nodes,
            clock = clock,
        )
    }

    private fun locFrames(): List<MeshFrame> {
        val payload = SosCodec.encodeLoc(
            kind = SosKind.TRAPPED,
            fix = fix,
            victimCount = 2,
            hazards = SosHazard.BLEEDING,
        )
        val base = MeshFrame(
            messageId = incidentId,
            destination = NodeId.BROADCAST_ID,
            ttl = MeshConfig.SOS_DEFAULT_TTL,
            hopCount = 0,
            flags = MsgFlag.SOS or MsgFlag.SOS_PAYLOAD or MsgFlag.SOS_LOC,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = payload.size,
            payloadChunk = ByteArray(0),
        )
        return Fragmenter.fragmentsOf(base, payload)
    }

    private fun detailFrame(text: String): MeshFrame {
        val payload = SosCodec.encodeDetail(incidentId, SosKind.TRAPPED, text)
        return MeshFrame(
            messageId = detailId,
            destination = NodeId.BROADCAST_ID,
            ttl = MeshConfig.SOS_DEFAULT_TTL,
            hopCount = 0,
            flags = MsgFlag.SOS or MsgFlag.SOS_PAYLOAD,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = payload.size,
            payloadChunk = payload,
        )
    }

    private suspend fun deliver(frames: List<MeshFrame>) {
        frames.forEach { engine.onFrame(it, peer = Peer.ADVERTISING, rssi = -55) }
    }

    private fun incidents() = messages.rows.values.toSosIncidents()

    @Test
    fun `koordinat tersimpan sebagai kolom bukan teks`() = runTest {
        deliver(locFrames())

        val row = messages.rows[incidentId.value]
        assertNotNull(row)
        assertEquals(incidentId.value, row?.incidentKey)
        assertEquals(SosKind.TRAPPED.wire, row?.sosKind)
        assertEquals(2, row?.victimCount)
        assertEquals(SosHazard.BLEEDING, row?.hazards)
        assertTrue(row?.isSosLoc == true)
        // Baris LOC tetap punya teks ringkas agar gelembung di riwayat tidak kosong.
        assertTrue(row?.payload?.contains("106.82715") == true)
    }

    @Test
    fun `koordinat pulih kembali dengan presisi yang cukup`() = runTest {
        deliver(locFrames())

        val row = requireNotNull(messages.rows[incidentId.value])
        val recoveredLat = row.latE4!!.toDouble() / MeshConfig.COORD_SCALE
        val recoveredLon = row.lonE4!!.toDouble() / MeshConfig.COORD_SCALE

        assertEquals(-6.17539, recoveredLat, 0.0001)
        assertEquals(106.82715, recoveredLon, 0.0001)
        assertEquals(15, row.accuracyM)
        assertEquals(64, row.originBatteryPct)
    }

    @Test
    fun `fragmen loc hanya menyisakan buffer sampai fragmen terakhir`() = runTest {
        val frames = locFrames()
        assertEquals("SOS_LOC harus dua frame", 2, frames.size)

        val first = engine.onFrame(frames.first(), peer = Peer.ADVERTISING, rssi = -55)
        assertEquals(RelayDecision.Ignore, first)

        val second = engine.onFrame(frames.last(), peer = Peer.ADVERTISING, rssi = -55)
        assertTrue("Fragmen kedua harus menyelesaikan pesan", second is RelayDecision.Relayed)
    }

    @Test
    fun `insiden lengkap menggabungkan loc dan detail jadi satu kartu`() = runTest {
        deliver(locFrames())
        deliver(listOf(detailFrame("kaki tersangkut di pagar")))

        val list = incidents()
        assertEquals(1, list.size)
        val incident = list.single()
        assertEquals(incidentId.value, incident.id.value)
        assertEquals(SosKind.TRAPPED, incident.kind)
        assertEquals("kaki tersangkut di pagar", incident.text)
        assertTrue(incident.hasLocation)
        assertTrue(incident.hasDetail)
        assertEquals(2, incident.victimCount)
        assertEquals("Luka berdarah", incident.hazardLabel)
    }

    @Test
    fun `detail yang tiba duluan tetap tampil sambil menunggu koordinat`() = runTest {
        deliver(listOf(detailFrame("suara teriak dari dalam")))

        val incident = incidents().single()
        assertEquals(incidentId.value, incident.id.value)
        assertFalse(incident.hasLocation)
        assertNull(incident.point)
        assertTrue(incident.hasDetail)
        // Jenis ikut di header DETAIL supaya klasifikasi tidak ikut menunggu.
        assertEquals(SosKind.TRAPPED, incident.kind)
    }

    @Test
    fun `koordinat menyusul dan melengkapi insiden yang sama`() = runTest {
        deliver(listOf(detailFrame("tertangkap reruntuhan")))
        deliver(locFrames())

        val list = incidents()
        assertEquals("LOC dan DETAIL harus jadi satu insiden", 1, list.size)
        val incident = list.single()
        assertTrue(incident.hasLocation)
        assertNotNull(incident.point)
        assertEquals("tertangkap reruntuhan", incident.text)
    }

    @Test
    fun `sos diteruskan tepat sekali walau disiarkan berkali kali`() = runTest {
        val frames = locFrames()
        deliver(frames)
        assertEquals(1, seen.rows[incidentId.value]?.forwardCount)

        // Pengulangan advertising dari peer lain tidak menambah penerusan.
        val repeated = engine.onFrame(frames.last(), peer = Peer.CONNECTED, rssi = -70)

        assertEquals(RelayDecision.Reject(RejectReason.DUPLICATE), repeated)
        assertEquals(1, seen.rows[incidentId.value]?.forwardCount)
    }

    @Test
    fun `chat biasa memakai batas dua kali bukan satu seperti sos`() = runTest {
        val chatId = MessageId.of(origin, 200)
        fun chatFrame(hop: Int) = MeshFrame(
            messageId = chatId,
            destination = NodeId.BROADCAST_ID,
            ttl = MeshConfig.SOS_DEFAULT_TTL,
            hopCount = hop,
            flags = MsgFlag.ACK_REQUESTED,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = 4,
            payloadChunk = "PESAN".toByteArray(),
        )

        assertTrue(engine.onFrame(chatFrame(hop = 0), peer = Peer.ADVERTISING, rssi = -55) is RelayDecision.Relayed)
        assertEquals(1, seen.rows[chatId.value]?.forwardCount)
        assertFalse(seen.rows[chatId.value]?.isSos == true)
    }

    @Test
    fun `payload rusak tidak hilang tapi ditandai`() = runTest {
        val broken = MeshFrame(
            messageId = incidentId,
            destination = NodeId.BROADCAST_ID,
            ttl = MeshConfig.SOS_DEFAULT_TTL,
            hopCount = 0,
            flags = MsgFlag.SOS or MsgFlag.SOS_PAYLOAD or MsgFlag.SOS_LOC,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = 6,
            payloadChunk = ByteArray(6),
        )

        engine.onFrame(broken, peer = Peer.ADVERTISING, rssi = -55)

        val row = messages.rows[incidentId.value]
        assertNotNull("Pesan harus tetap tersimpan", row)
        assertNotNull("Baris harus menandai kegagalan decode", row?.lastError)
        assertNull(row?.latE4)
    }

    @Test
    fun `teks panjang tetap utuh setelah dirakit`() = runTest {
        val text = "Longsor, 3 orang — butuh_bucket dan selimut"
        deliver(listOf(detailFrame(text)))

        assertEquals(text, messages.rows[detailId.value]?.payload)
    }

    @Test
    fun `ack milik orang lain diteruskan supaya pengirim asli tahu`() = runTest {
        val chatId = MessageId.of(origin, 300)
        val ack = MeshFrame(
            messageId = chatId,
            destination = NodeId.BROADCAST_ID,
            ttl = 4,
            hopCount = 1,
            flags = MsgFlag.IS_ACK,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = 0,
            payloadChunk = ByteArray(0),
        )

        val decision = engine.onFrame(ack, peer = Peer.ADVERTISING, rssi = -55)

        assertTrue("ACK harus diteruskan oleh node perantara", decision is RelayDecision.Relayed)
        assertEquals(3, (decision as RelayDecision.Relayed).frames.single().ttl)
    }

    @Test
    fun `grup baris kosong tidak menghasilkan insiden`() {
        assertTrue(emptyList<MessageEntity>().toSosIncidents().isEmpty())
    }
}
