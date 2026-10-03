package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.codec.FrameCodec
import com.resqmesh.data.codec.SosCodec
import com.resqmesh.data.db.message.MessageDao
import com.resqmesh.data.db.message.MessageEntity
import com.resqmesh.data.db.node.NodeDao
import com.resqmesh.data.db.node.NodeEntity
import com.resqmesh.domain.model.Fragmenter
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MessageDirection
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MessageStatus
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MeshState
import com.resqmesh.domain.model.MsgFlag
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SignalStrength
import com.resqmesh.domain.model.SosKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Titik masuk tunggal untuk transport. Lapisan BLE mengimplementasikan antarmuka
 * ini; sampai itu ada, [NoOpMeshTransport] dipakai sehingga logika mesh tetap
 * dapat diuji tanpa perangkat.
 */
interface MeshTransport {
    /** [onFrame] membawa frame pesan ter-decode. */
    fun start(
        onFrame: (MeshFrame, Int) -> Unit,
        onBeacon: (ByteArray, Int) -> Unit,
    )

    fun stop()

    /** Tier 1: siarkan lewat advertising. */
    fun advertise(payload: ByteArray)

    /** Tier 2: kirim langsung ke peer yang sudah terhubung GATT. */
    fun sendOverLink(peerId: NodeId, frames: List<MeshFrame>)

    fun connectedPeers(): Set<NodeId> = emptySet()
}

class NoOpMeshTransport : MeshTransport {
    override fun start(onFrame: (MeshFrame, Int) -> Unit, onBeacon: (ByteArray, Int) -> Unit) = Unit
    override fun stop() = Unit
    override fun advertise(payload: ByteArray) = Unit
    override fun sendOverLink(peerId: NodeId, frames: List<MeshFrame>) = Unit
}

