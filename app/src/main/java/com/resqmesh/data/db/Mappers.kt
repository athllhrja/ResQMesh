package com.resqmesh.data.db

import com.resqmesh.core.MeshConfig
import com.resqmesh.data.db.message.MessageEntity
import com.resqmesh.data.db.node.NodeEntity
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.Message
import com.resqmesh.domain.model.MessageDirection
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MessageStatus
import com.resqmesh.domain.model.Node
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosIncident
import com.resqmesh.domain.model.SosKind

fun NodeEntity.toDomain(): Node = Node(
    id = NodeId(nodeId),
    displayName = displayName,
    statusFlags = statusFlags,
    batteryPct = batteryPct,
    pendingCount = pendingCount,
    gattPeerCount = gattPeerCount,
    defaultTtl = defaultTtl,
    nodeSeq = nodeSeq,
    rssi = rssi,
    isSelf = isSelf,
    firstSeenAt = firstSeenAt,
    lastSeenAt = lastSeenAt,
)

fun MessageEntity.toDomain(): Message = Message(
    id = MessageId(messageKey),
    origin = NodeId(originNodeId),
    destination = NodeId(destinationId),
    peerId = NodeId(peerId),
    text = payload,
    createdAt = createdAt,
    initialTtl = initialTtl,
    ttl = ttl,
    hopCount = hopCount,
    status = MessageStatus.fromWire(status),
    direction = MessageDirection.fromWire(direction),
    isSos = isSos,
    isReplay = isReplay,
    deliveredAt = deliveredAt,
    isReassembly = isReassembly,
)

fun MessageEntity.toPoint(): LatLon? {
    val lat = latE4 ?: return null
    val lon = lonE4 ?: return null
    return LatLon(
        latitude = lat.toDouble() / MeshConfig.COORD_SCALE,
        longitude = lon.toDouble() / MeshConfig.COORD_SCALE,
    )
}

/**
 * Menggabungkan baris SOS_LOC dan SOS_DETAIL menjadi satu insiden.
 *
 * Dua-duanya menunjuk `incidentKey` yang sama, yaitu `messageKey` pesan LOC.
 * Urutan tidak penting: kadang DETAIL tiba lebih dulu, dan insiden itu tetap
 * harus muncul di daftar, hanya dengan lokasi yang menyusul.
 */
fun List<MessageEntity>.toSosIncidents(): List<SosIncident> =
    filter { it.isSos && it.incidentKey != null }
        .groupBy { it.incidentKey!! }
        .mapNotNull { (_, rows) -> rows.toSosIncident() }
        .sortedByDescending { it.createdAt }

/** Menggabungkan sekumpulan baris yang sudah difilter ke satu insiden. */
fun List<MessageEntity>.toSosIncident(): SosIncident? {
    if (isEmpty()) return null

    val loc = firstOrNull { it.isSosLoc } ?: firstOrNull { it.latE4 != null }
    val detail = firstOrNull { !it.isSosLoc && it.payload.isNotBlank() }
    val primary = loc ?: detail ?: first()
    val incidentKey = primary.incidentKey ?: primary.messageKey

    return SosIncident(
        id = MessageId(incidentKey),
        kind = SosKind.fromWire(loc?.sosKind ?: detail?.sosKind ?: 0),
        origin = NodeId(primary.originNodeId),
        point = loc?.toPoint(),
        accuracyMeters = loc?.accuracyM,
        fixAgeSeconds = loc?.fixAgeSec,
        originBatteryPct = loc?.originBatteryPct,
        victimCount = loc?.victimCount ?: 0,
        hazards = loc?.hazards ?: 0,
        text = detail?.payload.orEmpty(),
        createdAt = listOfNotNull(loc?.createdAt, detail?.createdAt).minOrNull() ?: primary.createdAt,
        isOutgoing = primary.direction == MessageDirection.OUTGOING.wire,
        isRelayOnly = primary.direction == MessageDirection.RELAYED.wire,
        status = MessageStatus.fromWire(primary.status),
        hopCount = listOfNotNull(loc?.hopCount, detail?.hopCount).minOrNull() ?: primary.hopCount,
        hasLocation = loc?.latE4 != null,
        hasDetail = !detail?.payload.isNullOrBlank(),
    )
}
