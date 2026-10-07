package com.resqmesh.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.resqmesh.core.MeshConfig
import com.resqmesh.data.codec.FrameCodec
import com.resqmesh.domain.model.FrameType
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.NodeId
import com.resqmesh.mesh.MeshTransport

/*
 * ASUMSI HEADER BLE ADVERTISING & BUDGET PAYLOAD 27 BYTE:
 * 1. PDU Advertising BLE Legacy memiliki kapasitas payload maksimum 31 byte.
 * 2. Menambahkan Manufacturer Specific Data (0xFF) menambahkan 3 byte header PDU:
 *    - Length (1 byte)
 *    - AD Type 0xFF (1 byte)
 *    - Company ID (2 byte, e.g. 0xE000)
 * 3. Dengan payload ResQMesh dikunci tepat pada 27 byte, total PDU adalah 3+2+27 = 32 byte.
 *    Beberapa controller Bluetooth BLE legacy menyisipkan Flags AD Type (3 byte), sehingga total
 *    melebihi 31 byte jika Flags diikutsertakan.
 * 4. setConnectable(false), setIncludeDeviceName(false), dan setIncludeTxPowerLevel(false)
 *    dijaga tetap false agar budget payload 27 byte muat di PDU BLE tanpa terpotong.
 *    [PERLU DIUJI PER PERANGKAT] — perilaku ini bervariasi antar chipset Bluetooth (Qualcomm, MediaTek, Exynos).
 */

/**
 * Transport BLE sungguhan untuk ResQMesh.
 *
 * Menangani BLE Scanning & Advertising untuk menemukan node di sekitar secara
 * offline, menyiarkan frame payload 27 byte (termasuk Beacon dengan Node ID),
 * dan melaporkan RSSI node peer untuk pelacakan node lokal.
 */
