package com.resqmesh.domain.model

object MsgFlag {
    const val NONE = 0x00
    const val SOS = 0x01
    const val FRAGMENTED = 0x02
    const val ACK_REQUESTED = 0x04
    const val IS_ACK = 0x08
    const val REPLAY = 0x10
    const val REPLY_TO_HOP = 0x20

    /**
     * Menandai bahwa isi `payloadChunk` adalah blob terstruktur [SosPayload]
     * dan bukan teks mentah. Tanpa flag ini, payload ditafsirkan sebagai UTF-8
     * biasa supaya chat tidak kehilangan satu byte pun.
     */
    const val SOS_PAYLOAD = 0x40

    /** Dipakai bersama [SOS_PAYLOAD]: fragmen pertama koordinat, dikirim duluan. */
    const val SOS_LOC = 0x80

    const val KNOWN_MASK = SOS or
        FRAGMENTED or
        ACK_REQUESTED or
        IS_ACK or
        REPLAY or
        REPLY_TO_HOP or
        SOS_PAYLOAD or
        SOS_LOC
}
