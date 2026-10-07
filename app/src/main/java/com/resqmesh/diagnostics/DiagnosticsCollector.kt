package com.resqmesh.diagnostics

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import com.resqmesh.ui.MeshPermissions

data class DiagnosticsInfo(
    val brand: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val versionName: String,
    val versionCode: Long,
    val applicationId: String,

    val bluetoothSupported: Boolean,
    val bluetoothEnabled: Boolean,
    val multipleAdvertisementSupported: Boolean,
    val extendedAdvertisingSupported: Boolean,

    val hasBluetoothPermissions: Boolean,
    val hasLocationPermissions: Boolean,
    val hasNotificationPermissions: Boolean,

    val isLocationProviderEnabled: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    val isMeshServiceRunning: Boolean,

    val meshPhase: String,
    val meshLastError: String?,
) {
    fun toFormattedText(): String {
        return """
            === DIAGNOSTIK RESQMESH ===
            Application ID  : $applicationId
            Versi Aplikasi  : $versionName (code $versionCode)
            Perangkat       : $manufacturer $brand $model
            Android Version : $androidVersion (SDK $sdkInt)

            --- Bluetooth ---
            Bluetooth Didukung        : ${if (bluetoothSupported) "Ya" else "Tidak"}
            Bluetooth Aktif           : ${if (bluetoothEnabled) "Ya" else "Tidak"}
            Multiple Advertisement    : ${if (multipleAdvertisementSupported) "Didukung" else "Tidak Didukung"}
            Extended Advertising      : ${if (extendedAdvertisingSupported) "Didukung" else "Tidak Didukung"}

            --- Status Izin ---
            Izin Bluetooth / BLE      : ${if (hasBluetoothPermissions) "Diberikan" else "Ditolak"}
            Izin Lokasi               : ${if (hasLocationPermissions) "Diberikan" else "Ditolak"}
            Izin Notifikasi           : ${if (hasNotificationPermissions) "Diberikan" else "Ditolak"}

            --- Layanan & Sistem ---
            Layanan Lokasi (GPS)      : ${if (isLocationProviderEnabled) "Aktif" else "Mati"}
            Abaikan Optimasi Baterai  : ${if (isIgnoringBatteryOptimizations) "Ya" else "Tidak"}
            MeshService Berjalan      : ${if (isMeshServiceRunning) "Ya" else "Tidak"}

            --- Status Mesh ---
            Fase Mesh                 : $meshPhase
            Last Error                : ${meshLastError ?: "-"}
        """.trimIndent()
    }
}

object DiagnosticsCollector {

    fun collect(
        context: Context,
        meshPhase: String = "UNKNOWN",
        meshLastError: String? = null,
        isMeshServiceRunning: Boolean = false,
    ): DiagnosticsInfo {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()

        val versionName = packageInfo?.versionName ?: "1.0.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 1L
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: 1L
        }

        val bluetoothManager = runCatching {
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        }.getOrNull()

        val bluetoothAdapter = runCatching { bluetoothManager?.adapter }.getOrNull()

        val btSupported = bluetoothAdapter != null
        val btEnabled = runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)
        val multiAdvSupported = runCatching { bluetoothAdapter?.isMultipleAdvertisementSupported == true }.getOrDefault(false)
        val extAdvSupported = runCatching { bluetoothAdapter?.isLeExtendedAdvertisingSupported == true }.getOrDefault(false)

        val hasBtPerm = runCatching { MeshPermissions.hasBluetooth(context) }.getOrDefault(false)
        val hasLocPerm = runCatching { MeshPermissions.hasLocation(context) }.getOrDefault(false)
        val hasNotifPerm = runCatching { MeshPermissions.hasNotification(context) }.getOrDefault(false)

        val lm = runCatching { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }.getOrNull()
        val locEnabled = runCatching {
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        }.getOrDefault(false)

        val powerManager = runCatching { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }.getOrNull()
        val ignoringBattery = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else {
                true
            }
        }.getOrDefault(false)

        return DiagnosticsInfo(
            brand = Build.BRAND ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            versionName = versionName,
            versionCode = versionCode,
            applicationId = context.packageName,
            bluetoothSupported = btSupported,
            bluetoothEnabled = btEnabled,
            multipleAdvertisementSupported = multiAdvSupported,
            extendedAdvertisingSupported = extAdvSupported,
            hasBluetoothPermissions = hasBtPerm,
            hasLocationPermissions = hasLocPerm,
            hasNotificationPermissions = hasNotifPerm,
            isLocationProviderEnabled = locEnabled,
            isIgnoringBatteryOptimizations = ignoringBattery,
            isMeshServiceRunning = isMeshServiceRunning,
            meshPhase = meshPhase,
            meshLastError = meshLastError,
        )
    }

    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            @Suppress("DEPRECATION")
            am?.getRunningServices(Int.MAX_VALUE)?.any { it.service.className == serviceClass.name } == true
        }.getOrDefault(false)
    }
}
