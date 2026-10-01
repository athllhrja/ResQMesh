package com.resqmesh.domain.model

data class Node(
    val id: NodeId,
    val displayName: String,
    val statusFlags: Int,
    val batteryPct: Int,
    val pendingCount: Int,
    val gattPeerCount: Int,
    val defaultTtl: Int,
    val nodeSeq: Long,
    val rssi: Int,
    val isSelf: Boolean,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
) {
    val isMeshActive: Boolean
        get() = statusFlags and NodeStatusFlags.MESH_ACTIVE != 0

    val hasPending: Boolean
        get() = statusFlags and NodeStatusFlags.HAS_PENDING != 0

    val signal: SignalStrength get() = SignalStrength.fromRssi(rssi)

    fun isStale(now: Long, staleMs: Long): Boolean = now - lastSeenAt > staleMs
}

enum class SignalStrength {
    STRONG,
    MEDIUM,
    WEAK,
    UNKNOWN;

    companion object {
        fun fromRssi(rssi: Int): SignalStrength = when {
            rssi >= RSSI_STRONG -> STRONG
            rssi >= RSSI_MEDIUM -> MEDIUM
            rssi > RSSI_UNKNOWN -> WEAK
            else -> UNKNOWN
        }

        const val RSSI_STRONG = -60
        const val RSSI_MEDIUM = -80
        const val RSSI_UNKNOWN = -127
        const val RSSI_NONE = -127
    }
}
