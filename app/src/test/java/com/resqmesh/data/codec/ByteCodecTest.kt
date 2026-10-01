package com.resqmesh.data.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ByteCodecTest {

    @Test
    fun `u24_menggunakan_big_endian`() {
        val w = ByteWriter(4)
        w.u24(0xA83F2CL)
        assertArrayEquals(
            byteArrayOf(0xA8.toByte(), 0x3F, 0x2C),
            w.toByteArray(),
        )
    }

    @Test
    fun `u32_menggunakan_big_endian`() {
        val w = ByteWriter(4)
        w.u32(0xDEADBEEF)
        assertArrayEquals(
            byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            w.toByteArray(),
        )
    }

    @Test
    fun `reader_dan_writer_balik_berpasangan_benar`() {
        val w = ByteWriter(16)
        w.u8(0x7F)
        w.u16(0xBEEF)
        w.u24(0x123456L)
        w.u32(0xCAFEBABEL)

        val r = ByteReader(w.toByteArray())
        assertEquals(0x7F, r.u8())
        assertEquals(0xBEEF, r.u16())
        assertEquals(0x123456L, r.u24())
        assertEquals(0xCAFEBABEL, r.u32())
        assertEquals(0, r.remaining)
    }

    @Test
    fun `reader_melempar_ketika_data_kurang`() {
        val r = ByteReader(byteArrayOf(0x01))
        var ditolak = false
        runCatching { r.u16() }.onFailure { ditolak = true }
        assertTrue(ditolak)
    }

    @Test
    fun `writer_melempar_kejadian_kapasitas_melewati`() {
        val w = ByteWriter(2)
        var ditolak = false
        runCatching { w.u32(1) }.onFailure { ditolak = true }
        assertTrue(ditolak)
    }
}
