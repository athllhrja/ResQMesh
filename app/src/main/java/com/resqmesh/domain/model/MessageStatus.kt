package com.resqmesh.domain.model

enum class MessageStatus(val wire: String) {
    PENDING_FORWARD("PENDING_FORWARD"),
    AWAITING_FRAGMENTS("AWAITING_FRAGMENTS"),
    IN_TRANSIT("IN_TRANSIT"),
    CARRYING("CARRYING"),
    DELIVERED("DELIVERED"),
    ACKED("ACKED"),
    EXPIRED("EXPIRED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    val isTerminal: Boolean
        get() = this == ACKED || this == EXPIRED || this == FAILED || this == CANCELLED

    companion object {
        fun fromWire(value: String): MessageStatus =
            entries.firstOrNull { it.wire == value } ?: FAILED
    }
}
