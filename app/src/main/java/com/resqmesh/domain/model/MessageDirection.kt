package com.resqmesh.domain.model

enum class MessageDirection(val wire: String) {
    OUTGOING("OUTGOING"),
    INCOMING("INCOMING"),
    RELAYED("RELAYED");

    companion object {
        fun fromWire(value: String): MessageDirection =
            entries.firstOrNull { it.wire == value } ?: RELAYED
    }
}
