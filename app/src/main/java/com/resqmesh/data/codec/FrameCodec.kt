package com.resqmesh.data.codec

import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.model.BeaconFrame
import com.resqmesh.domain.model.FrameType
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.NodeId

class FrameCodec {

    fun encodeBeacon(frame: BeaconFrame): ByteArray {
        val w = ByteWriter(BEACON_SIZE)
        w.u8(MeshConfig.PROTOCOL_VERSION)
        w.u8(FrameType.BEACON.code)
        w.u24(frame.nodeId.value)
        w.u8(frame.statusFlags)
        // -1 berarti "tidak diketahui", diserialkan sebagai 0xFF.
        w.u8(if (frame.batteryPct < 0) BATTERY_UNKNOWN else frame.batteryPct.coerceIn(0, 255))
        w.u16(frame.pendingCount)
        w.u8(frame.defaultTtl)
        w.u8(frame.gattPeerCount)
        w.u32(frame.nodeSeq)
        w.skip(BEACON_SIZE - w.size)
        return w.toByteArray()
    }

    fun encodeMessage(frame: MeshFrame): ByteArray {
        require(frame.payloadChunk.size <= MeshConfig.PAYLOAD_PER_FRAME) {
            "Fragment melebihi budget: ${frame.payloadChunk.size} > ${MeshConfig.PAYLOAD_PER_FRAME}"
        }
        val w = ByteWriter(MESSAGE_SIZE)
        w.u8(MeshConfig.PROTOCOL_VERSION)
        w.u8(FrameType.MSG.code)
        w.u24(frame.messageId.seq.toLong())
        w.u24(frame.messageId.origin.value)
        w.u24(frame.destination.value)
        w.u8(frame.ttl)
        w.u8(frame.hopCount)
        w.u8(frame.flags)
        w.u8(frame.fragIndex)
        w.u8(frame.fragCount)
        w.u8(frame.totalPayloadLen.coerceIn(0, MeshConfig.MAX_PAYLOAD_BYTES))
        w.u8(frame.payloadChunk.size)
        w.bytes(frame.payloadChunk)
        // Panjang frame pesan dikunci di 27 byte supaya budget Advertising
        // tidak berubah-ubah meski payload-nya pendek.
        w.skip(MESSAGE_SIZE - w.size)
        return w.toByteArray()
    }

    fun decode(bytes: ByteArray): MeshFrame {
        if (bytes.size < 2) throw FrameParseException("Frame terlalu pendek: ${bytes.size} byte")
        val r = ByteReader(bytes)
        val version = r.u8()
        if (version != MeshConfig.PROTOCOL_VERSION) {
            throw FrameParseException("Versi protokol tidak didukung: $version")
        }
        return when (FrameType.fromCode(r.u8())) {
            FrameType.BEACON -> throw FrameParseException("decode() hanya menerima frame MSG")
            FrameType.MSG -> decodeMessage(r, bytes.size)
            FrameType.UNKNOWN -> throw FrameParseException("Tipe frame tidak dikenal")
        }
    }

    fun decodeBeacon(bytes: ByteArray): BeaconFrame {
        if (bytes.size < 2) throw FrameParseException("Frame terlalu pendek: ${bytes.size} byte")
        val r = ByteReader(bytes)
        val version = r.u8()
        if (version != MeshConfig.PROTOCOL_VERSION) {
            throw FrameParseException("Versi protokol tidak didukung: $version")
        }
        if (FrameType.fromCode(r.u8()) != FrameType.BEACON) {
            throw FrameParseException("Bukan frame BEACON")
        }
        val nodeId = NodeId(r.u24())
        val statusFlags = r.u8()
        val batteryPct = r.u8()
        val pendingCount = r.u16()
        val defaultTtl = r.u8()
        val gattPeerCount = r.u8()
        val nodeSeq = r.u32()
        return BeaconFrame(
            nodeId = nodeId,
            statusFlags = statusFlags,
            batteryPct = if (batteryPct == BATTERY_UNKNOWN) -1 else batteryPct,
            pendingCount = pendingCount,
            defaultTtl = defaultTtl,
            gattPeerCount = gattPeerCount,
            nodeSeq = nodeSeq,
        )
    }

    private fun decodeMessage(r: ByteReader, frameSize: Int): MeshFrame {
        val seq = r.u24().toInt()
        val origin = NodeId(r.u24())
        val destination = NodeId(r.u24())
        val ttl = r.u8()
        val hopCount = r.u8()
        val flags = r.u8()
        val fragIndex = r.u8()
        val fragCount = r.u8()
        val totalPayloadLen = r.u8()
        val chunkLen = r.u8()

        if (chunkLen > MeshConfig.PAYLOAD_PER_FRAME) {
            throw FrameParseException("thisPayloadLen tidak valid: $chunkLen")
        }
        if (fragCount == 0 || fragIndex >= fragCount) {
            throw FrameParseException("Indeks fragmen tidak valid: $fragIndex/$fragCount")
        }
        if (chunkLen > r.remaining) {
            throw FrameParseException("Frame terpotong: butuh $chunkLen, tersisa ${r.remaining}")
        }
        if (frameSize != MESSAGE_SIZE) {
            throw FrameParseException("Panjang frame pesan harus $MESSAGE_SIZE, dapat $frameSize")
        }
        if (frameSize > MeshConfig.MAX_ADVERTISING_BYTES) {
            throw FrameParseException("Frame melebihi budget: $frameSize > ${MeshConfig.MAX_ADVERTISING_BYTES}")
        }

        return MeshFrame(
            messageId = MessageId.fromWire(origin, seq),
            destination = destination,
            ttl = ttl,
            hopCount = hopCount,
            flags = flags,
            fragIndex = fragIndex,
            fragCount = fragCount,
            totalPayloadLen = totalPayloadLen,
            payloadChunk = r.bytes(chunkLen),
        )
    }

    companion object {
        const val BEACON_SIZE = 27
        const val MESSAGE_SIZE = 27
        const val BATTERY_UNKNOWN = 0xFF
    }
}
