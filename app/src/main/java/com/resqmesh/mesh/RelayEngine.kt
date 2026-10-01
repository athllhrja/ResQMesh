package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.db.message.MessageDao
import com.resqmesh.data.db.message.MessageEntity
import com.resqmesh.data.db.message.MessageHopDao
import com.resqmesh.data.db.message.MessageHopEntity
import com.resqmesh.data.db.node.NodeDao
import com.resqmesh.data.db.toDomain
import com.resqmesh.domain.model.Fragmenter
import com.resqmesh.domain.model.Message
import com.resqmesh.domain.model.MessageDirection
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MessageStatus
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MsgFlag
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.NodeStatusFlags

enum class Peer(val label: String) {
    ADVERTISING("advert"),
    CONNECTED("gatt"),
    SELF("self"),
}

enum class RejectReason {
    DUPLICATE,
    TTL_EXPIRED,
    FRAGMENT_INCOMPLETE,
    SELF_ORIGIN,
    MALFORMED,
    FLOOD_SUPPRESSED,
    DESTINATION_UNKNOWN,
}

sealed interface RelayDecision {
    data object Ignore : RelayDecision
    data class Consume(val message: Message) : RelayDecision
    data class Relayed(val frames: List<MeshFrame>) : RelayDecision
    data class ForwardLater(val message: Message) : RelayDecision
    data class Ack(val frame: MeshFrame) : RelayDecision
    data class Reject(val reason: RejectReason) : RelayDecision
}

