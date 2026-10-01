package com.resqmesh.data.db.seen

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Catatan frame individual (messageId + fragIndex). Dipisah dari
 * [SeenMessageEntity] supaya pesan multi-fragment tetap bisa di-re-assemble:
 * duplikat pada level frame tidak boleh memblokir fragmen lain.
 */
@Entity(
    tableName = "SeenFrameEntity",
    primaryKeys = ["messageKey", "fragIndex"],
    indices = [Index(value = ["expiresAt"])],
)
data class SeenFrameEntity(
    @ColumnInfo(name = "messageKey") val messageKey: Long,
    @ColumnInfo(name = "fragIndex") val fragIndex: Int,
    @ColumnInfo(name = "hopCount") val hopCount: Int,
    @ColumnInfo(name = "firstSeenAt") val firstSeenAt: Long,
    @ColumnInfo(name = "expiresAt") val expiresAt: Long,
)
