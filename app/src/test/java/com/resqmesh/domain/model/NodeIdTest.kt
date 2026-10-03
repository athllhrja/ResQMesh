package com.resqmesh.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeIdTest {

    @Test
    fun `hex_enam_karakter_nol_di_awal`() {
        assertEquals("0A1B2C", NodeId(0x0A1B2C).hex)
        assertEquals("NODE-0A1B2C", NodeId(0x0A1B2C).toString())
    }

    @Test
    fun `fromHex_menerima_bentuk_lengkap_dan_pendek`() {
        assertEquals(NodeId(0xA83F2C), NodeId.fromHex("NODE-A83F2C"))
        assertEquals(NodeId(0xA83F2C), NodeId.fromHex("A83F2C"))
    }

    @Test
    fun `from_hex_tidak_valid_jatuh_ke_unknown`() {
        assertEquals(NodeId(NodeId.UNKNOWN), NodeId.fromHex("bukan-hex"))
    }

    @Test
    fun `broadcast_terbaca_dengan_benar`() {
        assertTrue(NodeId.BROADCAST_ID.isBroadcast)
        assertFalse(NodeId(0xA83F2C).isBroadcast)
    }

    @Test
    fun `nilai_di_luar_rentang_ditolak`() {
        var ditolak = false
        runCatching { NodeId(0x1000000L) }.onFailure { ditolak = true }
        assertTrue(ditolak)
    }

    @Test
    fun `konversi_byte_array_3_byte_bolak_balik`() {
        val original = NodeId(0xA83F2C)
        val bytes = original.toByteArray()
        assertEquals(3, bytes.size)
        assertArrayEquals(byteArrayOf(0xA8.toByte(), 0x3F.toByte(), 0x2C.toByte()), bytes)
        assertEquals(original, NodeId.fromByteArray(bytes))
    }
}
