package com.resqmesh.mesh.ble

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.resqmesh.core.MeshConfig
import com.resqmesh.data.codec.FrameCodec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kerangka Pengujian On-Device / Manual untuk [BleMeshTransport].
 *
 * Catatan: Pengujian ini memerlukan perangkat fisik dengan chip Bluetooth LE yang aktif
 * dan izin runtime Bluetooth (BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT).
 * [BELUM DIJALANKAN DI PERANGKAT FISIK]
 */
@RunWith(AndroidJUnit4::class)
class BleMeshTransportTest {

    @Test
    fun inisialisasi_transport_ble_pada_perangkat() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val codec = FrameCodec()
        val transport = BleMeshTransport(appContext, codec)
        assertNotNull(transport)
    }

    @Test
    fun verifikasi_konfigurasi_advertising_set_flag() {
        // Verifikasi bahwa flag konfigurasi BLE AdvertisingSet dapat dibaca pada runtime
        val isAdvertisingSetEnabled = MeshConfig.USE_BLE_ADVERTISING_SET
        assertFalse(isAdvertisingSetEnabled)
    }
}
