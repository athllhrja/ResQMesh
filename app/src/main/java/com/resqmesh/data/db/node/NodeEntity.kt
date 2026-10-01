package com.resqmesh.data.db.node

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "NodeEntity",
    indices = [Index(value = ["lastSeenAt"]), Index(value = ["isSelf"])],
)
data class NodeEntity(
    @PrimaryKey @ColumnInfo(name = "nodeId") val nodeId: Long,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "statusFlags") val statusFlags: Int,
    @ColumnInfo(name = "batteryPct") val batteryPct: Int,
    @ColumnInfo(name = "pendingCount") val pendingCount: Int,
    @ColumnInfo(name = "gattPeerCount") val gattPeerCount: Int,
    @ColumnInfo(name = "defaultTtl") val defaultTtl: Int,
    @ColumnInfo(name = "nodeSeq") val nodeSeq: Long,
    @ColumnInfo(name = "rssi") val rssi: Int,
    @ColumnInfo(name = "isSelf") val isSelf: Boolean,
    @ColumnInfo(name = "firstSeenAt") val firstSeenAt: Long,
    @ColumnInfo(name = "lastSeenAt") val lastSeenAt: Long,
)
