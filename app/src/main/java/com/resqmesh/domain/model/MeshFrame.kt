package com.resqmesh.domain.model

import com.resqmesh.core.MeshConfig

enum class FrameType(val code: Int) {
    BEACON(0x01),
    MSG(0x02),
    UNKNOWN(0xFF);

    companion object {
        fun fromCode(code: Int): FrameType =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class MeshFrame(
    val messageId: MessageId,
    val destination: NodeId,
    val ttl: Int,
    val hopCount: Int,
    val flags: Int,
    val fragIndex: Int,
    val fragCount: Int,
    val totalPayloadLen: Int,
    val payloadChunk: ByteArray,
) {
    val isAck: Boolean get() = flags and MsgFlag.IS_ACK != 0
    val isSos: Boolean get() = flags and MsgFlag.SOS != 0
    val isReplay: Boolean get() = flags and MsgFlag.REPLAY != 0

    /**
     * Payload-nya blob terstruktur SOS, bukan teks mentah. Ditandai oleh
     * [MsgFlag.SOS_PAYLOAD].
     */
    val isSosPayload: Boolean get() = flags and MsgFlag.SOS_PAYLOAD != 0

    /** Fragmen koordinat. Tanpa flag ini, [MsgFlag.SOS_PAYLOAD] berarti SOS_DETAIL. */
    val isSosLoc: Boolean get() = isSosPayload && flags and MsgFlag.SOS_LOC != 0

    val isSosDetail: Boolean get() = isSosPayload && !isSosLoc

    val isFragmented: Boolean get() = fragCount > 1
    val isBroadcast: Boolean get() = destination.isBroadcast

    /**
     * Invarian wire: pada frame pertama yang diterima, ttl + hopCount == initialTtl,
     * karena pengirim asli belum mengurangi TTL dan hopCount masih 0.
     */
    fun inferredInitialTtl(): Int = ttl + hopCount

    fun forwarded(nextTtl: Int, nextHop: Int, extraFlags: Int = 0): MeshFrame = copy(
        ttl = nextTtl,
        hopCount = nextHop,
        flags = flags or extraFlags,
    )

    fun withFragment(index: Int, chunk: ByteArray): MeshFrame = copy(
        fragIndex = index,
        payloadChunk = chunk,
        flags = if (fragCount > 1) flags or MsgFlag.FRAGMENTED else flags and MsgFlag.FRAGMENTED.inv(),
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshFrame) return false
        return messageId == other.messageId &&
            destination == other.destination &&
            ttl == other.ttl &&
            hopCount == other.hopCount &&
            flags == other.flags &&
            fragIndex == other.fragIndex &&
            fragCount == other.fragCount &&
            totalPayloadLen == other.totalPayloadLen &&
            payloadChunk.contentEquals(other.payloadChunk)
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + destination.hashCode()
        result = 31 * result + ttl
        result = 31 * result + hopCount
        result = 31 * result + flags
        result = 31 * result + fragIndex
        result = 31 * result + fragCount
        result = 31 * result + totalPayloadLen
        result = 31 * result + payloadChunk.contentHashCode()
        return result
    }
}

data class BeaconFrame(
    val nodeId: NodeId,
    val statusFlags: Int,
    val batteryPct: Int,
    val pendingCount: Int,
    val defaultTtl: Int,
    val gattPeerCount: Int,
    val nodeSeq: Long,
)

object Fragmenter {

    fun fragment(payload: ByteArray, chunkSize: Int = MeshConfig.PAYLOAD_PER_FRAME): List<ByteArray> {
        if (payload.isEmpty()) return listOf(ByteArray(0))
        return payload.toList().chunked(chunkSize).map { it.toByteArray() }
    }

    fun totalLength(payload: ByteArray): Int = payload.size

    fun fragmentsOf(frame: MeshFrame, payload: ByteArray): List<MeshFrame> {
        val chunks = fragment(payload)
        return chunks.mapIndexed { index, chunk ->
            frame.withFragment(index = index, chunk = chunk).copy(fragCount = chunks.size)
        }
    }
}
