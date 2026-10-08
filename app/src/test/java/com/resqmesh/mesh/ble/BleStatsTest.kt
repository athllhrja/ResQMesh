package com.resqmesh.mesh.ble

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BleStatsTest {

    @Before
    fun setUp() {
        BleStats.reset()
    }

    @Test
    fun `penghitung_ble_stats_dapat_diinkremen_dan_direset`() {
        BleStats.scanCallbackCount.incrementAndGet()
        BleStats.matchedFrameCount.incrementAndGet()
        BleStats.parseFailureCount.incrementAndGet()
        BleStats.advertiseStartSuccessCount.incrementAndGet()
        BleStats.advertiseFailureCount.incrementAndGet()
        BleStats.bluetoothStartResult = "Sukses"

        val snapshot = BleStats.snapshot()
        assertEquals(1L, snapshot.scanCallbackCount)
        assertEquals(1L, snapshot.matchedFrameCount)
        assertEquals(1L, snapshot.parseFailureCount)
        assertEquals(1L, snapshot.advertiseStartSuccessCount)
        assertEquals(1L, snapshot.advertiseFailureCount)
        assertEquals("Sukses", snapshot.bluetoothStartResult)

        BleStats.reset()
        val resetSnapshot = BleStats.snapshot()
        assertEquals(0L, resetSnapshot.scanCallbackCount)
        assertEquals(0L, resetSnapshot.matchedFrameCount)
        assertEquals(0L, resetSnapshot.parseFailureCount)
        assertEquals(0L, resetSnapshot.advertiseStartSuccessCount)
        assertEquals(0L, resetSnapshot.advertiseFailureCount)
        assertEquals("Belum Diuji", resetSnapshot.bluetoothStartResult)
    }

    @Test
    fun `tafsiran_tes_ble_start_gagal`() {
        val snapshot = BleStatsSnapshot(
            bluetoothStartResult = "Bluetooth mati atau tidak tersedia",
        )
        val interpretation = BleTestInterpreter.interpretTestResult(snapshot)
        assertEquals("Mulai BLE gagal: Bluetooth mati atau tidak tersedia", interpretation)
    }

    @Test
    fun `tafsiran_tes_ble_pemancaran_gagal`() {
        val snapshot = BleStatsSnapshot(
            bluetoothStartResult = "Sukses",
            advertiseStartSuccessCount = 0L,
            advertiseFailureCount = 5L,
        )
        val interpretation = BleTestInterpreter.interpretTestResult(snapshot)
        assertEquals("Pemancaran gagal: HP ini mungkin tidak mendukung advertising BLE", interpretation)
    }

    @Test
    fun `tafsiran_tes_ble_sinyal_diterima_tetapi_gagal_diurai`() {
        val snapshot = BleStatsSnapshot(
            bluetoothStartResult = "Sukses",
            advertiseStartSuccessCount = 10L,
            scanCallbackCount = 10L,
            matchedFrameCount = 0L,
            parseFailureCount = 3L,
        )
        val interpretation = BleTestInterpreter.interpretTestResult(snapshot)
        assertEquals("Sinyal diterima tetapi gagal diurai: versi APK berbeda", interpretation)
    }

    @Test
    fun `tafsiran_tes_ble_tidak_ada_sinyal_sama_sekali`() {
        val snapshot = BleStatsSnapshot(
            bluetoothStartResult = "Sukses",
            advertiseStartSuccessCount = 10L,
            scanCallbackCount = 0L,
            matchedFrameCount = 0L,
        )
        val interpretation = BleTestInterpreter.interpretTestResult(snapshot)
        assertEquals("Tidak ada sinyal dari HP lain: periksa jarak dan versi APK", interpretation)
    }

    @Test
    fun `tafsiran_tes_ble_sinyal_sekitar_bukan_resqmesh`() {
        val snapshot = BleStatsSnapshot(
            bluetoothStartResult = "Sukses",
            advertiseStartSuccessCount = 10L,
            scanCallbackCount = 25L,
            matchedFrameCount = 0L,
            parseFailureCount = 0L,
        )
        val interpretation = BleTestInterpreter.interpretTestResult(snapshot)
        assertEquals("Sinyal BLE sekitar terdeteksi tetapi bukan dari ResQMesh: periksa jarak dan versi APK", interpretation)
    }

    @Test
    fun `tafsiran_tes_ble_normal`() {
        val snapshot = BleStatsSnapshot(
            bluetoothStartResult = "Sukses",
            advertiseStartSuccessCount = 15L,
            scanCallbackCount = 30L,
            matchedFrameCount = 5L,
            parseFailureCount = 0L,
        )
        val interpretation = BleTestInterpreter.interpretTestResult(snapshot)
        assertEquals("BLE bekerja normal: pancaran dan penerimaan sinyal sukses", interpretation)
    }
}
