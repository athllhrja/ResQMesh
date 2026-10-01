package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.NodeId
import java.util.concurrent.ConcurrentHashMap

sealed interface AssemblyResult {
    data object Incomplete : AssemblyResult
    data class Complete(val payload: ByteArray) : AssemblyResult
    data class Stale(val id: MessageId) : AssemblyResult
    data class Malformed(val reason: String) : AssemblyResult
}

class FragmentAssembler(
    private val clock: TimeProvider,
) {
    private data class Pending(
        val id: MessageId,
        val origin: NodeId,
        val destination: NodeId,
        val ttl: Int,
        val hopCount: Int,
        val flags: Int,
        val totalLen: Int,
        val startedAt: Long,
        val received: MutableMap<Int, ByteArray> = mutableMapOf(),
    )

    private val pending = ConcurrentHashMap<Long, Pending>()

    fun accept(frame: MeshFrame): AssemblyResult {
        val now = clock.now()
        evictExpired(now)

        if (frame.fragCount == 1) {
            return AssemblyResult.Complete(frame.payloadChunk)
        }
        if (frame.totalPayloadLen > MeshConfig.MAX_PAYLOAD_BYTES) {
            return AssemblyResult.Malformed("totalPayloadLen=${frame.totalPayloadLen}")
        }

        val slot = pending.getOrPut(frame.messageId.value) {
            Pending(
                id = frame.messageId,
                origin = frame.messageId.origin,
                destination = frame.destination,
                ttl = frame.ttl,
                hopCount = frame.hopCount,
                flags = frame.flags,
                totalLen = frame.totalPayloadLen,
                startedAt = now,
            )
        }

        if (slot.destination != frame.destination || slot.totalLen != frame.totalPayloadLen) {
            pending.remove(frame.messageId.value)
            return AssemblyResult.Malformed("Header fragmen tidak konsisten")
        }

        slot.received[frame.fragIndex] = frame.payloadChunk

        if (slot.received.size < frame.fragCount) return AssemblyResult.Incomplete

        val ordered = (0 until frame.fragCount).map { index ->
            slot.received[index] ?: return AssemblyResult.Incomplete
        }
        val payload = ordered.fold(ByteArray(0)) { acc, part -> acc + part }
        pending.remove(frame.messageId.value)

        if (payload.size != frame.totalPayloadLen) {
            return AssemblyResult.Malformed("Panjang hasil re-assembly tidak cocok")
        }
        return AssemblyResult.Complete(payload)
    }

    fun pendingCount(): Int = pending.size

    fun evictExpired(now: Long = clock.now()): Int {
        val expired = pending.values.filter { now - it.startedAt > MeshConfig.ASSEMBLY_TIMEOUT_MS }
        expired.forEach { pending.remove(it.id.value) }
        return expired.size
    }
}
