package com.resqmesh.data.codec

import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosKind

/**
 * Isi payload bertipe SOS. Ditandai di frame lewat
 * `MsgFlag.SOS_PAYLOAD`, dan dibedakan `SOS_LOC` dari `SOS_DETAIL` lewat
 * `MsgFlag.SOS_LOC`. Tanpa flag tersebut payload ditafsirkan sebagai teks biasa.
 */
sealed interface SosPayload {

    /**
     * Blob lokasi, selalu 16 byte agar persis muat dua frame (9 + 7).
     *
     * ```
     * 0  u8   sosKind
     * 1  u8   caps          bit0 ada akurasi, bit1 ada umur fix, bit2 ada baterai
     * 2  s24  lat * 1e4
     * 5  s24  lon * 1e4
     * 8  u16  accuracyMeter
     * 10 u16  fixAgeDetik
     * 12 u8   batteryPct    0xFF bila tidak diketahui
     * 13 u8   victimCount   0 berarti pengirim sendiri
     * 14 u8   hazards       bitmask SosHazard
     * 15 u8   reserved
     * ```
     */
    data class Loc(
        val kind: SosKind,
        val point: LatLon,
        val accuracyMeters: Int?,
        val fixAgeSeconds: Int?,
        val originBatteryPct: Int?,
        val victimCount: Int,
        val hazards: Int,
    ) : SosPayload {
        fun toBytes(): ByteArray {
            val w = ByteWriter(MeshConfig.SOS_LOC_BLOB_BYTES)
            w.u8(kind.wire)

            var caps = 0
            if (accuracyMeters != null) caps = caps or CAP_ACCURACY
            if (fixAgeSeconds != null) caps = caps or CAP_FIX_AGE
            if (originBatteryPct != null) caps = caps or CAP_BATTERY
            w.u8(caps)

            w.s24((point.latitude * MeshConfig.COORD_SCALE).toInt())
            w.s24((point.longitude * MeshConfig.COORD_SCALE).toInt())
            w.u16(accuracyMeters ?: 0)
            w.u16(fixAgeSeconds ?: 0)
            w.u8(originBatteryPct ?: BATTERY_UNKNOWN)
            w.u8(victimCount.coerceIn(0, 255))
            w.u8(hazards and 0xFF)
            w.u8(0)
            return w.toByteArray()
        }
    }

    /**
     * Blob keterangan, 7 byte header dan sisanya teks bebas.
     *
     * ```
     * 0  u24  refSeq        seq pesan SOS_LOC yang dirujuk
     * 3  u24  refOrigin     node pengirim SOS_LOC
     * 6  u8   refSosKind    ikut dikirim agar penerima bisa klasifikasi
     *                        walau SOS_LOC belum sampai
     * 7  ...  text UTF-8
     * ```
     */
    data class Detail(
        val incident: MessageId,
        val kind: SosKind,
        val text: String,
    ) : SosPayload {
        fun toBytes(): ByteArray {
            val body = text.toByteArray(Charsets.UTF_8)
                .let { bytes -> bytes.copyOf(minOf(bytes.size, MeshConfig.SOS_TEXT_MAX_BYTES)) }
            val w = ByteWriter(MeshConfig.SOS_DETAIL_HEADER_BYTES + body.size)
            w.u24(incident.seq.toLong())
            w.u24(incident.origin.value)
            w.u8(kind.wire)
            w.bytes(body)
            return w.toByteArray()
        }
    }

    companion object {
        const val BATTERY_UNKNOWN = 0xFF
        const val CAP_ACCURACY = 0x01
        const val CAP_FIX_AGE = 0x02
        const val CAP_BATTERY = 0x04
    }
}

/** Kesalahan saat blob SOS tidak bisa dibaca, misal dari node versi lama. */
class SosParseException(message: String) : Exception(message)

object SosCodec {

    fun encodeLoc(
        kind: SosKind,
        fix: LocationFix,
        victimCount: Int,
        hazards: Int,
    ): ByteArray = SosPayload.Loc(
        kind = kind,
        point = fix.point,
        accuracyMeters = fix.accuracyMeters,
        fixAgeSeconds = fix.ageSeconds,
        originBatteryPct = fix.batteryPct,
        victimCount = victimCount,
        hazards = hazards,
    ).toBytes()

    fun encodeDetail(incident: MessageId, kind: SosKind, text: String): ByteArray =
        SosPayload.Detail(incident, kind, text).toBytes()

    fun decodeLoc(bytes: ByteArray): SosPayload.Loc {
        requireLocSize(bytes)
        val r = ByteReader(bytes)
        val kind = SosKind.fromWire(r.u8())
        val caps = r.u8()
        val lat = r.s24()
        val lon = r.s24()
        val accuracy = r.u16()
        val fixAge = r.u16()
        val battery = r.u8()
        val victims = r.u8()
        val hazards = r.u8()
        r.u8()

        val point = LatLon(lat.toDouble() / MeshConfig.COORD_SCALE, lon.toDouble() / MeshConfig.COORD_SCALE)
        if (!point.isPlausible()) {
            throw SosParseException("Koordinat tidak masuk akal: $point")
        }
        return SosPayload.Loc(
            kind = kind,
            point = point,
            accuracyMeters = if (caps and SosPayload.CAP_ACCURACY != 0) accuracy.takeIf { it > 0 } else null,
            fixAgeSeconds = if (caps and SosPayload.CAP_FIX_AGE != 0) fixAge.takeIf { it > 0 } else null,
            originBatteryPct = if (caps and SosPayload.CAP_BATTERY != 0) {
                battery.takeIf { it != SosPayload.BATTERY_UNKNOWN }
            } else {
                null
            },
            victimCount = victims,
            hazards = hazards,
        )
    }

    fun decodeDetail(bytes: ByteArray): SosPayload.Detail {
        if (bytes.size <= MeshConfig.SOS_DETAIL_HEADER_BYTES) {
            throw SosParseException("SOS_DETAIL terlalu pendek: ${bytes.size} byte")
        }
        val r = ByteReader(bytes)
        val seq = r.u24().toInt()
        val origin = NodeId(r.u24())
        val kind = SosKind.fromWire(r.u8())
        val text = r.bytes(r.remaining).toString(Charsets.UTF_8)
        return SosPayload.Detail(
            incident = MessageId.of(origin, seq),
            kind = kind,
            text = text,
        )
    }

    private fun requireLocSize(bytes: ByteArray) {
        if (bytes.size < MeshConfig.SOS_LOC_BLOB_BYTES) {
            throw SosParseException("SOS_LOC harus ${MeshConfig.SOS_LOC_BLOB_BYTES} byte, dapat ${bytes.size}")
        }
    }
}
