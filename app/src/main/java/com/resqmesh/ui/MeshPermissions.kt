package com.resqmesh.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.resqmesh.data.location.LocationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Izin yang dibutuhkan mesh tetap jalan.
 *
 * Pemisahan ini penting untuk kasus darurat: menolak izin lokasi **tidak boleh**
 * mematikan mesh. Tanpa lokasi, SOS masih menyiarkan jenis dan catatan; hanya
 * pin yang hilang. Aplikasi yang tiba-tiba mati saat izin ditolak justru membuat
 * penolong tidak bisa mengirim apa pun.
 */
object MeshPermissions {

    private val LOCATION = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val BLUETOOTH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    private val NOTIFICATION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    val locationPermissions: Array<String> = LOCATION

    val bluetoothPermissions: Array<String> = BLUETOOTH

    val notificationPermissions: Array<String> = NOTIFICATION

    val servicePermissions: Array<String> = BLUETOOTH + NOTIFICATION

    fun hasBluetooth(context: Context): Boolean =
        BLUETOOTH.all { granted(context, it) }

    fun hasNotification(context: Context): Boolean =
        NOTIFICATION.all { granted(context, it) }

    fun canStartService(context: Context): Boolean =
        hasBluetooth(context) && hasNotification(context)

    fun hasLocation(context: Context): Boolean =
        LOCATION.any { granted(context, it) }

    fun hasPreciseLocation(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** Izin yang belum terpenuhi, untuk langsung diminta dari UI. */
    fun missing(context: Context): List<String> =
        (BLUETOOTH + NOTIFICATION + LOCATION).distinct().filterNot { granted(context, it) }

    fun missingForService(context: Context): List<String> =
        (BLUETOOTH + NOTIFICATION).filterNot { granted(context, it) }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Keadaan izin yang diamati UI, terpisah dari logika mesh. */
class PermissionState(
    private val context: Context,
    private val locationSource: LocationSource,
) {
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<PermissionSnapshot> = _state.asStateFlow()

    fun refresh() {
        _state.value = snapshot()
    }

    private fun snapshot() = PermissionSnapshot(
        hasBluetooth = MeshPermissions.hasBluetooth(context),
        hasNotification = MeshPermissions.hasNotification(context),
        canStartService = MeshPermissions.canStartService(context),
        hasLocation = MeshPermissions.hasLocation(context),
        hasPreciseLocation = MeshPermissions.hasPreciseLocation(context),
        isLocationProviderEnabled = locationSource.isProviderEnabled(),
    )
}

data class PermissionSnapshot(
    val hasBluetooth: Boolean,
    val hasNotification: Boolean,
    val canStartService: Boolean,
    val hasLocation: Boolean,
    val hasPreciseLocation: Boolean,
    val isLocationProviderEnabled: Boolean,
) {
    /**
     * Izin lokasi lengkap berarti koordinat presisi bisa dikirim. Kalau hanya
     * COARSE, pin tetap ada tapi akurasinya jadi meteran atau lebih kasar.
     */
    val canSendPreciseLocation: Boolean get() = hasPreciseLocation

    val needsRuntimeRequest: Boolean get() = !canStartService || !hasLocation
}
