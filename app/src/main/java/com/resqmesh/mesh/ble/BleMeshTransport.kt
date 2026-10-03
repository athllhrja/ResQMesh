package com.resqmesh.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
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

    private var onFrameCallback: ((MeshFrame, Int) -> Unit)? = null
    private var onBeaconCallback: ((ByteArray, Int) -> Unit)? = null

    @Volatile
    private var isRunning = false

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

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "BLE Advertising aktif")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertising gagal, error code: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    override fun start(
        onFrame: (MeshFrame, Int) -> Unit,
        onBeacon: (ByteArray, Int) -> Unit,
    ) {
        if (isRunning) return
        this.onFrameCallback = onFrame
        this.onBeaconCallback = onBeacon

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth mati atau tidak didukung pada perangkat ini")
            return
        }

        isRunning = true
        startScanning()
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

        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(MeshConfig.COMPANY_ID, payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        runCatching {
            leAdvertiser.startAdvertising(settings, data, advertiseCallback)
        }.onFailure { e ->
            Log.e(TAG, "Gagal memulai BLE advertising: ${e.message}", e)
        }
    }

    override fun sendOverLink(peerId: NodeId, frames: List<MeshFrame>) {
        // Tier 2 Direct Link (GATT)
    }

    override fun connectedPeers(): Set<NodeId> = emptySet()

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val adapter = bluetoothAdapter ?: return
        scanner = adapter.bluetoothLeScanner
        val leScanner = scanner
        if (leScanner == null) {
            Log.w(TAG, "BluetoothLeScanner tidak tersedia")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = ScanFilter.Builder()
            .setManufacturerData(MeshConfig.COMPANY_ID, byteArrayOf())
            .build()

        runCatching {
            leScanner.startScan(listOf(filter), settings, scanCallback)
        }.onFailure { e ->
            Log.e(TAG, "Scan dengan filter gagal, mencoba scan tanpa filter", e)
            runCatching {
                leScanner.startScan(null, settings, scanCallback)
            }.onFailure { err ->
                Log.e(TAG, "BLE Scan gagal: ${err.message}", err)
            }
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

    companion object {
        private const val TAG = "BleMeshTransport"
    }
}
