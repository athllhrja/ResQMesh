package com.resqmesh.domain.model

@JvmInline
value class MessageId(val value: Long) {

    val origin: NodeId get() = NodeId((value ushr 24) and 0xFFFFFF)
    val seq: Int get() = (value and 0xFFFFFF).toInt()

    fun display(): String = "${origin.hex}-%06X".format(seq)

    override fun toString(): String = display()

    companion object {
        private const val SEQ_MASK = 0xFFFFFFL

        fun of(origin: NodeId, seq: Int): MessageId {
            require(seq >= 0 && seq.toLong() <= SEQ_MASK) { "seq di luar rentang: $seq" }
            return MessageId((origin.value shl 24) or seq.toLong())
        }

        fun fromWire(origin: NodeId, seq: Int): MessageId = of(origin, seq)
    }
}
