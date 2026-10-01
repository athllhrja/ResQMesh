package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.db.message.MessageEntity
import com.resqmesh.data.db.node.NodeEntity
import com.resqmesh.domain.model.Fragmenter
import com.resqmesh.domain.model.MessageDirection
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MessageStatus
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MsgFlag
import com.resqmesh.domain.model.NodeId
import com.resqmesh.mesh.fake.InMemoryMessageDao
import com.resqmesh.mesh.fake.InMemoryMessageHopDao
import com.resqmesh.mesh.fake.InMemoryNodeDao
import com.resqmesh.mesh.fake.InMemorySeenFrameDao
import com.resqmesh.mesh.fake.InMemorySeenMessageDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RelayEngineTest {

    private val self = NodeId(0xA83F2C)
    private val destination = NodeId(0xC72D4A)
    private val origin = NodeId(0xD1E2F3)

    private val shortText = "PESAN".toByteArray()

    private lateinit var nodes: InMemoryNodeDao
    private lateinit var messages: InMemoryMessageDao
    private lateinit var hops: InMemoryMessageHopDao
    private lateinit var seen: InMemorySeenMessageDao
    private lateinit var seenFrames: InMemorySeenFrameDao
    private lateinit var engine: RelayEngine

    @Before
    fun setUp() {
        val clock = TimeProvider { 1_000L }
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

    private fun frame(
        id: MessageId = MessageId.of(origin, 1),
        destinationId: NodeId = destination,
        ttl: Int = 5,
        hop: Int = 0,
        payload: ByteArray = shortText,
    ) = MeshFrame(
        messageId = id,
        destination = destinationId,
        ttl = ttl,
        hopCount = hop,
        flags = MsgFlag.ACK_REQUESTED,
        fragIndex = 0,
        fragCount = 1,
        totalPayloadLen = payload.size,
        payloadChunk = payload,
    )

    @Test
    fun `pesan_dari_diri_sendiri_ditolak`() = runTest {
        val decision = engine.onFrame(
            frame(id = MessageId.of(self, 1)),
            peer = Peer.ADVERTISING,
            rssi = -50,
        )
        assertEquals(RelayDecision.Reject(RejectReason.SELF_ORIGIN), decision)
    }

    @Test
    fun `tujuan_menerima_pesan_dan_balasan_ack`() = runTest {
        val decision = engine.onFrame(
            frame(destinationId = self),
            peer = Peer.ADVERTISING,
            rssi = -50,
        )
        assertTrue(decision is RelayDecision.Ack)
        val ack = (decision as RelayDecision.Ack).frame
        assertTrue(ack.isAck)
        assertEquals(NodeId.BROADCAST_ID, ack.destination)
        assertEquals(1, ack.fragCount)
        assertEquals(0, ack.totalPayloadLen)
    }

    @Test
    fun `pengirim_meneruskan_dan_menurunkan_ttl_serta_hop`() = runTest {
        nodes.upsert(nodeFor(destination))

        val decision = engine.onFrame(frame(), peer = Peer.ADVERTISING, rssi = -60)

        assertTrue(decision is RelayDecision.Relayed)
        val relayed = (decision as RelayDecision.Relayed).frames.single()
        assertEquals(4, relayed.ttl)
        assertEquals(1, relayed.hopCount)
    }

    @Test
    fun `pengirim_menunda_saat_tujuan_belum_diketahui`() = runTest {
        val decision = engine.onFrame(frame(), peer = Peer.ADVERTISING, rssi = -60)
        assertTrue(decision is RelayDecision.ForwardLater)
    }

    @Test
    fun `frame_identik_ditolak_pada_terusan_kedua`() = runTest {
        nodes.upsert(nodeFor(destination))
        val inbound = frame()
        assertTrue(engine.onFrame(inbound, peer = Peer.ADVERTISING, rssi = -60) is RelayDecision.Relayed)

        val duplicate = engine.onFrame(inbound, peer = Peer.ADVERTISING, rssi = -60)
        assertEquals(RelayDecision.Reject(RejectReason.DUPLICATE), duplicate)
    }

    @Test
    fun `pesan_multi_fragmen_tetap_diteruskan_setelah_reassembly_selesai`() = runTest {
        nodes.upsert(nodeFor(destination))
        val payload = "BANTUAN DARURAT SEKARANG".toByteArray()
        val base = MeshFrame(
            messageId = MessageId.of(origin, 9),
            destination = destination,
            ttl = 5,
            hopCount = 0,
            flags = MsgFlag.FRAGMENTED or MsgFlag.ACK_REQUESTED,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = payload.size,
            payloadChunk = ByteArray(0),
        )
        val fragments = Fragmenter.fragmentsOf(base, payload)
        assertTrue(fragments.size > 1)

        // Semua fragmen kecuali yang terakhir hanya menyisakan buffer.
        fragments.dropLast(1).forEach { fragment ->
            val decision = engine.onFrame(fragment, peer = Peer.ADVERTISING, rssi = -60)
            assertEquals(RelayDecision.Ignore, decision)
        }

        val complete = engine.onFrame(
            fragments.last(),
            peer = Peer.ADVERTISING,
            rssi = -60,
        )
        assertTrue("Fragmen terakhir harus tetap diteruskan", complete is RelayDecision.Relayed)
    }

    @Test
    fun `pengiriman_pertama_mencatat_satu_kali_penerusan`() = runTest {
        nodes.upsert(nodeFor(destination))
        val id = MessageId.of(origin, 20)

        val first = engine.onFrame(
            frame(id = id, ttl = 5, hop = 0),
            peer = Peer.ADVERTISING,
            rssi = -60,
        )
        assertTrue(first is RelayDecision.Relayed)
        assertEquals(1, seen.rows[id.value]?.forwardCount)

        val duplicate = engine.onFrame(
            frame(id = id, ttl = 5, hop = 0),
            peer = Peer.CONNECTED,
            rssi = -70,
        )
        assertEquals(RelayDecision.Reject(RejectReason.DUPLICATE), duplicate)
        assertEquals(1, seen.rows[id.value]?.forwardCount)
    }

    @Test
    fun `ack_dari_tujuan_menandai_pesan_terkirim`() = runTest {
        val id = MessageId.of(self, 4)
        messages.upsert(
            MessageEntity(
                messageKey = id.value,
                messageIdHex = id.display(),
                originNodeId = self.value,
                destinationId = destination.value,
                peerId = destination.value,
                payload = "halo",
                payloadBytes = "halo".toByteArray(),
                createdAt = 0L,
                initialTtl = 5,
                ttl = 5,
                hopCount = 0,
                status = MessageStatus.IN_TRANSIT.wire,
                direction = MessageDirection.OUTGOING.wire,
                isSos = false,
                isReplay = false,
                firstForwardAt = null,
                deliveredAt = null,
                isReassembly = false,
                lastError = null,
            ),
        )

        val ack = MeshFrame(
            messageId = id,
            destination = NodeId.BROADCAST_ID,
            ttl = 4,
            hopCount = 1,
            flags = MsgFlag.IS_ACK,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = 0,
            payloadChunk = ByteArray(0),
        )
        val decision = engine.onFrame(ack, peer = Peer.ADVERTISING, rssi = -60)

        assertEquals(RelayDecision.Ignore, decision)
        assertEquals(MessageStatus.ACKED.wire, messages.rows[id.value]?.status)
    }

    @Test
    fun `ttl_nol_tidak_lagi_diteruskan`() = runTest {
        nodes.upsert(nodeFor(destination))
        val decision = engine.onFrame(
            frame(id = MessageId.of(origin, 30), ttl = 0),
            peer = Peer.ADVERTISING,
            rssi = -60,
        )
        assertEquals(RelayDecision.Reject(RejectReason.TTL_EXPIRED), decision)
    }

    private fun nodeFor(id: NodeId) = NodeEntity(
        nodeId = id.value,
        displayName = id.toString(),
        statusFlags = 0,
        batteryPct = 80,
        pendingCount = 0,
        gattPeerCount = 0,
        defaultTtl = MeshConfig.DEFAULT_TTL,
        nodeSeq = 0L,
        rssi = -60,
        isSelf = false,
        firstSeenAt = 0L,
        lastSeenAt = 1_000L,
    )
}
