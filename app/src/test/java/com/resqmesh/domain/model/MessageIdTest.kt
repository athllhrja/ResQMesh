package com.resqmesh.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageIdTest {

    private val origin = NodeId(0xA83F2C)

    @Test
    fun `komposisi_menyimpan_origin_dan_seq`() {
        val id = MessageId.of(origin, 1)
        assertEquals(origin, id.origin)
        assertEquals(1, id.seq)
    }

    @Test
    fun `urutan_sama_dengan_origin_berbeda_tidak_kolisi`() {
        val a = MessageId.of(NodeId(0xA83F2C), 7)
        val b = MessageId.of(NodeId(0xB19C77), 7)
        assertFalse(a == b)
    }

    @Test
    fun `fromWire_konsisten_dengan_of`() {
        assertEquals(MessageId.of(origin, 42), MessageId.fromWire(origin, 42))
    }

    @Test
    fun `display_dua_bagian_enam_karakter`() {
        assertEquals("A83F2C-00002A", MessageId.of(origin, 42).display())
    }

    @Test
    fun `seq_di_luar_rentang_ditolak`() {
        var ditolak = false
        runCatching { MessageId.of(origin, 0x1000000) }.onFailure { ditolak = true }
        assertTrue(ditolak)
    }
}
