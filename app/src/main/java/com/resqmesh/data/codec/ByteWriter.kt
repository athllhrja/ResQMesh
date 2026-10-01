package com.resqmesh.data.codec

class ByteWriter(private val capacity: Int = 32) {
    private val buf = ByteArray(capacity)
    private var pos = 0

    fun u8(value: Int) {
        ensure(1)
        buf[pos++] = (value and 0xFF).toByte()
    }

    fun u16(value: Int) {
        u8(value shr 8)
        u8(value)
    }

    fun u24(value: Long) {
        u8(((value shr 16) and 0xFF).toInt())
        u8(((value shr 8) and 0xFF).toInt())
        u8((value and 0xFF).toInt())
    }

    /**
     * Signed 24-bit big-endian, rentang -8388608..8388607. Dipakai untuk
     * koordinat: 1e4 * 180 = 1800000 masih muat comfortably, jadi satu sumbu
     * hanya butuh 3 byte.
     */
    fun s24(value: Int) {
        require(value in MIN_S24..MAX_S24) { "Nilai di luar rentang s24: $value" }
        u24(value.toLong() and 0xFFFFFFL)
    }

    fun u32(value: Long) {
        u8(((value shr 24) and 0xFF).toInt())
        u8(((value shr 16) and 0xFF).toInt())
        u8(((value shr 8) and 0xFF).toInt())
        u8((value and 0xFF).toInt())
    }

    fun bytes(src: ByteArray) {
        ensure(src.size)
        src.copyInto(buf, pos)
        pos += src.size
    }

    fun skip(count: Int) {
        ensure(count)
        pos += count
    }

    val size: Int get() = pos

    fun toByteArray(): ByteArray = buf.copyOf(pos)

    private fun ensure(extra: Int) {
        require(pos + extra <= capacity) { "ByteWriter overflow: butuh ${pos + extra}, kapasitas $capacity" }
    }

    private companion object {
        const val MIN_S24 = -8388608
        const val MAX_S24 = 8388607
    }
}
