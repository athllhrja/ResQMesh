package com.resqmesh.core

object MeshConfig {
    const val DEFAULT_TTL = 5

    /**
     * SOS memakai TTL tinggi supaya menjangkau rantai node yang jauh. Pengiriman
     * SOS yang biasanya hanya aktif selama insiden berlangsung, sehingga
     * advertisement yang sering tidak membebani baterai dalam jangka panjang.
     */
    const val SOS_DEFAULT_TTL = 10
    const val MAX_TTL = 12

    const val PROTOCOL_VERSION = 0x01
    const val COMPANY_ID = 0xE000
    const val MAX_ADVERTISING_BYTES = 27
    const val PAYLOAD_PER_FRAME = 9
    const val MAX_PAYLOAD_BYTES = 255

    const val BEACON_INTERVAL_MS = 300L
    const val BEACON_INTERVAL_SOS_MS = 150L
    const val RELAY_INTERVAL_MS = 300L
    const val ACK_INTERVAL_MS = 200L
    const val SCHEDULER_JITTER_MS = 200L
    const val REPEAT_COUNT_NORMAL = 2
    const val REPEAT_COUNT_URGENT = 3

    const val SEEN_RETENTION_MS = 30 * 60 * 1000L
    const val ASSEMBLY_TIMEOUT_MS = 10_000L
    const val HISTORY_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
    const val NODE_STALE_MS = 90_000L
    const val PRUNE_INTERVAL_MS = 5 * 60_000L

    const val MAX_GATT_PEERS = 3
    const val GATT_CONNECT_BACKOFF_MS = 2_000L
    const val GATT_CHUNK_SIZE = 180

    /**
     * Batas jumlah kali satu node meneruskan hal yang sama.
     *
     * Chat biasa dibatasi [NORMAL_FORWARD_LIMIT] supaya tidak membanjiri duty
     * cycle advertising. Insiden SOS memakai [SOS_FLOOD_FORWARD_LIMIT] = 1:
     * setiap node meneruskan tepat sekali lalu menyingkirkan diri. Itulah yang
     * membuat jangkauan SOS menjauh dari A ke F tanpa memicu badai advertising.
     */
    const val NORMAL_FORWARD_LIMIT = 2
    const val SOS_FLOOD_FORWARD_LIMIT = 1

    const val MAX_RELAY_QUEUE = 32

    /**
     * Koordinat dikirim sebagai integer desimal. Presisi 1/10000 derajat
     * sekitar 11 meter, atau 1.1 meter pada lintang — cukup untuk tim
     * pencarian menemukan lokasi, dan muat di 3 byte per sumbu.
     */
    const val COORD_SCALE = 10_000

    /** Panjang blob SOS_LOC. 16 byte pas kebagi dua frame 9+7. */
    const val SOS_LOC_BLOB_BYTES = 16

    /** Header blob SOS_DETAIL: refSeq(3) + refOrigin(3) + refSosKind(1). */
    const val SOS_DETAIL_HEADER_BYTES = 7

    /** Umur maksimum fix lokasi yang masih dipakai untuk SOS. */
    const val LOCATION_MAX_AGE_SEC = 120

    /** Batas teks keterangan agar satu insiden tidak terlalu banyak frame. */
    const val SOS_TEXT_MAX_BYTES = 180

    /** Radius proximity untuk peringatan "korban di dekat sini". */
    const val SOS_NEARBY_METERS = 150.0
}
