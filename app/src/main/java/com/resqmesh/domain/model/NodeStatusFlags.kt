package com.resqmesh.domain.model

object NodeStatusFlags {
    const val NONE = 0x00
    const val MESH_ACTIVE = 0x01
    const val HAS_PENDING = 0x02
    const val SOS_RECEIVED = 0x04
    const val CHARGING = 0x08
    const val SCANNING = 0x10
    const val RESPONDER_NODE = 0x20
    const val KNOWN_MASK = MESH_ACTIVE or HAS_PENDING or SOS_RECEIVED or CHARGING or SCANNING or RESPONDER_NODE
}
