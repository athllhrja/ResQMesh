package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.codec.SosCodec
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MessageStatus
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

@OptIn(ExperimentalCoroutinesApi::class)
class SosBroadcastAckTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout(10, TimeUnit.SECONDS)

    private val clock = object : TimeProvider {
        override fun now(): Long = 10_000L
    }

    private val fix = LocationFix(
        point = LatLon(latitude = -6.17539, longitude = 106.82715),
        accuracyMeters = 10,
        ageSeconds = 2,
        batteryPct = 80,
    )

    private fun createEngine(nodeId: NodeId, isResponder: Boolean = false): Pair<RelayEngine, InMemoryMessageDao> {
        val messages = InMemoryMessageDao()
        val engine = RelayEngine(
            selfId = nodeId,
            duplicateGuard = DuplicateGuard(InMemorySeenMessageDao(), InMemorySeenFrameDao(), clock),
            ttlPolicy = TtlPolicy(),
            assembler = FragmentAssembler(clock),
            ackTracker = AckTracker(nodeId, messages, InMemoryMessageHopDao(), clock),
            messageDao = messages,
            hopDao = InMemoryMessageHopDao(),
            nodeDao = InMemoryNodeDao(),
            clock = clock,
        ).apply {
            this.isResponder = isResponder
        }
        return engine to messages
    }

    @Test
    fun `tes_A_B_C_responder_dengan_ACK_kembali_ke_A`() = runTest {
        val idA = NodeId(0x111111)
        val idB = NodeId(0x222222)
        val idC = NodeId(0x333333)

        val (engineA, daoA) = createEngine(idA, isResponder = false)
        val (engineB, _) = createEngine(idB, isResponder = false)
        val (engineC, _) = createEngine(idC, isResponder = true)

        val incidentId = MessageId.of(idA, 1)
        val locPayload = SosCodec.encodeLoc(SosKind.TRAPPED, fix, 1, 0)
        val sosFrame = MeshFrame(
            messageId = incidentId,
            destination = NodeId.BROADCAST_ID,
            ttl = 10,
            hopCount = 0,
            flags = MsgFlag.SOS or MsgFlag.SOS_PAYLOAD or MsgFlag.SOS_LOC,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = locPayload.size,
            payloadChunk = locPayload,
        )

        // Simpan SOS di Origin A
        daoA.upsert(
            com.resqmesh.data.db.message.MessageEntity(
                messageKey = incidentId.value,
                messageIdHex = incidentId.display(),
                originNodeId = idA.value,
                destinationId = NodeId.BROADCAST_ID.value,
                peerId = idA.value,
                payload = "SOS",
                payloadBytes = locPayload,
                createdAt = clock.now(),
                initialTtl = 10,
                ttl = 10,
                hopCount = 0,
                status = MessageStatus.IN_TRANSIT.wire,
                direction = "OUTGOING",
                isSos = true,
                isReplay = false,
                firstForwardAt = null,
                deliveredAt = null,
                isReassembly = false,
                lastError = null,
                incidentKey = incidentId.value,
                isSosLoc = true,
            ),
        )

        // A -> B
        val decisionB = engineB.onFrame(sosFrame, Peer.ADVERTISING, -60)
        assertTrue(decisionB is RelayDecision.Relayed)
        val framesFromB = (decisionB as RelayDecision.Relayed).frames

        // B -> C (Responder)
        var ackFromC: MeshFrame? = null
        for (frame in framesFromB) {
            val decisionC = engineC.onFrame(frame, Peer.ADVERTISING, -60)
            if (decisionC is RelayDecision.Ack) {
                ackFromC = decisionC.frame
            }
        }
        assertNotNull("Responder C harus menghasilkan ACK", ackFromC)

        // ACK C -> B
        val decisionAckB = engineB.onFrame(ackFromC!!, Peer.ADVERTISING, -60)
        assertTrue("Relay B harus meneruskan ACK", decisionAckB is RelayDecision.Relayed)
        val ackFromB = (decisionAckB as RelayDecision.Relayed).frames.single()

        // ACK B -> A
        engineA.onFrame(ackFromB, Peer.ADVERTISING, -60)

        // Origin A harus menandai SOS miliknya sebagai ACKED
        val updatedA = daoA.findByKey(incidentId.value)
        assertEquals(MessageStatus.ACKED.wire, updatedA?.status)
    }

    @Test
    fun `tes_ACK_kembali_untuk_TTL_awal_10_pada_6_plus_hop`() = runTest {
        val hopCountNodes = 7
        val nodes = (1..hopCountNodes).map { NodeId(it.toLong()) }
        val origin = nodes.first()

        val engines = nodes.mapIndexed { idx, id ->
            val isResp = (idx == nodes.lastIndex)
            createEngine(id, isResponder = isResp)
        }

        val (originEngine, originDao) = engines.first()
        val incidentId = MessageId.of(origin, 100)
        val locPayload = SosCodec.encodeLoc(SosKind.TRAPPED, fix, 1, 0)
        val sosFrame = MeshFrame(
            messageId = incidentId,
            destination = NodeId.BROADCAST_ID,
            ttl = 10,
            hopCount = 0,
            flags = MsgFlag.SOS or MsgFlag.SOS_PAYLOAD or MsgFlag.SOS_LOC,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = locPayload.size,
            payloadChunk = locPayload,
        )

        originDao.upsert(
            com.resqmesh.data.db.message.MessageEntity(
                messageKey = incidentId.value,
                messageIdHex = incidentId.display(),
                originNodeId = origin.value,
                destinationId = NodeId.BROADCAST_ID.value,
                peerId = origin.value,
                payload = "SOS",
                payloadBytes = locPayload,
                createdAt = clock.now(),
                initialTtl = 10,
                ttl = 10,
                hopCount = 0,
                status = MessageStatus.IN_TRANSIT.wire,
                direction = "OUTGOING",
                isSos = true,
                isReplay = false,
                firstForwardAt = null,
                deliveredAt = null,
                isReassembly = false,
                lastError = null,
                incidentKey = incidentId.value,
                isSosLoc = true,
            ),
        )

        // Forward SOS dari N1 sampai N7 (Responder)
        var currentFrames = listOf(sosFrame)
        var ackFrame: MeshFrame? = null

        for (i in 1 until hopCountNodes) {
            val (engine, _) = engines[i]
            val nextFrames = mutableListOf<MeshFrame>()
            for (frame in currentFrames) {
                val decision = engine.onFrame(frame, Peer.ADVERTISING, -60)
                if (decision is RelayDecision.Relayed) {
                    nextFrames.addAll(decision.frames)
                } else if (decision is RelayDecision.Ack) {
                    ackFrame = decision.frame
                }
            }
            currentFrames = nextFrames
        }

        assertNotNull("Responder (N7) pada hop ke-6 harus menghasilkan ACK", ackFrame)
        assertEquals(MeshConfig.ACK_TTL, ackFrame!!.ttl)

        // Propagasi ACK kembali dari N6 sampai N1
        var currentAck = ackFrame
        for (i in (hopCountNodes - 2) downTo 0) {
            val (engine, _) = engines[i]
            val decision = engine.onFrame(currentAck!!, Peer.ADVERTISING, -60)
            if (decision is RelayDecision.Relayed) {
                currentAck = decision.frames.single()
            }
        }

        val finalOriginMessage = originEngine.let { originDao.findByKey(incidentId.value) }
        assertEquals("Origin harus berhasil menerima ACK dari jalur 6+ hop", MessageStatus.ACKED.wire, finalOriginMessage?.status)
    }

    @Test
    fun `tes_ACK_duplikat_tidak_diteruskan_dua_kali`() = runTest {
        val relayId = NodeId(0x222222)
        val (relayEngine, _) = createEngine(relayId, isResponder = false)

        val incidentId = MessageId.of(NodeId(0x111111), 200)
        val ackFrame = MeshFrame(
            messageId = incidentId,
            destination = NodeId.BROADCAST_ID,
            ttl = 10,
            hopCount = 0,
            flags = MsgFlag.IS_ACK,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = 0,
            payloadChunk = ByteArray(0),
        )

        // Penerimaan ACK pertama
        val decision1 = relayEngine.onFrame(ackFrame, Peer.ADVERTISING, -60)
        assertTrue("Penerimaan ACK pertama harus diteruskan", decision1 is RelayDecision.Relayed)

        // Penerimaan ACK duplikat kedua dari tetangga lain dalam rentang < 3s
        val decision2 = relayEngine.onFrame(ackFrame, Peer.ADVERTISING, -60)
        assertEquals("ACK duplikat (<3s) harus ditolak dan tidak diteruskan dua kali", RelayDecision.Reject(RejectReason.DUPLICATE), decision2)
    }

    @Test
    fun `tes_resend_ACK_dari_responder_diteruskan_oleh_relay_saat_jarak_3_detik_atau_lebih`() = runTest {
        var currentTime = 10_000L
        val testClock = object : TimeProvider {
            override fun now(): Long = currentTime
        }

        val relayId = NodeId(0x222222)
        val messages = InMemoryMessageDao()
        val relayEngine = RelayEngine(
            selfId = relayId,
            duplicateGuard = DuplicateGuard(InMemorySeenMessageDao(), InMemorySeenFrameDao(), testClock),
            ttlPolicy = TtlPolicy(),
            assembler = FragmentAssembler(testClock),
            ackTracker = AckTracker(relayId, messages, InMemoryMessageHopDao(), testClock),
            messageDao = messages,
            hopDao = InMemoryMessageHopDao(),
            nodeDao = InMemoryNodeDao(),
            clock = testClock,
        )

        val incidentId = MessageId.of(NodeId(0x111111), 300)
        val ackFrame = MeshFrame(
            messageId = incidentId,
            destination = NodeId.BROADCAST_ID,
            ttl = 10,
            hopCount = 0,
            flags = MsgFlag.IS_ACK,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = 0,
            payloadChunk = ByteArray(0),
        )

        // 1. Penerimaan ACK pertama pada t = 10.000 ms
        val decision1 = relayEngine.onFrame(ackFrame, Peer.ADVERTISING, -60)
        assertTrue("ACK pertama harus diteruskan", decision1 is RelayDecision.Relayed)

        // 2. Salinan ke-2 dari pancaran yang sama (t = 10.150 ms) -> Harus ditolak sebagai DUPLICATE
        currentTime = 10_150L
        val decision2 = relayEngine.onFrame(ackFrame, Peer.ADVERTISING, -60)
        assertEquals("Salinan ke-2 dari pancaran yang sama (<3s) harus ditolak", RelayDecision.Reject(RejectReason.DUPLICATE), decision2)

        // 3. Salinan ke-3 dari pancaran yang sama (t = 10.300 ms) -> Harus ditolak sebagai DUPLICATE
        currentTime = 10_300L
        val decision3 = relayEngine.onFrame(ackFrame, Peer.ADVERTISING, -60)
        assertEquals("Salinan ke-3 dari pancaran yang sama (<3s) harus ditolak", RelayDecision.Reject(RejectReason.DUPLICATE), decision3)

        // 4. Resend ACK dari Responder setelah t = 13.500 ms (jarak 3.5 detik >= 3s)
        currentTime = 13_500L
        val decisionResend = relayEngine.onFrame(ackFrame, Peer.ADVERTISING, -60)
        assertTrue("Resend ACK setelah >= 3s harus diteruskan kembali oleh relay", decisionResend is RelayDecision.Relayed)
    }
}
