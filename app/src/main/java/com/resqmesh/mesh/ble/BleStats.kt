package com.resqmesh.mesh.ble

import com.resqmesh.core.MeshConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object BleStats {
    val scanCallbackCount = AtomicLong(0)
    val matchedFrameCount = AtomicLong(0)
    val parseFailureCount = AtomicLong(0)
    val scanFailCount = AtomicLong(0)
    val lastScanErrorCode = AtomicInteger(-1)

    val advertiseStartSuccessCount = AtomicLong(0)
    val advertiseFailureCount = AtomicLong(0)
    val lastAdvertiseErrorCode = AtomicInteger(-1)

    @Volatile var lastBeaconReceivedAtMs: Long = 0L
    @Volatile var lastBeaconRssi: Int = 0

    @Volatile var advertiseMode: String = if (MeshConfig.USE_BLE_ADVERTISING_SET) "AdvertisingSet (API 26+)" else "Legacy"
    @Volatile var bluetoothStartResult: String = "Belum Diuji"

    fun reset() {
        scanCallbackCount.set(0)
        matchedFrameCount.set(0)
        parseFailureCount.set(0)
        scanFailCount.set(0)
        lastScanErrorCode.set(-1)

        advertiseStartSuccessCount.set(0)
        advertiseFailureCount.set(0)
        lastAdvertiseErrorCode.set(-1)

        lastBeaconReceivedAtMs = 0L
        lastBeaconRssi = 0

        bluetoothStartResult = "Belum Diuji"
    }

    fun describeAdvertiseError(errorCode: Int): String = when (errorCode) {
        1 -> "DATA_TOO_LARGE (1)"
        2 -> "TOO_MANY_ADVERTISERS (2)"
        3 -> "ALREADY_STARTED (3)"
        4 -> "INTERNAL_ERROR (4)"
        5 -> "FEATURE_UNSUPPORTED (5)"
        -1 -> "Tidak Ada Error"
        else -> "UNKNOWN_ERROR ($errorCode)"
    }

    fun snapshot(): BleStatsSnapshot = BleStatsSnapshot(
        scanCallbackCount = scanCallbackCount.get(),
        matchedFrameCount = matchedFrameCount.get(),
        parseFailureCount = parseFailureCount.get(),
        scanFailCount = scanFailCount.get(),
        lastScanErrorCode = lastScanErrorCode.get(),
        advertiseStartSuccessCount = advertiseStartSuccessCount.get(),
        advertiseFailureCount = advertiseFailureCount.get(),
        lastAdvertiseErrorCode = lastAdvertiseErrorCode.get(),
        lastAdvertiseErrorDescription = describeAdvertiseError(lastAdvertiseErrorCode.get()),
        lastBeaconReceivedAtMs = lastBeaconReceivedAtMs,
        lastBeaconRssi = lastBeaconRssi,
        advertiseMode = advertiseMode,
        bluetoothStartResult = bluetoothStartResult,
    )
}

data class BleStatsSnapshot(
    val scanCallbackCount: Long = 0,
    val matchedFrameCount: Long = 0,
    val parseFailureCount: Long = 0,
    val scanFailCount: Long = 0,
    val lastScanErrorCode: Int = -1,
    val advertiseStartSuccessCount: Long = 0,
    val advertiseFailureCount: Long = 0,
    val lastAdvertiseErrorCode: Int = -1,
    val lastAdvertiseErrorDescription: String = "Tidak Ada Error",
    val lastBeaconReceivedAtMs: Long = 0,
    val lastBeaconRssi: Int = 0,
    val advertiseMode: String = "Legacy",
    val bluetoothStartResult: String = "Belum Diuji",
) {
    fun toFormattedText(): String {
        val lastBeaconStr = if (lastBeaconReceivedAtMs > 0) {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(lastBeaconReceivedAtMs))
            "$date (RSSI $lastBeaconRssi dBm)"
        } else {
            "Belum pernah diterima"
        }

        return """
            --- Statistik BLE (sejak mesh dimulai) ---
            Status Start BLE          : $bluetoothStartResult
            Mode Advertising          : $advertiseMode
            Pancaran Sukses (Adv)     : $advertiseStartSuccessCount
            Pancaran Gagal (Adv)      : $advertiseFailureCount (Err: $lastAdvertiseErrorDescription)
            Total Scan Callback       : $scanCallbackCount
            Frame Cocok (Company ID)  : $matchedFrameCount
            Gagal Urai (Parse Err)    : $parseFailureCount
            Scan Gagal (Scan Err)     : $scanFailCount (Code: ${if (lastScanErrorCode >= 0) lastScanErrorCode else "-"})
            Beacon Terakhir Diterima  : $lastBeaconStr

            Keterangan Arti Angka:
            - Total Scan > 0 tetapi Cocok = 0: HP menerima sinyal BLE lain tetapi bukan dari ResQMesh yang sama versinya.
            - Cocok > 0 tetapi Gagal Urai > 0: Sinyal ResQMesh diterima tetapi versi APK atau format payload berbeda.
            - Adv Gagal > 0: Chipset BLE HP ini menolak/tidak mendukung penyiaran data (advertising).
        """.trimIndent()
    }
}

object BleTestInterpreter {
    fun interpretTestResult(snapshot: BleStatsSnapshot): String {
        return when {
            snapshot.bluetoothStartResult != "Sukses" && snapshot.bluetoothStartResult != "Belum Diuji" -> {
                "Mulai BLE gagal: ${snapshot.bluetoothStartResult}"
            }
            snapshot.advertiseFailureCount > 0 && snapshot.advertiseStartSuccessCount == 0L -> {
                "Pemancaran gagal: HP ini mungkin tidak mendukung advertising BLE"
            }
            snapshot.parseFailureCount > 0 && snapshot.matchedFrameCount == 0L -> {
                "Sinyal diterima tetapi gagal diurai: versi APK berbeda"
            }
            snapshot.matchedFrameCount == 0L && snapshot.scanCallbackCount == 0L -> {
                "Tidak ada sinyal dari HP lain: periksa jarak dan versi APK"
            }
            snapshot.matchedFrameCount == 0L && snapshot.scanCallbackCount > 0L -> {
                "Sinyal BLE sekitar terdeteksi tetapi bukan dari ResQMesh: periksa jarak dan versi APK"
            }
            else -> {
                "BLE bekerja normal: pancaran dan penerimaan sinyal sukses"
            }
        }
    }
}
