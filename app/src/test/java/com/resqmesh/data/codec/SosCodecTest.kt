package com.resqmesh.data.codec

import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosHazard
import com.resqmesh.domain.model.SosKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosCodecTest {

    private val fix = LocationFix(
        point = LatLon(latitude = -6.17539, longitude = 106.82715),
        accuracyMeters = 12,
        ageSeconds = 4,
        batteryPct = 73,
    )

    @Test
    fun `sos loc selalu tepat enam belas byte`() {
        val bytes = SosCodec.encodeLoc(SosKind.MEDICAL, fix, victimCount = 2, hazards = SosHazard.BLEEDING)

        assertEquals(MeshConfig.SOS_LOC_BLOB_BYTES, bytes.size)
    }

    @Test
    fun `sos loc pecah tepat dua frame advertising`() {
        val bytes = SosCodec.encodeLoc(SosKind.FIRE, fix, victimCount = 1, hazards = 0)
        val chunks = bytes.toList().chunked(MeshConfig.PAYLOAD_PER_FRAME)

        assertEquals(2, chunks.size)
        assertEquals(MeshConfig.PAYLOAD_PER_FRAME, chunks.first().size)
    }

    @Test
    fun `koordinat negatif di lobe south survive round trip`() {
        val decoded = SosCodec.decodeLoc(
            SosCodec.encodeLoc(SosKind.MEDICAL, fix, victimCount = 1, hazards = 0),
        )

        // Toleransi satu unit LAST untuk pembulatan ke integer.
        assertEquals(-6.17539, decoded.point.latitude, 0.0001)
        assertEquals(106.82715, decoded.point.longitude, 0.0001)
    }

    @Test
    fun `sembolat nol semua kolom opsional`() {
        val bytes = SosCodec.encodeLoc(SosKind.OTHER, fix, victimCount = 0, hazards = 0)
        val decoded = SosCodec.decodeLoc(bytes)

        assertEquals(SosKind.OTHER, decoded.kind)
        assertEquals(0, decoded.victimCount)
        assertEquals(0, decoded.hazards)
        assertEquals(12, decoded.accuracyMeters)
        assertEquals(73, decoded.originBatteryPct)
    }

    @Test
    fun `akurasi dan baterai yang hilang menjadi null bukan nol`() {
        val bare = LocationFix(
            point = LatLon(latitude = 1.5, longitude = 2.5),
            accuracyMeters = null,
            ageSeconds = 0,
            batteryPct = null,
        )
        val decoded = SosCodec.decodeLoc(SosCodec.encodeLoc(SosKind.OTHER, bare, 0, 0))

        assertNull(decoded.accuracyMeters)
        assertNull(decoded.fixAgeSeconds)
        assertNull(decoded.originBatteryPct)
    }

    @Test
    fun `detail membawa referensi insiden yang benar`() {
        val incident = MessageId.of(NodeId(0xAABBCC), 0x012345)
        val decoded = SosCodec.decodeDetail(
            SosCodec.encodeDetail(incident, SosKind.TRAPPED, "kaki tersangkut di pagar"),
        )

        assertEquals(incident, decoded.incident)
        assertEquals(SosKind.TRAPPED, decoded.kind)
        assertEquals("kaki tersangkut di pagar", decoded.text)
    }

    @Test
    fun `detail non ascii tetap utf8 utuh`() {
        val incident = MessageId.of(NodeId(1), 1)
        val text = "Longsor di jalan utama, 3 orang terjebak — mohon_bucket dari atas"
        val decoded = SosCodec.decodeDetail(SosCodec.encodeDetail(incident, SosKind.OTHER, text))

        assertEquals(text, decoded.text)
    }

    @Test
    fun `detail dipotong pada batas maksimum`() {
        val incident = MessageId.of(NodeId(1), 2)
        val text = "x".repeat(MeshConfig.SOS_TEXT_MAX_BYTES + 50)

        val decoded = SosCodec.decodeDetail(SosCodec.encodeDetail(incident, SosKind.OTHER, text))

        assertEquals(MeshConfig.SOS_TEXT_MAX_BYTES, decoded.text.length)
    }

    @Test
    fun `sos loc terpotong ditolak bukan dibaca diam diam`() {
        val truncated = SosCodec.encodeLoc(SosKind.MEDICAL, fix, 1, 0).copyOfRange(0, 10)

        val error = runCatching { SosCodec.decodeLoc(truncated) }.exceptionOrNull()

        assertTrue(error is SosParseException)
    }

    @Test
    fun `koordinat nol nol ditolak`() {
        val bytes = SosCodec.encodeLoc(
            SosKind.OTHER,
            LocationFix(LatLon(0.0, 0.0), null, 0, null),
            0,
            0,
        )

        val error = runCatching { SosCodec.decodeLoc(bytes) }.exceptionOrNull()

        assertTrue(error is SosParseException)
    }

    @Test
    fun `sos kind tak dikenal turun ke unknown tanpa membuat error`() {
        val bytes = SosCodec.encodeLoc(SosKind.FLOOD, fix, 0, 0)
        val patched = bytes.copyOf().also { it[0] = 99.toByte() }

        val decoded = SosCodec.decodeLoc(patched)

        assertEquals(SosKind.UNKNOWN, decoded.kind)
    }

    @Test
    fun `bitmask hazard tidak bocor ke byte tetangga`() {
        val allHazards = SosHazard.NAMES.fold(0) { acc, (bit, _) -> acc or bit }
        val decoded = SosCodec.decodeLoc(
            SosCodec.encodeLoc(SosKind.OTHER, fix, victimCount = 1, hazards = allHazards),
        )

        assertEquals(allHazards, decoded.hazards)
    }

    @Test
    fun `encode decode losless untuk kasus lengkap`() {
        val original = SosCodec.encodeLoc(
            SosKind.ACCIDENT,
            fix,
            victimCount = 7,
            hazards = SosHazard.CONSCIOUS or SosHazard.HAZARD_AREA,
        )

        assertArrayEquals(original, SosCodec.encodeLoc(SosKind.ACCIDENT, fix, 7, SosHazard.CONSCIOUS or SosHazard.HAZARD_AREA))
        assertFalse(original.isEmpty())
    }
}
