package com.resqmesh.di

import android.content.Context
import com.resqmesh.core.MeshConfig
import com.resqmesh.core.SystemTimeProvider
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.codec.FrameCodec
import com.resqmesh.data.db.ResQMeshDatabase
import com.resqmesh.data.db.message.MessageDao
import com.resqmesh.data.db.message.MessageHopDao
import com.resqmesh.data.db.node.NodeDao
import com.resqmesh.data.db.node.NodeEntity
import com.resqmesh.data.db.seen.SeenFrameDao
import com.resqmesh.data.db.seen.SeenMessageDao
import com.resqmesh.data.location.BatteryReader
import com.resqmesh.data.location.LocationSource
import com.resqmesh.data.prefs.NodeIdentityStore
import com.resqmesh.data.repo.DefaultMeshRepository
import com.resqmesh.domain.MeshRepository
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.NodeStatusFlags
import com.resqmesh.domain.model.SignalStrength
import com.resqmesh.mesh.AckTracker
import com.resqmesh.mesh.BeaconScheduler
import com.resqmesh.mesh.DuplicateGuard
import com.resqmesh.mesh.ForwardingPolicy
import com.resqmesh.mesh.FragmentAssembler
import com.resqmesh.mesh.FramePublisher
import com.resqmesh.mesh.MessageIdFactory
import com.resqmesh.mesh.MeshManager
import com.resqmesh.mesh.MeshTransport
import com.resqmesh.mesh.NoOpMeshTransport
import com.resqmesh.mesh.PeerLinkRegistry
import com.resqmesh.mesh.RelayEngine
import com.resqmesh.mesh.StoreAndForwardQueue
import com.resqmesh.mesh.TtlPolicy
import com.resqmesh.mesh.ble.BleMeshTransport
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppGraph(context: Context) {

    private val appContext: Context = context.applicationContext
    private val clock: TimeProvider = SystemTimeProvider

    val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("resqmesh"))

    val database: ResQMeshDatabase by lazy { ResQMeshDatabase.build(appContext) }
    val nodeDao: NodeDao by lazy { database.nodeDao() }
    val messageDao: MessageDao by lazy { database.messageDao() }
    val hopDao: MessageHopDao by lazy { database.messageHopDao() }
    val seenDao: SeenMessageDao by lazy { database.seenMessageDao() }
    val seenFrameDao: SeenFrameDao by lazy { database.seenFrameDao() }

    val identityStore: NodeIdentityStore by lazy { NodeIdentityStore(appContext) }
    val codec: FrameCodec by lazy { FrameCodec() }
    val locationSource: LocationSource by lazy { LocationSource(appContext, clock) }

    val selfId: NodeId get() = identityStore.nodeId

    /**
     * Transport BLE sungguhan (BleMeshTransport) yang menangani BLE Scanning & Advertising.
     */
    val transport: MeshTransport by lazy { BleMeshTransport(appContext, codec) }

    val peerLinks: PeerLinkRegistry = object : PeerLinkRegistry {
        override fun connectedPeers(): Set<NodeId> = transport.connectedPeers()
    }

    val scheduler: BeaconScheduler by lazy {
        BeaconScheduler(
            scope = scope,
            codec = codec,
            publisher = FramePublisher { payload -> transport.advertise(payload) },
            selfId = selfId,
            logger = experimentLogger,
        )
    }

    val experimentLogger: com.resqmesh.experiment.ExperimentLogger by lazy {
        com.resqmesh.experiment.ExperimentLogger(appContext)
    }

    private val assembler: FragmentAssembler by lazy { FragmentAssembler(clock) }
    private val duplicateGuard: DuplicateGuard by lazy { DuplicateGuard(seenDao, seenFrameDao, clock) }
    private val ackTracker: AckTracker by lazy {
        AckTracker(selfId, messageDao, hopDao, clock, experimentLogger)
    }

    val relayEngine: RelayEngine by lazy {
        RelayEngine(
            selfId = selfId,
            duplicateGuard = duplicateGuard,
            ttlPolicy = TtlPolicy(),
            assembler = assembler,
            ackTracker = ackTracker,
            messageDao = messageDao,
            hopDao = hopDao,
            nodeDao = nodeDao,
            clock = clock,
            logger = experimentLogger,
        )
    }

    val storeAndForwardQueue: StoreAndForwardQueue by lazy {
        StoreAndForwardQueue(
            messageDao = messageDao,
            seenDao = seenDao,
            meshManager = meshManager,
            clock = clock,
            scope = scope,
        )
    }

    val meshManager: MeshManager by lazy {
        MeshManager(
            scope = scope,
            selfId = selfId,
            nodeDao = nodeDao,
            messageDao = messageDao,
            relayEngine = relayEngine,
            codec = codec,
            identity = MessageIdFactory { identityStore.nextMessageId() },
            clock = clock,
            transport = transport,
            scheduler = scheduler,
            forwardingPolicy = ForwardingPolicy(peerLinks),
            logger = experimentLogger,
        ).also { manager ->
            manager.storeAndForwardQueue = storeAndForwardQueue
        }
    }

    val repository: MeshRepository by lazy {
        DefaultMeshRepository(
            manager = meshManager,
            storeAndForwardQueue = storeAndForwardQueue,
            nodeDao = nodeDao,
            messageDao = messageDao,
            seenDao = seenDao,
            clock = clock,
        )
    }

    /** Persentase baterai untuk beacon dan SOS_LOC. Null bila tidak dilaporkan. */
    fun readBattery(): Int? = BatteryReader.read(appContext)

    fun ensureSelfNode() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val now = clock.now()
                nodeDao.upsert(
                    NodeEntity(
                        nodeId = selfId.value,
                        displayName = selfId.toString(),
                        statusFlags = NodeStatusFlags.MESH_ACTIVE or NodeStatusFlags.SCANNING,
                        batteryPct = -1,
                        pendingCount = 0,
                        gattPeerCount = 0,
                        defaultTtl = MeshConfig.DEFAULT_TTL,
                        nodeSeq = 0,
                        rssi = SignalStrength.RSSI_NONE,
                        isSelf = true,
                        firstSeenAt = now,
                        lastSeenAt = now,
                    ),
                )
            }.onFailure { e ->
                android.util.Log.e("AppGraph", "Gagal memastikan self node: ${e.message}", e)
            }
        }
    }

    fun close() {
        scope.cancel()
        if (database.isOpen) database.close()
    }
}
