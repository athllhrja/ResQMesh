package com.resqmesh.mesh.simulation

import com.resqmesh.core.TimeProvider
import com.resqmesh.data.codec.FrameCodec
import com.resqmesh.domain.model.FrameType
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.NodeId
import com.resqmesh.mesh.AckTracker
import com.resqmesh.mesh.BeaconScheduler
import com.resqmesh.mesh.DuplicateGuard
import com.resqmesh.mesh.ForwardingPolicy
import com.resqmesh.mesh.FragmentAssembler
import com.resqmesh.mesh.MessageIdFactory
import com.resqmesh.mesh.MeshManager
import com.resqmesh.mesh.MeshTransport
import com.resqmesh.mesh.PeerLinkRegistry
import com.resqmesh.mesh.RelayEngine
import com.resqmesh.mesh.StoreAndForwardQueue
import com.resqmesh.mesh.TtlPolicy
import com.resqmesh.mesh.fake.InMemoryMessageDao
import com.resqmesh.mesh.fake.InMemoryMessageHopDao
import com.resqmesh.mesh.fake.InMemoryNodeDao
import com.resqmesh.mesh.fake.InMemorySeenFrameDao
import com.resqmesh.mesh.fake.InMemorySeenMessageDao
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Simulator Media Udara BLE Multi-Node (Tugas T6):
 * - Mengukur transmisi multi-hop nyata di lingkungan virtual.
 * - Memperhitungkan matriks topologi ketetanggaan (siapa mendengar siapa).
 * - Mendukung simulasi probabilitas paket loss acak (0%, 20%, 40%).
 * - Mengontrol daya node (power ON / OFF) untuk menguji re-discovery dan carry.
 * - Memantau statistik total pancaran (`totalBroadcastCount`) untuk pembuktian bebas badai.
 */
