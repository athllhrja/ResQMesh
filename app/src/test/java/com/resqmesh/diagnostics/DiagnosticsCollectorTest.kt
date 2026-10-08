package com.resqmesh.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsCollectorTest {

    @Test
    fun `toFormattedText_menghasilkan_ringkasan_teks_diagnostik_lengkap`() {
        val info = DiagnosticsInfo(
            brand = "Google",
            model = "Pixel 8",
            manufacturer = "Google",
            androidVersion = "14",
            sdkInt = 34,
            versionName = "1.0.0",
            versionCode = 100L,
            applicationId = "com.resqmesh.field",
            bluetoothSupported = true,
            bluetoothEnabled = true,
            multipleAdvertisementSupported = true,
            extendedAdvertisingSupported = true,
            hasBluetoothPermissions = true,
            hasLocationPermissions = true,
            hasPreciseLocationPermissions = true,
            hasApproximateLocationPermissions = true,
            hasNotificationPermissions = true,
            isLocationProviderEnabled = true,
            isGpsProviderEnabled = true,
            isNetworkProviderEnabled = true,
            lastGpsFixAgeSeconds = 15L,
            lastNetworkFixAgeSeconds = 30L,
            lastLocationSearchResult = "Sukses (GPS Aktif)",
            isIgnoringBatteryOptimizations = true,
            isMeshServiceRunning = true,
            meshPhase = "ACTIVE",
            meshLastError = null,
        )

        val text = info.toFormattedText()

        assertTrue(text.contains("=== DIAGNOSTIK RESQMESH ==="))
        assertTrue(text.contains("Application ID  : com.resqmesh.field"))
        assertTrue(text.contains("Versi Aplikasi  : 1.0.0 (code 100)"))
        assertTrue(text.contains("Perangkat       : Google Google Pixel 8"))
        assertTrue(text.contains("Bluetooth Didukung        : Ya"))
        assertTrue(text.contains("Izin Bluetooth / BLE      : Diberikan"))
        assertTrue(text.contains("Izin Lokasi Presisi       : Diberikan"))
        assertTrue(text.contains("GPS Provider              : Aktif (Last Fix: 15s lalu)"))
        assertTrue(text.contains("Hasil Pencarian Terakhir  : Sukses (GPS Aktif)"))
        assertTrue(text.contains("MeshService Berjalan      : Ya"))
        assertTrue(text.contains("Fase Mesh                 : ACTIVE"))
        assertTrue(text.contains("Last Error                : -"))
    }
}
