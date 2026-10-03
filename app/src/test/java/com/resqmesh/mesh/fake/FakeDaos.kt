package com.resqmesh.mesh.fake

import com.resqmesh.data.db.message.MessageDao
import com.resqmesh.data.db.message.MessageEntity
import com.resqmesh.data.db.message.MessageHopDao
import com.resqmesh.data.db.message.MessageHopEntity
import com.resqmesh.data.db.node.NodeDao
import com.resqmesh.data.db.node.NodeEntity
import com.resqmesh.data.db.seen.SeenFrameDao
import com.resqmesh.data.db.seen.SeenFrameEntity
import com.resqmesh.data.db.seen.SeenMessageDao
import com.resqmesh.data.db.seen.SeenMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class InMemoryNodeDao : NodeDao {
    val rows = linkedMapOf<Long, NodeEntity>()

    override suspend fun upsert(node: NodeEntity): Long {
        rows[node.nodeId] = rows[node.nodeId]?.copy(
            displayName = node.displayName,
            statusFlags = node.statusFlags,
            batteryPct = node.batteryPct,
            pendingCount = node.pendingCount,
            gattPeerCount = node.gattPeerCount,
            defaultTtl = node.defaultTtl,
            nodeSeq = node.nodeSeq,
            rssi = node.rssi,
            isSelf = node.isSelf,
            firstSeenAt = node.firstSeenAt,
            lastSeenAt = node.lastSeenAt,
        ) ?: node
        return node.nodeId
    }

    override suspend fun self(): NodeEntity? = rows.values.firstOrNull { it.isSelf }

    override fun observeSelf(): Flow<NodeEntity?> = flowOf(rows.values.firstOrNull { it.isSelf })

    override fun observeNeighbors(): Flow<List<NodeEntity>> = flowOf(neighbors())

    override suspend fun find(id: Long): NodeEntity? = rows[id]

    override suspend fun touchRssi(id: Long, rssi: Int, now: Long): Int {
        val existing = rows[id] ?: return 0
        rows[id] = existing.copy(rssi = rssi, lastSeenAt = now)
        return 1
    }

    override suspend fun applyBeacon(
        id: Long,
        flags: Int,
        battery: Int,
        pending: Int,
        peers: Int,
        ttl: Int,
        seq: Long,
    ): Int {
        val existing = rows[id] ?: return 0
        rows[id] = existing.copy(
            statusFlags = flags,
            batteryPct = battery,
            pendingCount = pending,
            gattPeerCount = peers,
            defaultTtl = ttl,
            nodeSeq = seq,
        )
        return 1
    }

    override suspend fun pruneStale(cutoff: Long): Int {
        val stale = rows.values.filter { !it.isSelf && it.lastSeenAt < cutoff }
        stale.forEach { rows.remove(it.nodeId) }
        return stale.size
    }

    override suspend fun countSelf(): Int = rows.values.count { it.isSelf }

    override suspend fun countNeighbors(): Int = neighbors().size

    private fun neighbors(): List<NodeEntity> =
        rows.values.filter { !it.isSelf }.sortedByDescending { it.lastSeenAt }
}

class InMemoryMessageDao : MessageDao {
    val rows = linkedMapOf<Long, MessageEntity>()

    override suspend fun upsert(message: MessageEntity): Long {
        val existing = rows[message.messageKey]
        rows[message.messageKey] = if (existing == null) {
            message
        } else {
            message.copy(
                messageIdHex = existing.messageIdHex,
                peerId = existing.peerId,
                createdAt = existing.createdAt,
                firstForwardAt = existing.firstForwardAt ?: message.firstForwardAt,
            )
        }
        return message.messageKey
    }

    override suspend fun findByKey(key: Long): MessageEntity? = rows[key]

    override fun observeByKey(key: Long): Flow<MessageEntity?> = flowOf(rows[key])

    override suspend fun pendingForwards(limit: Int): List<MessageEntity> =
        rows.values.filter { it.status == "PENDING_FORWARD" }.take(limit)

    override suspend fun pendingForwardsTo(destinationId: Long): List<MessageEntity> =
        rows.values.filter { it.status == "PENDING_FORWARD" && it.destinationId == destinationId }

    override fun observePendingForwardCount(): Flow<Int> =
        flowOf(rows.values.count { it.status == "PENDING_FORWARD" })

    override suspend fun countPendingForwards(): Int =
        rows.values.count { it.status == "PENDING_FORWARD" }

