package com.resqmesh.data.db.seen

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
@JvmSuppressWildcards
interface SeenMessageDao {

    /** Mengembalikan -1 bila baris sudah ada, yaitu pesan ini duplikat. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun tryInsert(entry: SeenMessageEntity): Long

    @Query("SELECT * FROM SeenMessageEntity WHERE messageKey = :key LIMIT 1")
    suspend fun find(key: Long): SeenMessageEntity?

    @Query("UPDATE SeenMessageEntity SET hopCount = MIN(hopCount, :hop) WHERE messageKey = :key")
    suspend fun lowerHopIfBetter(key: Long, hop: Int): Int

    @Query("UPDATE SeenMessageEntity SET forwardCount = forwardCount + 1 WHERE messageKey = :key")
    suspend fun incrementForwardCount(key: Long): Int

    @Query("DELETE FROM SeenMessageEntity WHERE expiresAt < :now")
    suspend fun purgeExpired(now: Long): Int

    @Query("SELECT COUNT(*) FROM SeenMessageEntity")
    suspend fun count(): Int
}
