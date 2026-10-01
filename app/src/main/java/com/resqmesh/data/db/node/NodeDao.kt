package com.resqmesh.data.db.node

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {

    @Upsert
    suspend fun upsert(node: NodeEntity)

    @Query("SELECT * FROM NodeEntity WHERE isSelf = 1 LIMIT 1")
    suspend fun self(): NodeEntity?

    @Query("SELECT * FROM NodeEntity WHERE isSelf = 1 LIMIT 1")
    fun observeSelf(): Flow<NodeEntity?>

    @Query("SELECT * FROM NodeEntity WHERE isSelf = 0 ORDER BY lastSeenAt DESC")
    fun observeNeighbors(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM NodeEntity WHERE nodeId = :id LIMIT 1")
    suspend fun find(id: Long): NodeEntity?

    @Query("UPDATE NodeEntity SET rssi = :rssi, lastSeenAt = :now WHERE nodeId = :id")
    suspend fun touchRssi(id: Long, rssi: Int, now: Long)

    @Query("""
        UPDATE NodeEntity
        SET statusFlags = :flags,
            batteryPct = :battery,
            pendingCount = :pending,
            gattPeerCount = :peers,
            defaultTtl = :ttl,
            nodeSeq = :seq
        WHERE nodeId = :id
    """)
    suspend fun applyBeacon(
        id: Long,
        flags: Int,
        battery: Int,
        pending: Int,
        peers: Int,
        ttl: Int,
        seq: Long,
    )

    @Query("DELETE FROM NodeEntity WHERE isSelf = 0 AND lastSeenAt < :cutoff")
    suspend fun pruneStale(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM NodeEntity WHERE isSelf = 1")
    suspend fun countSelf(): Int

    @Query("SELECT COUNT(*) FROM NodeEntity WHERE isSelf = 0")
    suspend fun countNeighbors(): Int
}