    override suspend fun markForwarded(key: Long, status: String, ttl: Int, hopCount: Int, now: Long): Int {
        val existing = rows[key] ?: return 0
        rows[key] = existing.copy(
            status = status,
            ttl = ttl,
            hopCount = hopCount,
            firstForwardAt = existing.firstForwardAt ?: now,
        )
        return 1
    }

    override suspend fun markDelivered(key: Long, now: Long): Int {
        val row = rows[key] ?: return 0
        if (row.status != "PENDING_FORWARD" && row.status != "IN_TRANSIT") return 0
        rows[key] = row.copy(status = "DELIVERED", deliveredAt = now)
        return 1
    }

    override suspend fun markAcked(key: Long): Int {
        val row = rows[key] ?: return 0
        if (row.status == "ACKED") return 0
        rows[key] = row.copy(status = "ACKED")
        return 1
    }

    override suspend fun markExpired(key: Long): Int {
        val row = rows[key] ?: return 0
        rows[key] = row.copy(status = "EXPIRED", ttl = 0)
        return 1
    }

    override suspend fun purgeOlderThan(cutoff: Long): Int {
        val old = rows.values.filter { it.createdAt < cutoff }
        old.forEach { rows.remove(it.messageKey) }
        return old.size
    }

    override fun observeConversation(peerId: Long, limit: Int): Flow<List<MessageEntity>> =
        flowOf(rows.values.filter { it.peerId == peerId && !it.isReassembly }.take(limit))

    override fun observeHistory(limit: Int): Flow<List<MessageEntity>> =
        flowOf(rows.values.filter { !it.isReassembly }.take(limit))

    override fun observeSosRows(limit: Int): Flow<List<MessageEntity>> =
        flowOf(rows.values.filter { it.isSos && !it.isReassembly }.sortedByDescending { it.createdAt }.take(limit))

    override suspend fun findByIncident(incidentKey: Long): MessageEntity? =
        rows.values.firstOrNull { it.incidentKey == incidentKey }

    override fun observeSosIncidentCount(): Flow<Int> =
        flowOf(rows.values.filter { it.isSos }.mapNotNull { it.incidentKey }.distinct().size)
}

class InMemoryMessageHopDao : MessageHopDao {
    val rows = mutableListOf<MessageHopEntity>()

    override suspend fun insert(hop: MessageHopEntity): Long {
        rows += hop
        return rows.size.toLong()
    }

    override suspend fun path(messageKey: Long): List<MessageHopEntity> =
        rows.filter { it.messageKey == messageKey }.sortedBy { it.hopIndex }

    override suspend fun deleteForMessage(messageKey: Long): Int {
        val before = rows.size
        rows.removeAll { it.messageKey == messageKey }
        return before - rows.size
    }
}

class InMemorySeenMessageDao : SeenMessageDao {
    val rows = linkedMapOf<Long, SeenMessageEntity>()

    /** Membalik perilaku INSERT OR IGNORE Room: -1 bila sudah ada. */
    override suspend fun tryInsert(entry: SeenMessageEntity): Long {
        if (rows.containsKey(entry.messageKey)) return -1L
        rows[entry.messageKey] = entry
        return entry.messageKey
    }

    override suspend fun find(key: Long): SeenMessageEntity? = rows[key]

    override suspend fun lowerHopIfBetter(key: Long, hop: Int): Int {
        val existing = rows[key] ?: return 0
        rows[key] = existing.copy(hopCount = minOf(existing.hopCount, hop))
        return 1
    }

    override suspend fun incrementForwardCount(key: Long): Int {
        val existing = rows[key] ?: return 0
        rows[key] = existing.copy(forwardCount = existing.forwardCount + 1)
        return 1
    }

    override suspend fun purgeExpired(now: Long): Int {
        val expired = rows.values.filter { it.expiresAt < now }
        expired.forEach { rows.remove(it.messageKey) }
        return expired.size
    }

    override suspend fun count(): Int = rows.size
}

class InMemorySeenFrameDao : SeenFrameDao {
    private val rows = linkedMapOf<Pair<Long, Int>, SeenFrameEntity>()

    override suspend fun tryInsert(entry: SeenFrameEntity): Long {
        val key = entry.messageKey to entry.fragIndex
        if (rows.containsKey(key)) return -1L
        rows[key] = entry
        return 1L
    }

    override suspend fun purgeExpired(now: Long): Int {
        val expired = rows.values.filter { it.expiresAt < now }
        expired.forEach { rows.remove(it.messageKey to it.fragIndex) }
        return expired.size
    }

    override suspend fun count(): Int = rows.size
}
