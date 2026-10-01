package com.resqmesh.domain.model

data class Message(
    val id: MessageId,
    val origin: NodeId,
    val destination: NodeId,
    val peerId: NodeId,
    val text: String,
    val createdAt: Long,
    val initialTtl: Int,
    val ttl: Int,
    val hopCount: Int,
    val status: MessageStatus,
    val direction: MessageDirection,
    val isSos: Boolean,
    val isReplay: Boolean,
    val deliveredAt: Long?,
    val isReassembly: Boolean,
) {
    val isOutgoing: Boolean get() = direction == MessageDirection.OUTGOING
    val isInbound: Boolean get() = direction == MessageDirection.INCOMING
    val isDelivered: Boolean
        get() = status == MessageStatus.DELIVERED || status == MessageStatus.ACKED
}
