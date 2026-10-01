package com.resqmesh.domain.model

enum class MessageStatus(val wire: String) {
    PENDING_FORWARD("PENDING_FORWARD"),
    AWAITING_FRAGMENTS("AWAITING_FRAGMENTS"),
    IN_TRANSIT("IN_TRANSIT"),
    DELIVERED("DELIVERED"),
    ACKED("ACKED"),
    EXPIRED("EXPIRED"),
    FAILED("FAILED");

    val isTerminal: Boolean
        get() = this == ACKED || this == EXPIRED || this == FAILED

    companion object {
        fun fromWire(value: String): MessageStatus =
            entries.firstOrNull { it.wire == value } ?: FAILED
    }
}
