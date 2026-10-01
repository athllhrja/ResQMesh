package com.resqmesh.domain.model

/**
 * Jenis darurat. Nilainya ikut ke wire, jadi hanya boleh ditambah di akhir dan
 * [UNKNOWN] harus selalu bernilai 0 agar versi baru tetap bisa dibaca versi lama.
 */
enum class SosKind(val wire: Int, val label: String) {
    MEDICAL(1, "Medis"),
    FIRE(2, "Kebakaran"),
    ACCIDENT(3, "Kecelakaan"),
    FLOOD(4, "Banjir"),
    SECURITY(5, "Keamanan"),
    TRAPPED(6, "Terjebak"),
    OTHER(7, "Lainnya"),
    UNKNOWN(0, "Darurat");

    companion object {
        fun fromWire(value: Int): SosKind =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/**
 * Kondisi korban. Disimpan sebagai bitmask supaya seluruhnya muat dalam satu
 * byte, sehingga tim rescue bisa menilai kebutuhan alat sebelum tiba.
 */
object SosHazard {
    const val CONSCIOUS = 0x01
    const val BLEEDING = 0x02
    const val TRAPPED = 0x04
    const val FIRE = 0x08
    const val HAZARD_AREA = 0x10
    const val NEEDS_EVAC = 0x20

    val NAMES: List<Pair<Int, String>> = listOf(
        CONSCIOUS to "Sadari",
        BLEEDING to "Luka berdarah",
        TRAPPED to "Terjebak",
        FIRE to "Ada api",
        HAZARD_AREA to "Area berbahaya",
        NEEDS_EVAC to "Perlu evakuasi",
    )

    fun describe(mask: Int): String =
        NAMES.filter { (bit, _) -> mask and bit != 0 }
            .joinToString(", ") { (_, label) -> label }
}

/** Titik koordinat pada rentang derajat desimal. */
data class LatLon(val latitude: Double, val longitude: Double) {

    fun isPlausible(): Boolean =
        latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)

    /**
     * Jarak perkiraan dalam meter memakai rumus haversine. Cukup untuk
     * menentukan "korban dekat dengan saya atau bukan".
     */
    fun distanceMetersTo(other: LatLon): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(latitude)) *
            Math.cos(Math.toRadians(other.latitude)) *
            Math.sin(dLon / 2).let { it * it }
        return 2 * earthRadius * Math.asin(Math.sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun format(): String = "%.5f, %.5f".format(latitude, longitude)
}

/** Hasil pembacaan lokasi yang sudah dibersihkan dan siap dikirim. */
data class LocationFix(
    val point: LatLon,
    /** Radius akurasi horizontal dalam meter, atau null bila platform tidak melaporkannya. */
    val accuracyMeters: Int?,
    val ageSeconds: Int,
    val batteryPct: Int?,
)