class MeshManager(
    private val scope: CoroutineScope,
    private val selfId: NodeId,
    private val nodeDao: NodeDao,
    private val messageDao: MessageDao,
    private val relayEngine: RelayEngine,
    private val codec: FrameCodec,
    private val identity: MessageIdFactory,
    private val clock: TimeProvider,
    private val transport: MeshTransport,
    private val scheduler: BeaconScheduler,
    private val forwardingPolicy: ForwardingPolicy,
) {
    private val _state = MutableStateFlow(MeshState.stopped(selfId))
    val state: StateFlow<MeshState> = _state.asStateFlow()

    private var pruneJob: Job? = null

    fun start() {
        if (_state.value.isRunning) return
        _state.value = _state.value.copy(
            phase = MeshState.Phase.SCANNING,
            isScanning = true,
            isAdvertising = true,
            lastError = null,
        )
        scheduler.start()
        transport.start(
            onFrame = { frame, rssi -> scope.launch { handleFrame(frame, rssi) } },
            onBeacon = { payload, rssi -> scope.launch { onBeaconFrame(payload, rssi) } },
        )
        if (pruneJob == null) {
            pruneJob = scope.launch { pruneLoop() }
        }
    }

    fun stop() {
        scheduler.stop()
        transport.stop()
        pruneJob?.cancel()
        pruneJob = null
        _state.value = MeshState.stopped(selfId)
    }

    fun connectedNeighbors(): Set<NodeId> = transport.connectedPeers()

    suspend fun submitOutbound(
        destination: NodeId,
        text: String,
        ttl: Int,
        isSos: Boolean,
    ): MessageId {
        val safeTtl = ttl.coerceIn(1, MeshConfig.MAX_TTL)
        val payload = text.toByteArray(Charsets.UTF_8).let {
            require(it.size <= MeshConfig.MAX_PAYLOAD_BYTES) {
                "Pesan melebihi ${MeshConfig.MAX_PAYLOAD_BYTES} byte: ${it.size}"
            }
            it
        }
        val id = identity.next()
        val now = clock.now()

        messageDao.upsert(
            MessageEntity(
                messageKey = id.value,
                messageIdHex = id.display(),
                originNodeId = selfId.value,
                destinationId = destination.value,
                peerId = destination.value,
                payload = text,
                payloadBytes = payload,
                createdAt = now,
                initialTtl = safeTtl,
                ttl = safeTtl,
                hopCount = 0,
                status = MessageStatus.PENDING_FORWARD.wire,
                direction = MessageDirection.OUTGOING.wire,
                isSos = isSos,
                isReplay = false,
                firstForwardAt = null,
                deliveredAt = null,
                isReassembly = false,
                lastError = null,
            ),
        )

        val flags = when {
            isSos -> MsgFlag.SOS or MsgFlag.ACK_REQUESTED
            else -> MsgFlag.ACK_REQUESTED
        }
        val base = MeshFrame(
            messageId = id,
            destination = destination,
            ttl = safeTtl,
            hopCount = 0,
            flags = flags,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = payload.size,
            payloadChunk = payload,
        )
        val frames = Fragmenter.fragmentsOf(base, payload)
        scheduler.enqueueFrames(frames, urgent = isSos)

        if (isSos) {
            scheduler.setSosActive(true)
            _state.value = _state.value.copy(isSosActive = true)
        }
        return id
    }

    /**
 * Mengirim satu insiden darurat sebagai dua pesan yang terantai.
 *
 * Urutan penting: SOS_LOC dulu, lalu SOS_DETAIL. LOC muat dalam dua frame dan
 * membawa titik, jadi begitu fragmen pertamanya diterima penolong langsung tahu
 * ke mana harus datang, walaupun catatan lengkap belum sampai. DETAIL menyusul
 * dan menunjuk `incidentId` LOC, sehingga keduanya tampil sebagai satu kartu.
 *
 * Keduanya di-enqueue sebagai urgent supaya mendahului chat yang sedang antre.
 */
suspend fun submitSos(
        kind: SosKind,
        fix: LocationFix,
        victimCount: Int,
        hazards: Int,
        text: String,
        ttl: Int = MeshConfig.SOS_DEFAULT_TTL,
    ): MessageId {
        val safeTtl = ttl.coerceIn(1, MeshConfig.MAX_TTL)
        val now = clock.now()

        val locId = identity.next()
        val locPayload = SosCodec.encodeLoc(kind, fix, victimCount, hazards)
        persistAndAdvertise(
            id = locId,
            destination = NodeId.BROADCAST_ID,
            payload = locPayload,
            createdAt = now,
            ttl = safeTtl,
            flags = MsgFlag.SOS or MsgFlag.SOS_PAYLOAD or MsgFlag.SOS_LOC,
            sos = SosColumns(
                incidentKey = locId.value,
                kind = kind.wire,
                latE4 = (fix.point.latitude * MeshConfig.COORD_SCALE).toInt(),
                lonE4 = (fix.point.longitude * MeshConfig.COORD_SCALE).toInt(),
                accuracyM = fix.accuracyMeters,
                fixAgeSec = fix.ageSeconds,
                originBatteryPct = fix.batteryPct,
                victimCount = victimCount,
                hazards = hazards,
                isSosLoc = true,
                text = SosColumns.locSummary(fix.point, fix.accuracyMeters),
            ),
        )

        // Detail kosong tidak perlu dikirim: LOC sudah cukup untuk membuat pin.
        val detail = text.trim()
        if (detail.isNotEmpty()) {
            val detailId = identity.next()
            val detailPayload = SosCodec.encodeDetail(locId, kind, detail)
            persistAndAdvertise(
                id = detailId,
                destination = NodeId.BROADCAST_ID,
                payload = detailPayload,
                createdAt = now,
                ttl = safeTtl,
                flags = MsgFlag.SOS or MsgFlag.SOS_PAYLOAD,
                sos = SosColumns(
                    incidentKey = locId.value,
                    kind = kind.wire,
                    latE4 = null,
                    lonE4 = null,
                    accuracyM = null,
                    fixAgeSec = null,
                    originBatteryPct = null,
                    victimCount = 0,
                    hazards = 0,
                    isSosLoc = false,
                    text = detail,
                ),
            )
        }

        scheduler.setSosActive(true)
        _state.value = _state.value.copy(isSosActive = true)
        return locId
    }

    /**
     * Menyimpan satu pesan SOS ke database lalu menyiarkan frame-nya sebagai
     * urgent. Fragmentasi ditangani di sini supaya pemanggil cukup handing over
     * blob utuh.
     */
    private suspend fun persistAndAdvertise(
        id: MessageId,
        destination: NodeId,
        payload: ByteArray,
        createdAt: Long,
        ttl: Int,
        flags: Int,
        sos: SosColumns,
    ) {
        require(payload.size <= MeshConfig.MAX_PAYLOAD_BYTES) {
            "Payload SOS melebihi ${MeshConfig.MAX_PAYLOAD_BYTES} byte: ${payload.size}"
        }
        messageDao.upsert(
            MessageEntity(
                messageKey = id.value,
                messageIdHex = id.display(),
                originNodeId = selfId.value,
                destinationId = destination.value,
                peerId = destination.value,
                payload = sos.text,
                payloadBytes = payload,
                createdAt = createdAt,
                initialTtl = ttl,
                ttl = ttl,
                hopCount = 0,
                status = MessageStatus.IN_TRANSIT.wire,
                direction = MessageDirection.OUTGOING.wire,
                isSos = true,
                isReplay = false,
                firstForwardAt = null,
                deliveredAt = null,
                isReassembly = false,
                lastError = null,
                incidentKey = sos.incidentKey,
                sosKind = sos.kind,
                latE4 = sos.latE4,
                lonE4 = sos.lonE4,
                accuracyM = sos.accuracyM,
                fixAgeSec = sos.fixAgeSec,
                originBatteryPct = sos.originBatteryPct,
                victimCount = sos.victimCount,
                hazards = sos.hazards,
                isSosLoc = sos.isSosLoc,
            ),
        )

        val base = MeshFrame(
            messageId = id,
            destination = destination,
            ttl = ttl,
            hopCount = 0,
            flags = flags,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = payload.size,
            payloadChunk = payload,
        )
        scheduler.enqueueFrames(Fragmenter.fragmentsOf(base, payload), urgent = true)
    }

    suspend fun replay(message: MessageEntity) {
        // Flag struktural harus ikut di-replay. Kalau SOS_PAYLOAD/SOS_LOC hilang,
        // node penerima akan membaca blob koordinat sebagai teks UTF-8 dan
        // insidennya rusak meskipun frame-nya sampai.
        val structuralFlags = MsgFlag.SOS_PAYLOAD or
            (if (message.isSosLoc) MsgFlag.SOS_LOC else 0) or
            (if (message.isSos) MsgFlag.SOS else 0)
        val base = MeshFrame(
            messageId = MessageId(message.messageKey),
            destination = NodeId(message.destinationId),
            ttl = (message.ttl - 1).coerceAtLeast(0),
            hopCount = message.hopCount + 1,
            flags = MsgFlag.REPLAY or MsgFlag.ACK_REQUESTED or structuralFlags,
            fragIndex = 0,
            fragCount = 1,
            totalPayloadLen = message.payloadBytes.size,
            payloadChunk = message.payloadBytes,
        )
        messageDao.markForwarded(
            key = message.messageKey,
            status = MessageStatus.IN_TRANSIT.wire,
            ttl = base.ttl,
            hopCount = base.hopCount,
            now = clock.now(),
        )
        scheduler.enqueueFrames(
            Fragmenter.fragmentsOf(base, message.payloadBytes),
            urgent = message.isSos,
        )
    }

    suspend fun onBeaconFrame(payload: ByteArray, rssi: Int = SignalStrength.RSSI_NONE) {
        val beacon = runCatching { codec.decodeBeacon(payload) }.getOrNull() ?: return
        if (beacon.nodeId == selfId) return
        val now = clock.now()
        val existing = nodeDao.find(beacon.nodeId.value)
        if (existing == null) {
            nodeDao.upsert(
                NodeEntity(
                    nodeId = beacon.nodeId.value,
                    displayName = beacon.nodeId.toString(),
                    statusFlags = beacon.statusFlags,
                    batteryPct = beacon.batteryPct,
                    pendingCount = beacon.pendingCount,
                    gattPeerCount = beacon.gattPeerCount,
                    defaultTtl = beacon.defaultTtl,
                    nodeSeq = beacon.nodeSeq,
                    rssi = rssi,
                    isSelf = false,
                    firstSeenAt = now,
                    lastSeenAt = now,
                ),
            )
            onNodeDiscovered(beacon.nodeId)
        } else {
            nodeDao.upsert(
                existing.copy(
                    statusFlags = beacon.statusFlags,
                    batteryPct = beacon.batteryPct,
                    pendingCount = beacon.pendingCount,
                    gattPeerCount = beacon.gattPeerCount,
                    defaultTtl = beacon.defaultTtl,
                    nodeSeq = beacon.nodeSeq,
                    rssi = rssi,
                    lastSeenAt = now,
                ),
            )
        }
        // Selalu terapkan beacon terbaru supaya statusFlags konsisten dan
        // node yang baru ditemukan tetap menandai dirinya MESH_ACTIVE.
        relayEngine.applyBeacon(
            nodeId = beacon.nodeId,
            statusFlags = beacon.statusFlags,
            batteryPct = beacon.batteryPct,
            pendingCount = beacon.pendingCount,
            gattPeerCount = beacon.gattPeerCount,
            defaultTtl = beacon.defaultTtl,
            nodeSeq = beacon.nodeSeq,
        )
        refreshCounts()
    }

    private suspend fun handleFrame(frame: MeshFrame, rssi: Int) {
        when (val decision = relayEngine.onFrame(frame, Peer.ADVERTISING, rssi)) {
            is RelayDecision.Relayed -> route(decision.frames, frame.destination)
            is RelayDecision.Ack -> scheduler.enqueueUrgent(decision.frame)
            else -> Unit
        }
        refreshCounts()
    }

    /**
     * Dua tier: kalau tujuan sudah punya tautan GATT, kirim langsung; selain itu
     * siarkan lewat advertising dan biarkan node lain yang meneruskan.
     */
    private fun route(frames: List<MeshFrame>, destination: NodeId) {
        when (forwardingPolicy.decide(destination)) {
            Route.TIER2_GATT -> transport.sendOverLink(destination, frames)
            Route.TIER1_ADVERTISING -> scheduler.enqueueFrames(frames)
        }
    }

    private suspend fun onNodeDiscovered(nodeId: NodeId) {
        val pending = messageDao.pendingForwardsTo(nodeId.value)
        for (message in pending) {
            if (message.ttl <= 0) continue
            replay(message)
        }
    }

    private suspend fun refreshCounts() {
        _state.value = _state.value.copy(
            neighborCount = nodeDao.countNeighbors(),
            pendingForwardCount = messageDao.countPendingForwards(),
            gattPeerCount = transport.connectedPeers().size,
        )
    }

    /** Dipanggil lapisan BLE saat advertising sudah benar-benar aktif. */
    fun setActive() {
        _state.value = _state.value.copy(phase = MeshState.Phase.ACTIVE)
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(
            phase = MeshState.Phase.ERROR,
            isScanning = false,
            isAdvertising = false,
            lastError = message,
        )
    }

    private suspend fun pruneLoop() {
        while (currentCoroutineContext().isActive) {
            delay(MeshConfig.PRUNE_INTERVAL_MS)
            relayEngine.pruneStale()
        }
    }
}

fun interface MessageIdFactory {
    fun next(): MessageId
}
