package com.resqmesh.data.codec

import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.model.BeaconFrame
import com.resqmesh.domain.model.FrameType
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MsgFlag
import com.resqmesh.domain.model.NodeId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCodecTest {

    private val codec = FrameCodec()
    private val origin = NodeId(0xA83F2C)
    private val destination = NodeId(0xC72D4A)

    private fun sampleFrame(
        payload: ByteArray = "BUTUH BAN".toByteArray(),
        fragIndex: Int = 0,
        fragCount: Int = 2,
        totalLen: Int = 12,
    ) = MeshFrame(
        messageId = MessageId.of(origin, 1),
        destination = destination,
        ttl = 5,
        hopCount = 0,
        flags = MsgFlag.FRAGMENTED or MsgFlag.ACK_REQUESTED,
        fragIndex = fragIndex,
        fragCount = fragCount,
        totalPayloadLen = totalLen,
        payloadChunk = payload,
    )

    @Test
    fun `frame_pesan_selalu_27_byte`() {
        assertEquals(FrameCodec.MESSAGE_SIZE, codec.encodeMessage(sampleFrame()).size)
    }

    @Test
    fun `frame_pesan_dengan_payload_kosong_tetap_27_byte`() {
        val bytes = codec.encodeMessage(sampleFrame(payload = ByteArray(0), fragCount = 1, totalLen = 0))
        assertEquals(FrameCodec.MESSAGE_SIZE, bytes.size)
    }

    @Test
    fun roundtrip_pesan_menjaga_semua_field() {
        val original = sampleFrame()
        val decoded = codec.decode(codec.encodeMessage(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `payload_fragmen_tidak_melewati_budget_advertising`() {
        val terlaluBesar = sampleFrame(payload = ByteArray(MeshConfig.PAYLOAD_PER_FRAME + 1))
        var ditolak = false
        runCatching { codec.encodeMessage(terlaluBesar) }.onFailure { ditolak = true }
        assertTrue(ditolak)
    }

    @Test
    fun frame_beacon_selalu_27_byte_dan_roundtrip_jaga_field() {
        val beacon = BeaconFrame(
            nodeId = NodeId(0xB19C77),
            statusFlags = 0x03,
            batteryPct = 87,
            pendingCount = 2,
            defaultTtl = 5,
            gattPeerCount = 1,
            nodeSeq = 12_345L,
        )
        val bytes = codec.encodeBeacon(beacon)
        assertEquals(FrameCodec.BEACON_SIZE, bytes.size)
        assertEquals(beacon, codec.decodeBeacon(bytes))
    }

    @Test
    fun `baterai_tidak_diketahui_disimpan_sebagai_minus_satu`() {
        val beacon = BeaconFrame(
            nodeId = NodeId(0xB19C77),
            statusFlags = 0,
            batteryPct = -1,
            pendingCount = 0,
            defaultTtl = 5,
            gattPeerCount = 0,
            nodeSeq = 1L,
        )
        // Byte 0 = versi, byte 1 = tipe, byte 2-4 = nodeId, byte 5 = status, byte 6 = baterai.
        assertEquals(FrameCodec.BATTERY_UNKNOWN, codec.encodeBeacon(beacon)[6].toUByte().toInt())
        assertEquals(-1, codec.decodeBeacon(codec.encodeBeacon(beacon)).batteryPct)
    }

    @Test
    fun `versi_selalu_menandai_tipe_frame_di_byte_kedua`() {
        val bytes = codec.encodeMessage(sampleFrame())
        assertEquals(MeshConfig.PROTOCOL_VERSION, bytes[0].toInt())
        assertEquals(FrameType.MSG.code, bytes[1].toInt())
    }

    @Test
    fun versi_tidak_didukung_ditolak() {
        val bytes = codec.encodeMessage(sampleFrame()).also { it[0] = 0x09 }
        var ditolak = false
        runCatching { codec.decode(bytes) }.onFailure { ditolak = true }
        assertTrue(ditolak)
    }

    @Test
    fun frame_terpotong_ditolak_saat_decode() {
        val penuh = codec.encodeMessage(sampleFrame())
        val terpotong = penuh.copyOf(20)
        var ditolak = false
        runCatching { codec.decode(terpotong) }.onFailure { ditolak = true }
        assertTrue(ditolak)
    }

    @Test
    fun decode_menolak_frame_beacon() {
        val beacon = codec.encodeBeacon(
            BeaconFrame(NodeId(0xB19C77), 0, 50, 0, 5, 0, 1L),
        )
        var ditolak = false
        runCatching { codec.decode(beacon) }.onFailure { ditolak = true }
        assertTrue(ditolak)
    }

    @Test
    fun fragmen_kosong_boleh_ada_pada_pesan_sos_kosong() {
        val kosong = sampleFrame(payload = ByteArray(0), fragCount = 1, totalLen = 0)
        assertArrayEquals(ByteArray(0), codec.decode(codec.encodeMessage(kosong)).payloadChunk)
    }
}
