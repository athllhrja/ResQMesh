package com.resqmesh.data.db.seen

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "SeenMessageEntity",
    indices = [Index(value = ["expiresAt"]), Index(value = ["originNodeId", "msgSeq"])],
)
data class SeenMessageEntity(
    @PrimaryKey @ColumnInfo(name = "messageKey") val messageKey: Long,
    @ColumnInfo(name = "originNodeId") val originNodeId: Long,
    @ColumnInfo(name = "msgSeq") val msgSeq: Int,
    @ColumnInfo(name = "firstSeenAt") val firstSeenAt: Long,
    @ColumnInfo(name = "expiresAt") val expiresAt: Long,
    @ColumnInfo(name = "hopCount") val hopCount: Int,
    @ColumnInfo(name = "forwardCount") val forwardCount: Int,

    /**
     * Menentukan forwarded mana yang dipakai: pesan biasa dibatasi
     * [MeshConfig.NORMAL_FORWARD_LIMIT] dengan bias hop, SOS memakai
     * [MeshConfig.SOS_FLOOD_FORWARD_LIMIT] tanpa bias hop.
     */
    @ColumnInfo(name = "isSos") val isSos: Boolean = false,
)
