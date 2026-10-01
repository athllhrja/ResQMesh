package com.resqmesh.data.codec

class ByteReader(private val buf: ByteArray) {
    private var pos = 0

    fun u8(): Int {
        require(pos < buf.size) { "ByteReader underflow pada offset $pos" }
        return buf[pos++].toInt() and 0xFF
    }

    fun u16(): Int = (u8() shl 8) or u8()

    fun u24(): Long = (u8().toLong() shl 16) or (u8().toLong() shl 8) or u8().toLong()

    /** Signed 24-bit, kembalian di-sign-extend ke Int. */
    fun s24(): Int {
        val raw = u24().toInt()
        return if (raw and 0x800000 != 0) raw or -0x1000000 else raw
    }

    fun u32(): Long =
        (u8().toLong() shl 24) or (u8().toLong() shl 16) or
            (u8().toLong() shl 8) or u8().toLong()

    fun bytes(count: Int): ByteArray {
        require(pos + count <= buf.size) { "ByteReader underflow pada offset $pos, butuh $count" }
        return buf.copyOfRange(pos, pos + count).also { pos += count }
    }

    val remaining: Int get() = buf.size - pos
}