class SimulatedAirwave(
    private val scope: CoroutineScope,
    private val clock: TimeProvider,
    private val lossRate: Double = 0.0,
    private val random: Random = Random(42),
) {
    val nodes = ConcurrentHashMap<NodeId, SimulatedNode>()
    private val adjacencies = ConcurrentHashMap<Pair<NodeId, NodeId>, Boolean>()

    val totalBroadcastCount = AtomicInteger(0)

    fun setLink(node1: NodeId, node2: NodeId, reachable: Boolean = true) {
        adjacencies[node1 to node2] = reachable
        adjacencies[node2 to node1] = reachable
    }

    fun setLineTopology(nodeList: List<NodeId>) {
        for (i in 0 until nodeList.size - 1) {
            setLink(nodeList[i], nodeList[i + 1], true)
        }
    }

    fun canReach(from: NodeId, to: NodeId): Boolean {
        if (from == to) return false
        return adjacencies[from to to] == true
    }

    fun createNode(
        nodeId: NodeId,
        isResponder: Boolean = false,
    ): SimulatedNode {
        val nodeDao = InMemoryNodeDao()
        val messageDao = InMemoryMessageDao()
        val hopDao = InMemoryMessageHopDao()
        val seenDao = InMemorySeenMessageDao()
        val seenFrameDao = InMemorySeenFrameDao()
        val codec = FrameCodec()

        val transport = SimulatedNodeTransport(nodeId, this)

        val scheduler = BeaconScheduler(
            scope = scope,
            codec = codec,
            publisher = { payload -> transport.publish(payload) },
            random = random,
        )

        val relayEngine = RelayEngine(
            selfId = nodeId,
            duplicateGuard = DuplicateGuard(seenDao, seenFrameDao, clock),
            ttlPolicy = TtlPolicy(),
            assembler = FragmentAssembler(clock),
            ackTracker = AckTracker(nodeId, messageDao, hopDao, clock),
            messageDao = messageDao,
            hopDao = hopDao,
            nodeDao = nodeDao,
            clock = clock,
        ).apply {
            this.isResponder = isResponder
        }

        val manager = MeshManager(
            scope = scope,
            selfId = nodeId,
            nodeDao = nodeDao,
            messageDao = messageDao,
            relayEngine = relayEngine,
            codec = codec,
            identity = MessageIdFactory { MessageId.of(nodeId, 1) },
            clock = clock,
            transport = transport,
            scheduler = scheduler,
            forwardingPolicy = ForwardingPolicy(object : PeerLinkRegistry {
                override fun connectedPeers(): Set<NodeId> = emptySet()
            }),
        )

        val storeAndForward = StoreAndForwardQueue(
            messageDao = messageDao,
            seenDao = seenDao,
            meshManager = manager,
            clock = clock,
            scope = scope,
            random = random,
        )
        manager.storeAndForwardQueue = storeAndForward

        val simNode = SimulatedNode(
            nodeId = nodeId,
            manager = manager,
            transport = transport,
            storeAndForward = storeAndForward,
            messageDao = messageDao,
            nodeDao = nodeDao,
            codec = codec,
        )
        nodes[nodeId] = simNode
        return simNode
    }

    fun transmit(fromId: NodeId, payload: ByteArray) {
        val senderNode = nodes[fromId] ?: return
        if (!senderNode.isPoweredOn) return

        totalBroadcastCount.incrementAndGet()

        for ((targetId, targetNode) in nodes) {
            if (targetId == fromId || !targetNode.isPoweredOn) continue
            if (!canReach(fromId, targetId)) continue

            if (lossRate > 0.0 && random.nextDouble() < lossRate) {
                continue // Simulasi packet loss acak
            }

            targetNode.receivePayload(payload, rssi = -60)
        }
    }

    class SimulatedNode(
        val nodeId: NodeId,
        val manager: MeshManager,
        val transport: SimulatedNodeTransport,
        val storeAndForward: StoreAndForwardQueue,
        val messageDao: InMemoryMessageDao,
        val nodeDao: InMemoryNodeDao,
        val codec: FrameCodec,
    ) {
        var isPoweredOn: Boolean = false
            private set

        fun powerOn() {
            if (isPoweredOn) return
            isPoweredOn = true
            manager.start()
        }

        fun powerOff() {
            if (!isPoweredOn) return
            isPoweredOn = false
            manager.stop()
        }

        fun receivePayload(payload: ByteArray, rssi: Int) {
            if (!isPoweredOn) return
            transport.deliverToCallbacks(payload, rssi)
        }
    }

    class SimulatedNodeTransport(
        val nodeId: NodeId,
        private val airwave: SimulatedAirwave,
    ) : MeshTransport {

        private var onFrameCb: ((MeshFrame, Int) -> Unit)? = null
        private var onBeaconCb: ((ByteArray, Int) -> Unit)? = null

        override fun start(
            onFrame: (MeshFrame, Int) -> Unit,
            onBeacon: (ByteArray, Int) -> Unit,
        ): Result<Unit> {
            onFrameCb = onFrame
            onBeaconCb = onBeacon
            return Result.success(Unit)
        }

        override fun stop() {
            onFrameCb = null
            onBeaconCb = null
        }

        fun publish(payload: ByteArray) {
            airwave.transmit(nodeId, payload)
        }

        override fun advertise(payload: ByteArray) {
            publish(payload)
        }

        override fun sendOverLink(peerId: NodeId, frames: List<MeshFrame>) {}

        fun deliverToCallbacks(payload: ByteArray, rssi: Int) {
            val type = payload.getOrNull(1)?.let { FrameType.fromCode(it.toInt() and 0xFF) } ?: return
            when (type) {
                FrameType.BEACON -> onBeaconCb?.invoke(payload, rssi)
                FrameType.MSG -> {
                    val frame = runCatching { airwave.nodes[nodeId]?.codec?.decode(payload) }.getOrNull()
                    if (frame != null) {
                        onFrameCb?.invoke(frame, rssi)
                    }
                }
                FrameType.UNKNOWN -> Unit
            }
        }
    }
}
