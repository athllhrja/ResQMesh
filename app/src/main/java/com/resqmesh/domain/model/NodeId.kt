package com.resqmesh.domain.model

import kotlin.random.Random

@JvmInline
value class NodeId(val value: Long) : Comparable<NodeId> {

    init {
        require(value in UNKNOWN..MAX_VALUE) { "NodeId di luar rentang: $value" }
    }

    val hex: String get() = "%06X".format(value)
    val isValid: Boolean get() = value != UNKNOWN
    val isBroadcast: Boolean get() = value == BROADCAST

    override fun toString(): String = "NODE-$hex"

    override fun compareTo(other: NodeId): Int = value.compareTo(other.value)

    companion object {
        const val UNKNOWN = 0x000000L
        const val BROADCAST = 0xFFFFFFL
        const val MIN_VALUE = 0x000001L
        const val MAX_VALUE = 0xFFFFFFL

        val BROADCAST_ID = NodeId(BROADCAST)

        fun fromHex(value: String): NodeId =
            NodeId(value.removePrefix("NODE-").trim().toLongOrNull(16) ?: UNKNOWN)

        fun random(random: Random = Random.Default): NodeId =
            NodeId((MIN_VALUE..MAX_VALUE).random(random))
    }
}
