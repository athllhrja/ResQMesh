package com.resqmesh.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.resqmesh.core.MeshConfig
import com.resqmesh.core.SystemTimeProvider
import com.resqmesh.core.TimeProvider
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix

/** Hasil pembacaan lokasi, termasuk alasan gagal supaya UI bisa menjelaskan. */
sealed interface LocationResult {
    data class Ready(val fix: LocationFix) : LocationResult
    data object PermissionMissing : LocationResult
    data object ProviderDisabled : LocationResult
    data object NoRecentFix : LocationResult
}

/**
 * Sumber lokasi tanpa Google Play Services.
 *
 * `LocationManager` adalah pilihan yang disengaja: perangkat milik tim rescue
 * tidak selalu lolos peninjauan Play Store, dan aplikasi ini tidak butuh Place
 * API beresolusi tinggi yang hanya tersedia di Google.
 */
class LocationSource(
    context: Context,
    private val clock: TimeProvider = SystemTimeProvider,
) {
    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isProviderEnabled(): Boolean {
        val lm = manager ?: return false
        return PREFERRED_PROVIDERS.any { provider ->
            runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    /**
     * Fix terakhir yang masih segar.
     *
     * Sengaja memakai `getLastKnownLocation` dan bukan `requestSingleUpdate`:
     * tidak perlu izin tambahan maupun callback yang berbelit, dan cukup untuk
     * SOS karena operator menekan tombol sambil berdiri di lokasi yang sama
     * dengan GPS yang sedang aktif.
     */
    suspend fun currentFix(batteryPct: Int?): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionMissing
        val lm = manager ?: return LocationResult.ProviderDisabled
        if (!isProviderEnabled()) return LocationResult.ProviderDisabled

        val now = clock.now()
        val best = PREFERRED_PROVIDERS
            .mapNotNull { provider -> lastKnown(provider, lm) }
            .filter { isFresh(it, now) }
            .minByOrNull { accuracyOf(it) }

        val location = best ?: return LocationResult.NoRecentFix
        return LocationResult.Ready(toFix(location, now, batteryPct))
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(provider: String, lm: LocationManager): Location? =
        runCatching { lm.getLastKnownLocation(provider) }.getOrNull()

    /**
     * Fix dianggap segar bila umurnya masih di bawah
     * [MeshConfig.LOCATION_MAX_AGE_SEC]. Fix GPS yang menganggur dua puluh menit
     * karena layar mati akan ditolak: lebih baik mengirim tanpa koordinat
     * daripada mengirim lokasi keliru yang membuat penolong datang ke tempat
     * yang salah.
     */
    private fun isFresh(location: Location, now: Long): Boolean {
        val ageSeconds = (now - location.time).coerceAtLeast(0L) / 1000L
        return ageSeconds <= MeshConfig.LOCATION_MAX_AGE_SEC
    }

    private fun accuracyOf(location: Location): Float =
        if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE

    private fun toFix(location: Location, now: Long, batteryPct: Int?): LocationFix {
        val ageSeconds = ((now - location.time).coerceAtLeast(0L) / 1000L).toInt()
        return LocationFix(
            point = LatLon(location.latitude, location.longitude),
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toInt() else null,
            ageSeconds = ageSeconds,
            batteryPct = batteryPct,
        )
    }

    companion object {
        val PREFERRED_PROVIDERS =
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
