package com.resqmesh.data.db.seen

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SeenFrameDao {

    /** Mengembalikan -1 bila frame persis ini sudah pernah masuk. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun tryInsert(entry: SeenFrameEntity): Long

    @Query("DELETE FROM SeenFrameEntity WHERE expiresAt < :now")
    suspend fun purgeExpired(now: Long): Int

    @Query("SELECT COUNT(*) FROM SeenFrameEntity")
    suspend fun count(): Int
}
