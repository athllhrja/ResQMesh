package com.resqmesh.data.db.message

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "MessageEntity",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["status"]),
        Index(value = ["peerId", "createdAt"]),
        Index(value = ["status", "destinationId"]),
        Index(value = ["isSos", "createdAt"]),
        Index(value = ["incidentKey"]),
    ],
)
data class MessageEntity(
    @PrimaryKey @ColumnInfo(name = "messageKey") val messageKey: Long,
    @ColumnInfo(name = "messageIdHex") val messageIdHex: String,
    @ColumnInfo(name = "originNodeId") val originNodeId: Long,
    @ColumnInfo(name = "destinationId") val destinationId: Long,
    @ColumnInfo(name = "peerId") val peerId: Long,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "payloadBytes") val payloadBytes: ByteArray,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "initialTtl") val initialTtl: Int,
    @ColumnInfo(name = "ttl") val ttl: Int,
    @ColumnInfo(name = "hopCount") val hopCount: Int,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "isSos") val isSos: Boolean,
    @ColumnInfo(name = "isReplay") val isReplay: Boolean,
    @ColumnInfo(name = "firstForwardAt") val firstForwardAt: Long?,
    @ColumnInfo(name = "deliveredAt") val deliveredAt: Long?,
    @ColumnInfo(name = "isReassembly") val isReassembly: Boolean,
    @ColumnInfo(name = "lastError") val lastError: String?,

    /**
     * Kolom insiden darurat. Untuk pesan biasa semuanya bernilai null/0 sehingga
     * tidak menambah beban pada jalur chat.
     *
     * [incidentKey] selalu menunjuk pesan SOS_LOC: untuk pesan SOS_LOC ia sama
     * dengan [messageKey] sendiri, untuk SOS_DETAIL ia menunjuk pesan LOC yang
     * dirujuk. Inilah yang menggabungkan dua pesan jadi satu insiden.
     */
    @ColumnInfo(name = "incidentKey") val incidentKey: Long? = null,
    @ColumnInfo(name = "sosKind") val sosKind: Int = 0,
    @ColumnInfo(name = "latE4") val latE4: Int? = null,
    @ColumnInfo(name = "lonE4") val lonE4: Int? = null,
    @ColumnInfo(name = "accuracyM") val accuracyM: Int? = null,
    @ColumnInfo(name = "fixAgeSec") val fixAgeSec: Int? = null,
    @ColumnInfo(name = "originBatteryPct") val originBatteryPct: Int? = null,
    @ColumnInfo(name = "victimCount") val victimCount: Int = 0,
    @ColumnInfo(name = "hazards") val hazards: Int = 0,
    @ColumnInfo(name = "isSosLoc") val isSosLoc: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageEntity) return false
        return messageKey == other.messageKey &&
            payloadBytes.contentEquals(other.payloadBytes) &&
            status == other.status &&
            ttl == other.ttl &&
            hopCount == other.hopCount &&
            deliveredAt == other.deliveredAt &&
            isReassembly == other.isReassembly
    }

    override fun hashCode(): Int {
        var result = messageKey.hashCode()
        result = 31 * result + payloadBytes.contentHashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + ttl
        result = 31 * result + hopCount
        result = 31 * result + (deliveredAt?.hashCode() ?: 0)
        result = 31 * result + isReassembly.hashCode()
        return result
    }
}