class RelayEngine(
    private val selfId: NodeId,
    private val duplicateGuard: DuplicateGuard,
    private val ttlPolicy: TtlPolicy,
    private val assembler: FragmentAssembler,
    private val ackTracker: AckTracker,
    private val messageDao: MessageDao,
    private val hopDao: MessageHopDao,
    private val nodeDao: NodeDao,
    private val clock: TimeProvider,
) {

    suspend fun onFrame(frame: MeshFrame, peer: Peer, rssi: Int): RelayDecision {
        val now = clock.now()

        // ACK harus diperiksa lebih dulu: frame-nya memakai messageId pesan
        // asli, jadi origin-nya adalah pengirim, bukan node kita.
        if (frame.isAck) {
            if (frame.messageId.origin == selfId) {
                ackTracker.onAck(frame.messageId)
                return RelayDecision.Ignore
            }
            // ACK tidak punya field tujuan sendiri, jadi address-nya broadcast.
            // Node perantara tetap harus meneruskannya, kalau tidak pengirim asli
            // tidak akan pernah tahu pesannya sampai. Batasnya TTL dan dedupe
            // per-fragmen, bukan addr tujuan.
            if (frame.destination == selfId) {
                return RelayDecision.Ignore
            }
            if (!ttlPolicy.shouldForward(frame.ttl, frame.hopCount, frame.inferredInitialTtl())) {
                return RelayDecision.Reject(RejectReason.TTL_EXPIRED)
            }
            val nextTtl = ttlPolicy.nextTtl(frame.ttl)
            val nextHop = ttlPolicy.nextHop(frame.hopCount)
            return RelayDecision.Relayed(
                listOf(frame.forwarded(nextTtl = nextTtl, nextHop = nextHop)),
            )
        }

        if (frame.messageId.origin == selfId) {
            return RelayDecision.Reject(RejectReason.SELF_ORIGIN)
        }

        duplicateGuard.trackMessage(frame.messageId, frame.hopCount, frame.isSos)
        val verdict = duplicateGuard.registerFrame(frame.messageId, frame.fragIndex, frame.hopCount)
        val stored = messageDao.findByKey(frame.messageId.value)

        if (peer != Peer.SELF) {
            hopDao.insert(
                MessageHopEntity(
                    messageKey = frame.messageId.value,
                    nodeId = selfId.value,
                    hopIndex = frame.hopCount + 1,
                    rssi = rssi,
                    latencyMs = stored?.let { now - it.createdAt },
                    observedAt = now,
                ),
            )
        }

        when (val assembly = assembler.accept(frame)) {
            is AssemblyResult.Malformed -> return RelayDecision.Reject(RejectReason.MALFORMED)
            is AssemblyResult.Stale -> return RelayDecision.Ignore
            is AssemblyResult.Incomplete -> {
                if (stored == null || !stored.isReassembly) {
                    upsertReassembly(frame, stored, now)
                }
                return RelayDecision.Ignore
            }

            is AssemblyResult.Complete -> {
                return finalize(frame, assembly.payload, stored, verdict, now)
            }
        }
    }

    private suspend fun finalize(
        frame: MeshFrame,
        payload: ByteArray,
        stored: MessageEntity?,
        verdict: FrameVerdict,
        now: Long,
    ): RelayDecision {
        val isDestination = frame.destination == selfId

        if (isDestination) {
            val entity = buildEntity(
                frame = frame,
                payload = payload,
                createdAt = stored?.createdAt ?: now,
                initialTtl = stored?.initialTtl ?: frame.inferredInitialTtl(),
                ttl = frame.ttl,
                hopCount = frame.hopCount,
                status = MessageStatus.DELIVERED,
                direction = MessageDirection.INCOMING,
                deliveredAt = now,
            )
            messageDao.upsert(entity)
            return RelayDecision.Ack(buildAck(frame))
        }

        // Frame identik yang sudah pernah diproses ulang tidak perlu diteruskan
        // lagi. Pesan yang fragmennya berbeda tetap lolos karena kunci dedupe-nya
        // per-fragmen, bukan per-pesan.
        if (verdict is FrameVerdict.Repeated) {
            return RelayDecision.Reject(RejectReason.DUPLICATE)
        }

        if (duplicateGuard.shouldSuppress(frame.messageId, frame.hopCount)) {
            messageDao.upsert(
                buildEntity(
                    frame = frame,
                    payload = payload,
                    createdAt = stored?.createdAt ?: now,
                    initialTtl = stored?.initialTtl ?: frame.inferredInitialTtl(),
                    ttl = frame.ttl,
                    hopCount = frame.hopCount,
                    status = MessageStatus.EXPIRED,
                    direction = MessageDirection.RELAYED,
                    deliveredAt = null,
                ),
            )
            return RelayDecision.Reject(RejectReason.FLOOD_SUPPRESSED)
        }

        val initialTtl = stored?.initialTtl ?: frame.inferredInitialTtl()
        if (!ttlPolicy.shouldForward(frame.ttl, frame.hopCount, initialTtl)) {
            messageDao.upsert(
                buildEntity(
                    frame = frame,
                    payload = payload,
                    createdAt = stored?.createdAt ?: now,
                    initialTtl = initialTtl,
                    ttl = 0,
                    hopCount = frame.hopCount,
                    status = MessageStatus.EXPIRED,
                    direction = MessageDirection.RELAYED,
                    deliveredAt = null,
                ),
            )
            return RelayDecision.Reject(RejectReason.TTL_EXPIRED)
        }

        // Broadcast tidak pernah "ditemukan" di tabel node, tapi tetap harus
        // disiarkan ulang oleh node perantara.
        val destinationKnown =
            frame.destination.isBroadcast || nodeDao.find(frame.destination.value) != null
        val status = if (destinationKnown) {
            MessageStatus.IN_TRANSIT
        } else {
            MessageStatus.PENDING_FORWARD
        }
        val nextTtl = ttlPolicy.nextTtl(frame.ttl)
        val nextHop = ttlPolicy.nextHop(frame.hopCount)

        val entity = buildEntity(
            frame = frame,
            payload = payload,
            createdAt = stored?.createdAt ?: now,
            initialTtl = initialTtl,
            ttl = nextTtl,
            hopCount = nextHop,
            status = status,
            direction = MessageDirection.RELAYED,
            deliveredAt = null,
        )
        messageDao.upsert(entity)

        if (!destinationKnown) {
            return RelayDecision.ForwardLater(entity.toDomain())
        }

        duplicateGuard.noteForward(frame.messageId, nextHop)
        val forwarded = frame.forwarded(nextTtl = nextTtl, nextHop = nextHop)
        return RelayDecision.Relayed(Fragmenter.fragmentsOf(forwarded, payload))
    }

    private suspend fun upsertReassembly(frame: MeshFrame, stored: MessageEntity?, now: Long) {
        messageDao.upsert(
            MessageEntity(
                messageKey = frame.messageId.value,
                messageIdHex = frame.messageId.display(),
                originNodeId = frame.messageId.origin.value,
                destinationId = frame.destination.value,
                peerId = stored?.peerId ?: frame.messageId.origin.value,
                payload = "",
                payloadBytes = ByteArray(0),
                createdAt = stored?.createdAt ?: now,
                initialTtl = stored?.initialTtl ?: frame.inferredInitialTtl(),
                ttl = stored?.ttl ?: frame.ttl,
                hopCount = frame.hopCount,
                status = MessageStatus.AWAITING_FRAGMENTS.wire,
                direction = stored?.direction ?: MessageDirection.RELAYED.wire,
                isSos = frame.isSos,
                isReplay = frame.isReplay,
                firstForwardAt = null,
                deliveredAt = null,
                isReassembly = true,
                lastError = null,
                // Insiden harus tetap bisa dirakit walau baru satu fragmen yang
                // sampai, jadi kolom SOS diambil dari baris sebelumnya bila ada.
                incidentKey = stored?.incidentKey ?: if (frame.isSos) frame.messageId.value else null,
                sosKind = stored?.sosKind ?: 0,
                latE4 = stored?.latE4,
                lonE4 = stored?.lonE4,
                accuracyM = stored?.accuracyM,
                fixAgeSec = stored?.fixAgeSec,
                originBatteryPct = stored?.originBatteryPct,
                victimCount = stored?.victimCount ?: 0,
                hazards = stored?.hazards ?: 0,
                isSosLoc = stored?.isSosLoc ?: false,
            ),
        )
    }

    private fun buildEntity(
        frame: MeshFrame,
        payload: ByteArray,
        createdAt: Long,
        initialTtl: Int,
        ttl: Int,
        hopCount: Int,
        status: MessageStatus,
        direction: MessageDirection,
        deliveredAt: Long?,
    ): MessageEntity {
        val sos = readSosColumns(frame, payload)
        return MessageEntity(
            messageKey = frame.messageId.value,
            messageIdHex = frame.messageId.display(),
            originNodeId = frame.messageId.origin.value,
            destinationId = frame.destination.value,
            peerId = frame.messageId.origin.value,
            payload = if (frame.isSosPayload) sos.text else payload.toString(Charsets.UTF_8),
            payloadBytes = payload,
            createdAt = createdAt,
            initialTtl = initialTtl,
            ttl = ttl,
            hopCount = hopCount,
            status = status.wire,
            direction = direction.wire,
            isSos = frame.isSos,
            isReplay = frame.isReplay,
            firstForwardAt = null,
            deliveredAt = deliveredAt,
            isReassembly = false,
            lastError = sos.error,
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
        )
    }

    /**
     * ACK adalah frame tunggal tanpa payload. `messageId` pada frame inilah yang
     * menunjuk pesan asli, jadi tidak perlu memuat teks apa pun.
     */
    private fun buildAck(frame: MeshFrame): MeshFrame = MeshFrame(
        messageId = frame.messageId,
        destination = NodeId.BROADCAST_ID,
        ttl = frame.ttl,
        hopCount = frame.hopCount,
        flags = frame.flags or MsgFlag.IS_ACK,
        fragIndex = 0,
        fragCount = 1,
        totalPayloadLen = 0,
        payloadChunk = ByteArray(0),
    )

    suspend fun applyBeacon(
        nodeId: NodeId,
        statusFlags: Int,
        batteryPct: Int,
        pendingCount: Int,
        gattPeerCount: Int,
        defaultTtl: Int,
        nodeSeq: Long,
    ) {
        nodeDao.applyBeacon(
            id = nodeId.value,
            flags = statusFlags or NodeStatusFlags.MESH_ACTIVE,
            battery = batteryPct,
            pending = pendingCount,
            peers = gattPeerCount,
            ttl = defaultTtl,
            seq = nodeSeq,
        )
    }

    suspend fun pruneStale() {
        val now = clock.now()
        nodeDao.pruneStale(now - MeshConfig.NODE_STALE_MS)
        assembler.evictExpired(now)
        duplicateGuard.pruneExpired()
    }
}
