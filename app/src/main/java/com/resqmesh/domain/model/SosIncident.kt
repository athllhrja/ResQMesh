package com.resqmesh.domain.model

/**
 * Satu insiden darurat setelah digabung dari dua pesan.
 *
 * Protokol mengirimnya sebagai dua pesan terpisah yang menunjuk satu
 * `incidentId` yang sama:
 *
 * - `SOS_LOC` (16 byte) membawa titik, akurasi, kondisi korban. Bisa diterima
 *   penuh dalam dua frame advertising, sehingga pin bisa langsung tampil.
 * - `SOS_DETAIL` membawa catatan bebas dan dirantai-ragmentasi.
 *
 * Pemisahan ini disengaja: kalau koordinat dan teks digabung dalam satu payload
 * ter-fragmentasi, penerima baru bisa menampilkan apa pun setelah fragmen
 * terakhir tiba. Dengan pemisahan, lokasi tampil lebih dulu dan teks menyusul.
 */
data class SosIncident(
    val id: MessageId,
    val kind: SosKind,
    val origin: NodeId,
    val point: LatLon?,
    val accuracyMeters: Int?,
    val fixAgeSeconds: Int?,
    val originBatteryPct: Int?,
    val victimCount: Int,
    val hazards: Int,
    val text: String,
    val createdAt: Long,
    val isOutgoing: Boolean,
    val isRelayOnly: Boolean,
    val status: MessageStatus,
    val hopCount: Int,
    val hasLocation: Boolean,
    val hasDetail: Boolean,
) {
    val hazardLabel: String get() = SosHazard.describe(hazards)

    val victimLabel: String
        get() = if (victimCount <= 0) "Pengirim saja" else "$victimCount orang"

    /** Ringkasan satu baris untuk daftar dan notifikasi. */
    fun summary(): String = buildString {
        append(kind.label)
        if (hazards != 0) {
            append(" · ").append(hazardLabel)
        }
        if (text.isNotBlank()) {
            append(" · ").append(text.take(60))
        }
    }
}
