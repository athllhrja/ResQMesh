package com.resqmesh.mesh

import com.resqmesh.core.TimeProvider
import com.resqmesh.data.db.message.MessageDao
import com.resqmesh.data.db.message.MessageHopDao
import com.resqmesh.data.db.message.MessageHopEntity
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SignalStrength

/**
 * Menandai pesan milik sendiri sebagai terkirim setelah ACK tiba.
 *
 * ACK di-wire-kan memakai `messageId` pesan asli, jadi originator mengenali
 * ACK-nya tanpa perlu field pengirim tambahan.
 */
class AckTracker(
    private val selfId: NodeId,
    private val messageDao: MessageDao,
    private val hopDao: MessageHopDao,
    private val clock: TimeProvider,
) {

    suspend fun onAck(id: MessageId) {
        val now = clock.now()
        val updated = messageDao.markAcked(id.value)
        if (updated == 0) return

        val createdAt = messageDao.findByKey(id.value)?.createdAt ?: now
        hopDao.insert(
            MessageHopEntity(
                messageKey = id.value,
                nodeId = selfId.value,
                hopIndex = 0,
                rssi = SignalStrength.RSSI_NONE,
                latencyMs = now - createdAt,
                observedAt = now,
            ),
        )
    }
}
