package com.resqmesh.data.db.message

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Rekam jejak hop per-pesan.
 *
 * [latencyMs] menyimpan nilai Round-Trip Time (RTT) dalam milidetik yang diukur
 * hanya pada Origin (HP Pengirim) saat ACK tiba, menggunakan satu jam tunggal.
 * Latensi satu arah antar-HP per-hop bernilai null karena jam antar-HP tidak tersinkron.
 */
@Entity(
    tableName = "MessageHopEntity",
    indices = [Index(value = ["messageKey", "hopIndex"])],
)
data class MessageHopEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "messageKey") val messageKey: Long,
    @ColumnInfo(name = "nodeId") val nodeId: Long,
    @ColumnInfo(name = "hopIndex") val hopIndex: Int,
    @ColumnInfo(name = "rssi") val rssi: Int,
    @ColumnInfo(name = "latencyMs") val latencyMs: Long?,
    @ColumnInfo(name = "observedAt") val observedAt: Long,
)
