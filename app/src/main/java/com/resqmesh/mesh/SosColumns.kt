package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.data.codec.SosCodec
import com.resqmesh.data.codec.SosParseException
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.MeshFrame

/**
 * Kolom SOS hasil pembacaan payload, siap ditempel ke
 * [com.resqmesh.data.db.message.MessageEntity].
 *
 * Kolom yang tidak relevan dibiarkan null/0 agar pesan biasa tidak bertambah
 * byte di database.
 */
data class SosColumns(
    val incidentKey: Long?,
    val kind: Int,
    val latE4: Int?,
    val lonE4: Int?,
    val accuracyM: Int?,
    val fixAgeSec: Int?,
    val originBatteryPct: Int?,
    val victimCount: Int,
    val hazards: Int,
    val isSosLoc: Boolean,

    /**
     * Teks siap tampil di daftar riwayat dan chat.
     *
     * Untuk SOS_LOC isinya ringkasan koordinat, bukan catatan korban. Baris LOC
     * tidak boleh kosong, karena daftar pesan akan menampilkan gelembung kosong
     * untuk setiap koordinat yang masuk. Baris ini tidak pernah ikut jadi teks
     * kartu insiden, karena kartu memakai payload dari baris DETAIL saja.
     */
    val text: String,
    val error: String? = null,
) {
    companion object {
        val NONE = SosColumns(
            incidentKey = null,
            kind = 0,
            latE4 = null,
            lonE4 = null,
            accuracyM = null,
            fixAgeSec = null,
            originBatteryPct = null,
            victimCount = 0,
            hazards = 0,
            isSosLoc = false,
            text = "",
        )

        /** Ringkasan satu baris untuk baris SOS_LOC. */
        fun locSummary(point: LatLon, accuracyMeters: Int?): String = buildString {
            append(point.format())
            if (accuracyMeters != null) {
                append(" ±").append(accuracyMeters).append("m")
            }
        }
    }
}

/**
 * Membaca payload SOS dari frame yang sudah selesai di-assemble.
 *
 * Sengaja tidak melempar error: pengirim boleh berjalan di versi lebih tua atau
 * salah kirim, dan pesan tetap harus bisa masuk daftar. Payload yang rusak
 * disimpan apa adanya dengan pesan di [SosColumns.error] supaya bisa ditelusuri
 * di log, bukan hilang.
 */
fun readSosColumns(frame: MeshFrame, payload: ByteArray): SosColumns {
    if (!frame.isSosPayload) return SosColumns.NONE

    return try {
        if (frame.isSosLoc) {
            val loc = SosCodec.decodeLoc(payload)
            SosColumns(
                incidentKey = frame.messageId.value,
                kind = loc.kind.wire,
                latE4 = (loc.point.latitude * MeshConfig.COORD_SCALE).toInt(),
                lonE4 = (loc.point.longitude * MeshConfig.COORD_SCALE).toInt(),
                accuracyM = loc.accuracyMeters,
                fixAgeSec = loc.fixAgeSeconds,
                originBatteryPct = loc.originBatteryPct,
                victimCount = loc.victimCount,
                hazards = loc.hazards,
                isSosLoc = true,
                text = SosColumns.locSummary(loc.point, loc.accuracyMeters),
            )
        } else {
            val detail = SosCodec.decodeDetail(payload)
            SosColumns(
                incidentKey = detail.incident.value,
                kind = detail.kind.wire,
                latE4 = null,
                lonE4 = null,
                accuracyM = null,
                fixAgeSec = null,
                originBatteryPct = null,
                victimCount = 0,
                hazards = 0,
                isSosLoc = false,
                text = detail.text,
            )
        }
    } catch (e: SosParseException) {
        SosColumns(
            incidentKey = frame.messageId.value,
            kind = 0,
            latE4 = null,
            lonE4 = null,
            accuracyM = null,
            fixAgeSec = null,
            originBatteryPct = null,
            victimCount = 0,
            hazards = 0,
            isSosLoc = frame.isSosLoc,
            text = "",
            error = e.message,
        )
    }
}
