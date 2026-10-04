package com.resqmesh.mesh

import com.resqmesh.core.TimeProvider
import com.resqmesh.data.codec.FrameCodec
import com.resqmesh.data.codec.SosCodec
import com.resqmesh.data.db.message.MessageEntity
import com.resqmesh.domain.model.BeaconFrame
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MessageStatus
import com.resqmesh.domain.model.MsgFlag
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosKind
import com.resqmesh.mesh.fake.InMemoryMessageDao
import com.resqmesh.mesh.fake.InMemoryMessageHopDao
import com.resqmesh.mesh.fake.InMemoryNodeDao
import com.resqmesh.mesh.fake.InMemorySeenFrameDao
import com.resqmesh.mesh.fake.InMemorySeenMessageDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SosRetransmitAndCarryTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout(10, TimeUnit.SECONDS)

    private val originId = NodeId(0x111111)
    private val relayId = NodeId(0x222222)
    private val responderId = NodeId(0x333333)

    private val clock = object : TimeProvider {
        var time = 10_000L
        override fun now(): Long = time
    }

    private val codec = FrameCodec()
    private val fix = LocationFix(
        point = LatLon(latitude = -6.17539, longitude = 106.82715),
        accuracyMeters = 10,
        ageSeconds = 2,
        batteryPct = 80,
    )

    private lateinit var messageDao: InMemoryMessageDao
    private lateinit var nodeDao: InMemoryNodeDao
    private lateinit var hopDao: InMemoryMessageHopDao
    private lateinit var seenDao: InMemorySeenMessageDao
    private lateinit var seenFrameDao: InMemorySeenFrameDao

    @Before
    fun setUp() {
        messageDao = InMemoryMessageDao()
        nodeDao = InMemoryNodeDao()
        hopDao = InMemoryMessageHopDao()
        seenDao = InMemorySeenMessageDao()
        seenFrameDao = InMemorySeenFrameDao()
    }

    private fun TestScope.buildRelayManager(
        selfId: NodeId,
        sentFrames: MutableList<MeshFrame>,
    ): Pair<MeshManager, StoreAndForwardQueue> {
        val transport = object : MeshTransport {
            override fun start(onFrame: (MeshFrame, Int) -> Unit, onBeacon: (ByteArray, Int) -> Unit) {}
            override fun stop() {}
            override fun advertise(payload: ByteArray) {
                runCatching { codec.decode(payload) }.getOrNull()?.let { sentFrames += it }
            }
            override fun sendOverLink(peerId: NodeId, frames: List<MeshFrame>) {}
        }
        val scheduler = BeaconScheduler(
            scope = backgroundScope,
            codec = codec,
            publisher = { transport.advertise(it) },
            random = Random(42),
        )
        val relayEngine = RelayEngine(
            selfId = selfId,
            duplicateGuard = DuplicateGuard(seenDao, seenFrameDao, clock),
            ttlPolicy = TtlPolicy(),
            assembler = FragmentAssembler(clock),
            ackTracker = AckTracker(selfId, messageDao, hopDao, clock),
            messageDao = messageDao,
            hopDao = hopDao,
            nodeDao = nodeDao,
            clock = clock,
        )
        val peerLinks = object : PeerLinkRegistry {
            override fun connectedPeers(): Set<NodeId> = emptySet()
        }
        val manager = MeshManager(
            scope = backgroundScope,
            selfId = selfId,
            nodeDao = nodeDao,
            messageDao = messageDao,
            relayEngine = relayEngine,
            codec = codec,
            identity = { MessageId.of(selfId, 1) },
            clock = clock,
            transport = transport,
            scheduler = scheduler,
            forwardingPolicy = ForwardingPolicy(peerLinks),
        )
        val storeAndForward = StoreAndForwardQueue(
            messageDao = messageDao,
            seenDao = seenDao,
            meshManager = manager,
            clock = clock,
            scope = backgroundScope,
            random = Random(42),
        )
        manager.storeAndForwardQueue = storeAndForward
        scheduler.start()
        return manager to storeAndForward
    }

    @Test
    fun `tes_TTL_tidak_berkurang_dua_kali`() = runTest {
        val sentFrames = mutableListOf<MeshFrame>()
        val (manager, _) = buildRelayManager(relayId, sentFrames)

        val incidentId = MessageId.of(originId, 50)
        val locPayload = SosCodec.encodeLoc(SosKind.TRAPPED, fix, 1, 0)
        val incomingFrame = MeshFrame(
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

        val decision = manager.relayEngine.onFrame(incomingFrame, Peer.ADVERTISING, -60)
        assertTrue(decision is RelayDecision.Relayed || decision is RelayDecision.Consume)

        val storedInDb = requireNotNull(messageDao.findByKey(incidentId.value))
        // Saat pertama kali disimpan oleh relay, TTL sudah berkurang dari 10 ke 9 dan hop menjadi 1
        assertEquals(9, storedInDb.ttl)
        assertEquals(1, storedInDb.hopCount)

        sentFrames.clear()
        // Saat replayRelay dipanggil untuk dipancarkan ulang, TTL harus TETAP 9 dan hop TETAP 1
        manager.replayRelay(storedInDb)
        testScheduler.advanceTimeBy(3_000L)

        val retransmitted = sentFrames.last()
        assertEquals(9, retransmitted.ttl)
        assertEquals(1, retransmitted.hopCount)
    }

    @Test
    fun `tes_responder_yang_masuk_jangkauan_setelah_SOS_pertama_tetap_menerimanya`() = runTest {
        val sentFrames = mutableListOf<MeshFrame>()
        val (manager, storeAndForward) = buildRelayManager(relayId, sentFrames)

        val incidentId = MessageId.of(originId, 60)
        val locPayload = SosCodec.encodeLoc(SosKind.MEDICAL, fix, 1, 0)
        val incomingFrame = MeshFrame(
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

        // Relay B menerima SOS pertama dari Origin A
        manager.relayEngine.onFrame(incomingFrame, Peer.ADVERTISING, -60)
        val stored = requireNotNull(messageDao.findByKey(incidentId.value))
        assertEquals(MessageStatus.CARRYING.wire, stored.status)

        sentFrames.clear()

        // Responder C baru muncul dan mengirimkan beacon ke Relay B
        val beaconPayload = codec.encodeBeacon(
            BeaconFrame(
                nodeId = responderId,
                statusFlags = 0x01,
                batteryPct = 90,
                pendingCount = 0,
                gattPeerCount = 0,
                defaultTtl = 5,
                nodeSeq = 1L,
            ),
        )
        manager.onBeaconFrame(beaconPayload, -50)

        // Store-and-forward dipicu karena ada peer baru
        storeAndForward.onNewPeerDiscovered()
        testScheduler.advanceTimeBy(3_000L)

        // Memastikan frame SOS carried dipancarkan ulang ke Responder C
        assertTrue("Frame SOS harus dipancarkan ulang saat ada tetangga baru", sentFrames.isNotEmpty())
        val carriedFrame = sentFrames.first { it.messageId == incidentId }
        assertEquals(9, carriedFrame.ttl)
    }

    @Test
    fun `tes_carry_berhenti_setelah_ACK`() = runTest {
        val sentFrames = mutableListOf<MeshFrame>()
        val (manager, storeAndForward) = buildRelayManager(relayId, sentFrames)

        val incidentId = MessageId.of(originId, 70)
        val locPayload = SosCodec.encodeLoc(SosKind.FIRE, fix, 1, 0)
        val incomingFrame = MeshFrame(
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

        manager.relayEngine.onFrame(incomingFrame, Peer.ADVERTISING, -60)
        assertEquals(MessageStatus.CARRYING.wire, messageDao.findByKey(incidentId.value)?.status)

        // ACK terdengar dari responder/origin
        val ackFrame = MeshFrame(
            messageId = incidentId,
            destination = NodeId.BROADCAST_ID,
            ttl = 8,
            hopCount = 2,
            flags = MsgFlag.IS_ACK,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = 0,
            payloadChunk = ByteArray(0),
        )
        manager.relayEngine.onFrame(ackFrame, Peer.ADVERTISING, -50)

        assertEquals(MessageStatus.ACKED.wire, messageDao.findByKey(incidentId.value)?.status)

        // Memastikan tidak ada lagi SOS carried yang aktif untuk di-broadcast
        val activeCarried = messageDao.activeCarriedSosMessages(relayId.value, 0)
        assertTrue(activeCarried.isEmpty())

        sentFrames.clear()
        storeAndForward.processCarriedSos()
        testScheduler.advanceTimeBy(3_000L)
        assertTrue("Pancaran ulang harus berhenti total setelah ACK", sentFrames.isEmpty())
    }

    @Test
    fun `tes_origin_re_advertise_berhenti_saat_dibatalkan`() = runTest {
        val sentFrames = mutableListOf<MeshFrame>()
        val (_, storeAndForward) = buildRelayManager(originId, sentFrames)

        val incidentId = MessageId.of(originId, 80)
        val locPayload = SosCodec.encodeLoc(SosKind.TRAPPED, fix, 1, 0)

        // Origin menyimpan SOS miliknya sendiri
        messageDao.upsert(
            MessageEntity(
                messageKey = incidentId.value,
                messageIdHex = incidentId.display(),
                originNodeId = originId.value,
                destinationId = NodeId.BROADCAST_ID.value,
                peerId = NodeId.BROADCAST_ID.value,
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

        sentFrames.clear()
        storeAndForward.processOriginSos()
        testScheduler.advanceTimeBy(3_000L)
        assertTrue("Origin harus memancarkan ulang SOS aktif", sentFrames.isNotEmpty())

        // User menekan Batalkan SOS
        storeAndForward.cancelSelfSos()
        assertEquals(MessageStatus.CANCELLED.wire, messageDao.findByKey(incidentId.value)?.status)

        sentFrames.clear()
        clock.time += 10_000L
        storeAndForward.processOriginSos()
        testScheduler.advanceTimeBy(3_000L)
        assertTrue("Origin berhenti memancarkan ulang setelah dibatalkan", sentFrames.isEmpty())
    }
}
