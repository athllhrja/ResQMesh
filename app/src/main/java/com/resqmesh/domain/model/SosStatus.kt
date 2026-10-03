package com.resqmesh.domain.model

/**
 * Siklus hidup status SOS (Requirement F9):
 * - CREATED: Dibuat oleh user sendiri.
 * - RELAYED: Berhasil diteruskan oleh node relay.
 * - DELIVERED: Tiba di gateway/responder atau menerima ACK.
 * - EXPIRED: TTL habis atau paket kadaluwarsa.
 */
enum class SosStatus(val wire: String) {
    CREATED("CREATED"),
    RELAYED("RELAYED"),
    DELIVERED("DELIVERED"),
    EXPIRED("EXPIRED");

    companion object {
        fun fromWire(value: String): SosStatus =
            entries.firstOrNull { it.wire == value } ?: CREATED

        fun fromMessageStatus(status: MessageStatus): SosStatus = when (status) {
            MessageStatus.PENDING_FORWARD, MessageStatus.IN_TRANSIT -> CREATED
            MessageStatus.DELIVERED, MessageStatus.ACKED -> DELIVERED
            MessageStatus.EXPIRED, MessageStatus.FAILED -> EXPIRED
            else -> CREATED
        }
    }
}
