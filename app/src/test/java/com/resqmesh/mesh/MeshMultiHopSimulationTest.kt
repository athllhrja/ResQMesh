package com.resqmesh.mesh

import com.resqmesh.core.TimeProvider
import com.resqmesh.data.codec.SosCodec
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MessageStatus
import com.resqmesh.domain.model.MessageDirection
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.Fragmenter
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MeshMultiHopSimulationTest {

    private val idA = NodeId(0xAAAAAA)
    private val idB = NodeId(0xBBBBBB)
    private val idC = NodeId(0xCCCCCC)

    // Node A storage & engine
    private lateinit var nodesA: InMemoryNodeDao
    private lateinit var messagesA: InMemoryMessageDao
    private lateinit var hopsA: InMemoryMessageHopDao
    private lateinit var seenA: InMemorySeenMessageDao
    private lateinit var seenFramesA: InMemorySeenFrameDao
    private lateinit var engineA: RelayEngine

    // Node B storage & engine
    private lateinit var nodesB: InMemoryNodeDao
    private lateinit var messagesB: InMemoryMessageDao
    private lateinit var hopsB: InMemoryMessageHopDao
    private lateinit var seenB: InMemorySeenMessageDao
    private lateinit var seenFramesB: InMemorySeenFrameDao
    private lateinit var engineB: RelayEngine

    // Node C storage & engine
    private lateinit var nodesC: InMemoryNodeDao
    private lateinit var messagesC: InMemoryMessageDao
    private lateinit var hopsC: InMemoryMessageHopDao
    private lateinit var seenC: InMemorySeenMessageDao
    private lateinit var seenFramesC: InMemorySeenFrameDao
    private lateinit var engineC: RelayEngine

    @Before
    fun setUp() {
        val clock = TimeProvider { 1_000L }

        // Setup A
        nodesA = InMemoryNodeDao()
        messagesA = InMemoryMessageDao()
        hopsA = InMemoryMessageHopDao()
        seenA = InMemorySeenMessageDao()
        seenFramesA = InMemorySeenFrameDao()
        engineA = RelayEngine(
            selfId = idA,
            duplicateGuard = DuplicateGuard(seenA, seenFramesA, clock),
            ttlPolicy = TtlPolicy(),
            assembler = FragmentAssembler(clock),
            ackTracker = AckTracker(idA, messagesA, hopsA, clock),
            messageDao = messagesA,
            hopDao = hopsA,
            nodeDao = nodesA,
            clock = clock,
        )

        // Setup B
        nodesB = InMemoryNodeDao()
        messagesB = InMemoryMessageDao()
        hopsB = InMemoryMessageHopDao()
        seenB = InMemorySeenMessageDao()
        seenFramesB = InMemorySeenFrameDao()
        engineB = RelayEngine(
            selfId = idB,
            duplicateGuard = DuplicateGuard(seenB, seenFramesB, clock),
            ttlPolicy = TtlPolicy(),
            assembler = FragmentAssembler(clock),
            ackTracker = AckTracker(idB, messagesB, hopsB, clock),
            messageDao = messagesB,
            hopDao = hopsB,
            nodeDao = nodesB,
            clock = clock,
        )

        // Setup C
        nodesC = InMemoryNodeDao()
        messagesC = InMemoryMessageDao()
        hopsC = InMemoryMessageHopDao()
        seenC = InMemorySeenMessageDao()
        seenFramesC = InMemorySeenFrameDao()
        engineC = RelayEngine(
            selfId = idC,
            duplicateGuard = DuplicateGuard(seenC, seenFramesC, clock),
            ttlPolicy = TtlPolicy(),
            assembler = FragmentAssembler(clock),
            ackTracker = AckTracker(idC, messagesC, hopsC, clock),
            messageDao = messagesC,
            hopDao = hopsC,
            nodeDao = nodesC,
            clock = clock,
        )
    }

    @Test
    fun simulasi_multi_hop_mesh_a_b_c_end_to_end() = runTest {
        println("=== SIMULASI MULTI-HOP MESH (A -> B -> C) STARTED ===")

        // 1. Node A (Trigger SOS) F4 & F3
        val sosMessageId = MessageId.of(idA, 1)
        val fix = LocationFix(
            point = LatLon(latitude = -6.17539, longitude = 106.82715),
            accuracyMeters = 15,
            ageSeconds = 2,
            batteryPct = 90,
        )
        val payload = SosCodec.encodeLoc(
            kind = SosKind.MEDICAL,
            fix = fix,
            victimCount = 1,
            hazards = SosHazard.BLEEDING,
        )

        val baseFrame = MeshFrame(
            messageId = sosMessageId,
            destination = NodeId.BROADCAST_ID,
            ttl = 3, // Default TTL = 3 (F7)
            hopCount = 0, // Hop = 0 (F8)
            flags = MsgFlag.SOS or MsgFlag.SOS_PAYLOAD or MsgFlag.SOS_LOC or MsgFlag.ACK_REQUESTED,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = payload.size,
            payloadChunk = ByteArray(0),
        )

        // Pecah menjadi fragmen-fragmen (karena payload 16 byte > 9 byte per frame)
        val sosFramesFromA = Fragmenter.fragmentsOf(baseFrame, payload)

        // Catat SOS di local DB Node A (Status CREATED / PENDING_FORWARD, hop = 0)
        messagesA.upsert(
            com.resqmesh.data.db.message.MessageEntity(
                messageKey = sosMessageId.value,
                messageIdHex = sosMessageId.display(),
                originNodeId = idA.value,
                destinationId = NodeId.BROADCAST_ID.value,
                peerId = idA.value,
                payload = "-6.17539, 106.82715 ±15m",
                payloadBytes = payload,
                createdAt = 1_000L,
                initialTtl = 3,
                ttl = 3,
                hopCount = 0,
                status = MessageStatus.PENDING_FORWARD.wire,
                direction = MessageDirection.OUTGOING.wire,
                isSos = true,
                isReplay = false,
                firstForwardAt = null,
                deliveredAt = null,
                isReassembly = false,
                lastError = null,
                isSosLoc = true,
            )
        )

        val localNodeA = messagesA.findByKey(sosMessageId.value)
        assertNotNull("Node A harus menyimpan record SOS lokal", localNodeA)
        assertEquals("Status awal Node A harus PENDING_FORWARD / CREATED", MessageStatus.PENDING_FORWARD.wire, localNodeA?.status)
        assertEquals("Hop awal Node A harus 0 (F8)", 0, localNodeA?.hopCount)
        println("[STEP 1 SUCCESS] Node A berhasil membuat SOS (${sosFramesFromA.size} fragmen). Status: CREATED, Hop: 0, TTL: 3")

        // 2. Aliran A -> B (First Hop Relay)
        val relayedByB = mutableListOf<MeshFrame>()
        for (frame in sosFramesFromA) {
            val decisionB = engineB.onFrame(frame, peer = Peer.ADVERTISING, rssi = -55)
            if (decisionB is RelayDecision.Relayed) {
                relayedByB.addAll(decisionB.frames)
            }
        }
        assertTrue("Node B harus merelay fragmen dari A", relayedByB.isNotEmpty())
        val firstRelayedB = relayedByB.first()

        assertEquals("TTL Node B harus berkurang jadi 2 (F7)", 2, firstRelayedB.ttl)
        assertEquals("Hop Count Node B harus bertambah jadi 1 (F8)", 1, firstRelayedB.hopCount)
        assertEquals("Sender ID original harus tetap milik A", idA, firstRelayedB.messageId.origin)

        val storedB = messagesB.findByKey(sosMessageId.value)
        assertNotNull("Node B harus menyimpan history (F11)", storedB)
        println("[STEP 2 SUCCESS] Aliran A -> B: Node B menerima fragmen, TTL -> 2, Hop -> 1, history tersimpan (F11)")

        // Uji Duplicate Detection (F6): Kirim ulang fragmen yang sama ke B
        val duplicateDecisionB = engineB.onFrame(sosFramesFromA.first(), peer = Peer.ADVERTISING, rssi = -55)
        assertEquals("Duplicate Detection (F6) harus menolak duplikat", RelayDecision.Reject(RejectReason.DUPLICATE), duplicateDecisionB)
        println("[STEP 2.1 SUCCESS] Duplicate Detection (F6) berhasil menolak frame duplikat pada B")

        // 3. Aliran B -> C (Second Hop Relay & Responder C)
        engineC.isResponder = true
        var ackFromC: MeshFrame? = null
        for (frame in relayedByB) {
            val decisionC = engineC.onFrame(frame, peer = Peer.ADVERTISING, rssi = -65)
            if (decisionC is RelayDecision.Ack) {
                ackFromC = decisionC.frame
            }
        }
        assertNotNull("Responder Node C harus memproduksi ACK untuk SOS", ackFromC)

        val storedC = messagesC.findByKey(sosMessageId.value)
        assertNotNull("Node C harus menyimpan history (F11)", storedC)
        println("[STEP 3 SUCCESS] Aliran B -> C: Responder Node C menerima fragmen dan membuat ACK produksi")

        // 4. Aliran C -> A (ACK Propagation F10 lewat jalur produksi)
        val decisionAckB = engineB.onFrame(ackFromC!!, peer = Peer.ADVERTISING, rssi = -60)
        assertTrue("Node B harus merelay ACK", decisionAckB is RelayDecision.Relayed)
        val relayedAck = (decisionAckB as RelayDecision.Relayed).frames.single()

        // Node A menerima ACK dan memperbarui status menjadi DELIVERED / ACKED
        val decisionAckA = engineA.onFrame(relayedAck, peer = Peer.ADVERTISING, rssi = -50)
        assertTrue("Node A harus mengenali ACK miliknya", decisionAckA is RelayDecision.Ignore || decisionAckA is RelayDecision.Ack)

        val finalNodeA = messagesA.findByKey(sosMessageId.value)
        assertEquals("Status SOS di Node A otomatis berubah menjadi DELIVERED / ACKED (F10 / F9)", MessageStatus.ACKED.wire, finalNodeA?.status)
        println("[STEP 4 SUCCESS] Aliran C -> A: ACK dipropagasi, status SOS di Node A berubah menjadi DELIVERED / ACKED (ACKED)")
        println("=== SIMULASI MULTI-HOP MESH (A -> B -> C) COMPLETED SUCCESSFULLY ===")
    }
}