class BleMeshTransport(
    context: Context,
    private val codec: FrameCodec,
) : MeshTransport {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var activeAdvertisingSet: AdvertisingSet? = null

    private var onFrameCallback: ((MeshFrame, Int) -> Unit)? = null
    private var onBeaconCallback: ((ByteArray, Int) -> Unit)? = null

    @Volatile
    private var isRunning = false

    // Variabel diagnostik & timing
    private var lastAdvertiseTimeMs = 0L
    private var advertiseSuccessCount = 0L
    private var advertiseFailureCount = 0L

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result == null) return
            val record = result.scanRecord ?: return
            val rssi = result.rssi

            var payload = record.getManufacturerSpecificData(MeshConfig.COMPANY_ID)
            if (payload == null || payload.size < 2) {
                payload = parseRawManufacturerData(record.bytes, MeshConfig.COMPANY_ID)
            }

            if (payload == null || payload.size < 2) return

            processPayload(payload, rssi)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan gagal, error code: $errorCode")
        }
    }

    // Callback Diagnostik Legacy Advertising
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            val now = System.currentTimeMillis()
            val intervalMs = if (lastAdvertiseTimeMs > 0) now - lastAdvertiseTimeMs else 0
            lastAdvertiseTimeMs = now
            advertiseSuccessCount++
            Log.d(
                TAG,
                "BLE Advertising Legacy sukses | Selang waktu aktual: ${intervalMs}ms | Total sukses: $advertiseSuccessCount",
            )
        }

        override fun onStartFailure(errorCode: Int) {
            advertiseFailureCount++
            Log.e(
                TAG,
                "BLE Advertising Legacy gagal [Code $errorCode: ${describeAdvertiseError(errorCode)}] | Total gagal: $advertiseFailureCount",
            )
        }
    }

    // Callback Diagnostik AdvertisingSet (API 26+)
    private val advertisingSetCallback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(
            advertisingSet: AdvertisingSet?,
            txPower: Int,
            status: Int,
        ) {
            if (status == ADVERTISE_SUCCESS) {
                activeAdvertisingSet = advertisingSet
                val now = System.currentTimeMillis()
                val intervalMs = if (lastAdvertiseTimeMs > 0) now - lastAdvertiseTimeMs else 0
                lastAdvertiseTimeMs = now
                advertiseSuccessCount++
                Log.d(
                    TAG,
                    "BLE AdvertisingSet aktif (txPower=$txPower) | Selang waktu aktual: ${intervalMs}ms | Total sukses: $advertiseSuccessCount",
                )
            } else {
                activeAdvertisingSet = null
                advertiseFailureCount++
                Log.e(
                    TAG,
                    "BLE AdvertisingSet gagal dimulai [Status $status] | Total gagal: $advertiseFailureCount",
                )
            }
        }

        override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet?, status: Int) {
            if (status == ADVERTISE_SUCCESS) {
                val now = System.currentTimeMillis()
                val intervalMs = if (lastAdvertiseTimeMs > 0) now - lastAdvertiseTimeMs else 0
                lastAdvertiseTimeMs = now
                advertiseSuccessCount++
                Log.d(
                    TAG,
                    "BLE AdvertisingSet payload berhasil diperbarui tanpa stop/start | Selang waktu aktual: ${intervalMs}ms | Total sukses: $advertiseSuccessCount",
                )
            } else {
                Log.e(TAG, "BLE AdvertisingSet gagal memperbarui payload [Status $status]")
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun start(
        onFrame: (MeshFrame, Int) -> Unit,
        onBeacon: (ByteArray, Int) -> Unit,
    ): Result<Unit> {
        if (isRunning) return Result.success(Unit)
        this.onFrameCallback = onFrame
        this.onBeaconCallback = onBeacon

        return try {
            val adapter = bluetoothAdapter
            if (adapter == null || !adapter.isEnabled) {
                Log.w(TAG, "Bluetooth mati atau tidak didukung pada perangkat ini")
                return Result.failure(IllegalStateException("Bluetooth mati atau tidak tersedia"))
            }

            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                Log.w(TAG, "BluetoothLeScanner tidak tersedia")
                return Result.failure(IllegalStateException("BluetoothLeScanner tidak tersedia"))
            }

            isRunning = true
            startScanning()
            Result.success(Unit)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException saat memulai BleMeshTransport", e)
            isRunning = false
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException saat memulai BleMeshTransport", e)
            isRunning = false
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai BleMeshTransport", e)
            isRunning = false
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        if (!isRunning) return
        isRunning = false

        stopScanning()
        stopAdvertising()

        onFrameCallback = null
        onBeaconCallback = null
    }

    @SuppressLint("MissingPermission")
    override fun advertise(payload: ByteArray) {
        if (!isRunning) return

        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        advertiser = adapter.bluetoothLeAdvertiser
        val leAdvertiser = advertiser
        if (leAdvertiser == null) {
            Log.w(TAG, "BluetoothLeAdvertiser tidak didukung")
            return
        }

        val frameType = payload.getOrNull(1)?.let { FrameType.fromCode(it.toInt() and 0xFF) } ?: FrameType.UNKNOWN
        val modeLabel = if (MeshConfig.USE_BLE_ADVERTISING_SET) "AdvertisingSet (API 26+)" else "Legacy"
        Log.d(
            TAG,
            "Mempersiapkan pancaran BLE payload (${payload.size} byte, type=$frameType, mode=$modeLabel)",
        )

        val data = buildAdvertiseData(payload)

        if (MeshConfig.USE_BLE_ADVERTISING_SET) {
            advertiseSet(leAdvertiser, data)
        } else {
            advertiseLegacy(leAdvertiser, data)
        }
    }

    @SuppressLint("MissingPermission")
    private fun advertiseLegacy(leAdvertiser: BluetoothLeAdvertiser, data: AdvertiseData) {
        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        runCatching {
            leAdvertiser.startAdvertising(settings, data, advertiseCallback)
        }.onFailure { e ->
            Log.e(TAG, "Gagal memulai BLE advertising legacy: ${e.message}", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun advertiseSet(leAdvertiser: BluetoothLeAdvertiser, data: AdvertiseData) {
        val activeSet = activeAdvertisingSet
        if (activeSet != null) {
            runCatching {
                activeSet.setAdvertisingData(data)
            }.onFailure { e ->
                Log.e(TAG, "Gagal memperbarui payload AdvertisingSet: ${e.message}, mencoba restart set", e)
                stopAdvertising()
                startNewAdvertisingSet(leAdvertiser, data)
            }
        } else {
            startNewAdvertisingSet(leAdvertiser, data)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startNewAdvertisingSet(leAdvertiser: BluetoothLeAdvertiser, data: AdvertiseData) {
        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(true)
            .setConnectable(false)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
            .build()

        runCatching {
            leAdvertiser.startAdvertisingSet(
                parameters,
                data,
                null,
                null,
                null,
                advertisingSetCallback,
            )
        }.onFailure { e ->
            Log.e(TAG, "Gagal memutus/memulai AdvertisingSet baru: ${e.message}", e)
        }
    }

    private fun buildAdvertiseData(payload: ByteArray): AdvertiseData =
        AdvertiseData.Builder()
            .addManufacturerData(MeshConfig.COMPANY_ID, payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

    override fun sendOverLink(peerId: NodeId, frames: List<MeshFrame>) {
        // TODO(not implemented): Tier 2 Direct GATT Link belum diimplementasikan.
        // Komunikasi multi-hop saat ini sepenuhnya mengandalkan Tier 1 BLE Advertising store-and-forward.
        Log.d(TAG, "sendOverLink dipanggil untuk $peerId dengan ${frames.size} frame - TODO(not implemented)")
    }

    override fun connectedPeers(): Set<NodeId> = emptySet()

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val adapter = bluetoothAdapter ?: throw IllegalStateException("Bluetooth adapter null")
        scanner = adapter.bluetoothLeScanner
        val leScanner = scanner ?: throw IllegalStateException("BluetoothLeScanner tidak tersedia")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = ScanFilter.Builder()
            .setManufacturerData(MeshConfig.COMPANY_ID, byteArrayOf())
            .build()

        try {
            leScanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            throw e
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Scan dengan filter gagal, mencoba scan tanpa filter", e)
            leScanner.startScan(null, settings, scanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        runCatching {
            scanner?.stopScan(scanCallback)
        }
        scanner = null
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        runCatching {
            advertiser?.stopAdvertising(advertiseCallback)
        }
        runCatching {
            advertiser?.stopAdvertisingSet(advertisingSetCallback)
        }
        activeAdvertisingSet = null
    }

    private fun processPayload(payload: ByteArray, rssi: Int) {
        val frameType = payload.getOrNull(1)?.let { FrameType.fromCode(it.toInt() and 0xFF) } ?: return
        when (frameType) {
            FrameType.BEACON -> {
                onBeaconCallback?.invoke(payload, rssi)
            }
            FrameType.MSG -> {
                val frame = runCatching { codec.decode(payload) }.getOrNull()
                if (frame != null) {
                    onFrameCallback?.invoke(frame, rssi)
                }
            }
            FrameType.UNKNOWN -> Unit
        }
    }

    private fun parseRawManufacturerData(bytes: ByteArray, companyId: Int): ByteArray? {
        var offset = 0
        while (offset < bytes.size - 2) {
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0) break
            if (offset + 1 >= bytes.size) break

            val type = bytes[offset + 1].toInt() and 0xFF
            if (type == 0xFF && length >= 3) {
                val id = ((bytes[offset + 3].toInt() and 0xFF) shl 8) or (bytes[offset + 2].toInt() and 0xFF)
                if (id == companyId) {
                    val dataLength = length - 3
                    if (offset + 4 + dataLength <= bytes.size) {
                        return bytes.copyOfRange(offset + 4, offset + 4 + dataLength)
                    }
                }
            }
            offset += length + 1
        }
        return null
    }

    private fun describeAdvertiseError(errorCode: Int): String = when (errorCode) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE (1)"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS (2)"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED (3)"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR (4)"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED (5)"
        else -> "UNKNOWN_ERROR ($errorCode)"
    }

    companion object {
        private const val TAG = "BleMeshTransport"
    }
}
