package com.resqmesh.data.db.message

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
@JvmSuppressWildcards
interface MessageHopDao {

    @Insert
    suspend fun insert(hop: MessageHopEntity): Long

    @Query("""
        SELECT * FROM MessageHopEntity
        WHERE messageKey = :messageKey
        ORDER BY hopIndex ASC
    """)
    suspend fun path(messageKey: Long): List<MessageHopEntity>

    @Query("DELETE FROM MessageHopEntity WHERE messageKey = :messageKey")
    suspend fun deleteForMessage(messageKey: Long): Int
}
