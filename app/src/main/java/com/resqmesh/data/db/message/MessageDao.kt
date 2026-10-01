package com.resqmesh.data.db.message

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM MessageEntity WHERE messageKey = :key LIMIT 1")
    suspend fun findByKey(key: Long): MessageEntity?

    @Query("SELECT * FROM MessageEntity WHERE messageKey = :key LIMIT 1")
    fun observeByKey(key: Long): Flow<MessageEntity?>

    @Query("""
        SELECT * FROM MessageEntity
        WHERE status = 'PENDING_FORWARD'
        ORDER BY isSos DESC, createdAt ASC
        LIMIT :limit
    """)
    suspend fun pendingForwards(limit: Int = 8): List<MessageEntity>

    @Query("""
        SELECT * FROM MessageEntity
        WHERE status = 'PENDING_FORWARD'
          AND destinationId = :destinationId
        ORDER BY isSos DESC, createdAt ASC
    """)
    suspend fun pendingForwardsTo(destinationId: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM MessageEntity WHERE status = 'PENDING_FORWARD'")
    fun observePendingForwardCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM MessageEntity WHERE status = 'PENDING_FORWARD'")
    suspend fun countPendingForwards(): Int

    @Query("""
        UPDATE MessageEntity
        SET status = :status,
            ttl = :ttl,
            hopCount = :hopCount,
            firstForwardAt = COALESCE(firstForwardAt, :now)
        WHERE messageKey = :key
    """)
    suspend fun markForwarded(key: Long, status: String, ttl: Int, hopCount: Int, now: Long)

    @Query("""
        UPDATE MessageEntity
        SET status = 'DELIVERED', deliveredAt = :now
        WHERE messageKey = :key AND status IN ('PENDING_FORWARD','IN_TRANSIT')
    """)
    suspend fun markDelivered(key: Long, now: Long): Int

    @Query("UPDATE MessageEntity SET status = 'ACKED' WHERE messageKey = :key AND status != 'ACKED'")
    suspend fun markAcked(key: Long): Int

    @Query("UPDATE MessageEntity SET status = 'EXPIRED', ttl = 0 WHERE messageKey = :key")
    suspend fun markExpired(key: Long): Int

    @Query("DELETE FROM MessageEntity WHERE createdAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long): Int

    @Query("""
        SELECT * FROM MessageEntity
        WHERE peerId = :peerId AND isReassembly = 0
        ORDER BY createdAt DESC LIMIT :limit
    """)
    fun observeConversation(peerId: Long, limit: Int = 100): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM MessageEntity
        WHERE isReassembly = 0
        ORDER BY createdAt DESC LIMIT :limit
    """)
    fun observeHistory(limit: Int = 200): Flow<List<MessageEntity>>

    /**
     * Baris pesan yang membentuk insiden darurat. Dua pesan per insiden, jadi
     * repository yang menggabungkannya menjadi satu [SosIncident].
     */
    @Query("""
        SELECT * FROM MessageEntity
        WHERE isSos = 1 AND isReassembly = 0
        ORDER BY createdAt DESC LIMIT :limit
    """)
    fun observeSosRows(limit: Int = 100): Flow<List<MessageEntity>>

    @Query("SELECT * FROM MessageEntity WHERE incidentKey = :incidentKey LIMIT 1")
    suspend fun findByIncident(incidentKey: Long): MessageEntity?

    @Query("SELECT COUNT(DISTINCT incidentKey) FROM MessageEntity WHERE isSos = 1")
    fun observeSosIncidentCount(): Flow<Int>
}
