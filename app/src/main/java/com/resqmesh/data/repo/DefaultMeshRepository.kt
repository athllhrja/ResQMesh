package com.resqmesh.data.repo

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.db.message.MessageDao
import com.resqmesh.data.db.message.MessageEntity
import com.resqmesh.data.db.node.NodeDao
import com.resqmesh.data.db.seen.SeenMessageDao
import com.resqmesh.data.db.toDomain
import com.resqmesh.data.db.toSosIncidents
import com.resqmesh.domain.MeshRepository
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MeshState
import com.resqmesh.domain.model.Message
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.Node
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosIncident
import com.resqmesh.domain.model.SosKind
import com.resqmesh.mesh.MeshManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class DefaultMeshRepository(
    private val manager: MeshManager,
    private val nodeDao: NodeDao,
    private val messageDao: MessageDao,
    private val seenDao: SeenMessageDao,
    private val clock: TimeProvider,
) : MeshRepository {

    override fun observeSelf(): Flow<Node> =
        nodeDao.observeSelf().filterNotNull().map { it.toDomain() }

    override fun observeNeighbors(): Flow<List<Node>> =
        nodeDao.observeNeighbors().map { list -> list.map { it.toDomain() } }

    override fun observeHistory(limit: Int): Flow<List<Message>> =
        messageDao.observeHistory(limit).map { list -> list.map { it.toDomain() } }

    override fun observeConversation(peerId: NodeId, limit: Int): Flow<List<Message>> =
        messageDao.observeConversation(peerId.value, limit).map { list -> list.map { it.toDomain() } }

    override fun observeSosIncidents(limit: Int): Flow<List<SosIncident>> =
        messageDao.observeSosRows(limit).map { rows -> rows.toSosIncidents() }

    override fun observeSelfSosIncidents(limit: Int): Flow<List<SosIncident>> =
        observeSosIncidents(limit).map { list -> list.filter { it.isOutgoing } }

    override fun observePeerSosIncidents(limit: Int): Flow<List<SosIncident>> =
        observeSosIncidents(limit).map { list -> list.filter { !it.isOutgoing } }

    override fun observeMeshState(): Flow<MeshState> = manager.state

    override fun observePendingForwardCount(): Flow<Int> = messageDao.observePendingForwardCount()

    override suspend fun sendTo(
        destination: NodeId,
        text: String,
        ttl: Int,
        isSos: Boolean,
    ): MessageId = manager.submitOutbound(destination, text, ttl, isSos)

    override suspend fun broadcastSos(text: String, ttl: Int): MessageId =
        manager.submitOutbound(NodeId.BROADCAST_ID, text, ttl, isSos = true)

    override suspend fun submitSos(
        kind: SosKind,
        fix: LocationFix,
        victimCount: Int,
        hazards: Int,
        text: String,
        ttl: Int,
    ): MessageId = manager.submitSos(
        kind = kind,
        fix = fix,
        victimCount = victimCount,
        hazards = hazards,
        text = text,
        ttl = ttl,
    )

    override suspend fun rebroadcastPending(): Int {
        val pending = messageDao.pendingForwards(limit = 16)
        var replayed = 0
        for (message: MessageEntity in pending) {
            if (message.ttl <= 0) {
                messageDao.markExpired(message.messageKey)
                continue
            }
            val entry = seenDao.find(message.messageKey)
            val limit = if (message.isSos) {
                MeshConfig.SOS_FLOOD_FORWARD_LIMIT
            } else {
                MeshConfig.NORMAL_FORWARD_LIMIT
            }
            if (entry != null && entry.forwardCount >= limit) {
                messageDao.markExpired(message.messageKey)
                continue
            }
            manager.replay(message)
            replayed++
        }
        return replayed
    }

    override suspend fun prune(): Int {
        val now = clock.now()
        val messages = messageDao.purgeOlderThan(now - MeshConfig.HISTORY_RETENTION_MS)
        val seen = seenDao.purgeExpired(now)
        val nodes = nodeDao.pruneStale(now - MeshConfig.NODE_STALE_MS)
        return messages + seen + nodes
    }
}
