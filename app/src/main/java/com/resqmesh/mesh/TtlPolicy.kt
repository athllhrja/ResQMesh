package com.resqmesh.mesh

class TtlPolicy {

    fun shouldForward(ttl: Int, hop: Int, initialTtl: Int): Boolean =
        ttl > 0 && hop < initialTtl

    fun nextTtl(ttl: Int): Int = (ttl - 1).coerceAtLeast(0)

    fun nextHop(hop: Int): Int = hop + 1
}
