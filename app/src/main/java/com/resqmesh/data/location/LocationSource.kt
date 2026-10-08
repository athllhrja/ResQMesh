package com.resqmesh.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.resqmesh.core.MeshConfig
import com.resqmesh.core.SystemTimeProvider
import com.resqmesh.core.TimeProvider
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Hasil pembacaan lokasi, termasuk alasan gagal supaya UI bisa menjelaskan. */
sealed interface LocationResult {
    data class Ready(val fix: LocationFix) : LocationResult
    data object Searching : LocationResult
    data object PermissionMissing : LocationResult
    data object ApproximateOnly : LocationResult
    data object ProviderDisabled : LocationResult
    data object NoRecentFix : LocationResult
    data object NoFixAfterTimeout : LocationResult
}

data class LocationData(
    val provider: String,
    val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)

interface LocationListenerToken {
    fun cancel()
}

interface LocationManagerFacade {
    fun hasFinePermission(): Boolean
    fun hasCoarsePermission(): Boolean
    fun isProviderEnabled(provider: String): Boolean
    fun getLastKnownLocation(provider: String): LocationData?
    suspend fun getCurrentLocation(provider: String, timeoutMs: Long): LocationData?
    fun requestLocationUpdates(
        provider: String,
        intervalMs: Long,
        minDistanceMeters: Float,
        onLocationChanged: (LocationData) -> Unit,
    ): LocationListenerToken?
}

class DefaultLocationManagerFacade(
    private val context: Context,
) : LocationManagerFacade {
    private val manager =
        runCatching { context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }.getOrNull()

    private val executor = runCatching { ContextCompat.getMainExecutor(context.applicationContext) }.getOrNull()

    override fun hasFinePermission(): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override fun hasCoarsePermission(): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override fun isProviderEnabled(provider: String): Boolean {
        val lm = manager ?: return false
        return runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    override fun getLastKnownLocation(provider: String): LocationData? {
        val lm = manager ?: return null
        val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: return null
        return loc.toData()
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(provider: String, timeoutMs: Long): LocationData? {
        val lm = manager ?: return null
        val exec = executor ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation {
                    runCatching { signal.cancel() }
                }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(
                        lm,
                        provider,
                        signal,
                        exec,
                    ) { location ->
                        if (continuation.isActive) {
                            continuation.resume(location?.toData())
                        }
                    }
                }.onFailure {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(
        provider: String,
        intervalMs: Long,
        minDistanceMeters: Float,
        onLocationChanged: (LocationData) -> Unit,
    ): LocationListenerToken? {
        val lm = manager ?: return null
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { onLocationChanged(location.toData()) }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }

        val registered = runCatching {
            lm.requestLocationUpdates(provider, intervalMs, minDistanceMeters, listener, android.os.Looper.getMainLooper())
            true
        }.getOrDefault(false)

        if (!registered) return null

        return object : LocationListenerToken {
            override fun cancel() {
                runCatching { lm.removeUpdates(listener) }
            }
        }
    }

    private fun Location.toData(): LocationData = LocationData(
        provider = this.provider ?: LocationManager.GPS_PROVIDER,
        timeMs = this.time,
        latitude = this.latitude,
        longitude = this.longitude,
        accuracyMeters = if (this.hasAccuracy()) this.accuracy else null,
    )
}

/**
 * Sumber lokasi tanpa Google Play Services.
 *
 * Menggunakan `LocationManagerFacade` untuk mendukung pencarian aktif
 * `getCurrentLocation` (20s timeout) dan pembaruan lokasi pasif periodik.
 */
class LocationSource(
    context: Context,
    private val clock: TimeProvider = SystemTimeProvider,
    facade: LocationManagerFacade? = null,
) {
    private val facade: LocationManagerFacade = facade ?: DefaultLocationManagerFacade(context)

    @Volatile var lastGpsFixTimeMs: Long = 0L
        private set
    @Volatile var lastNetworkFixTimeMs: Long = 0L
        private set
    @Volatile var lastSearchResult: String = "Belum dicari"
        private set

    private val passiveTokens = mutableListOf<LocationListenerToken>()

    fun hasPermission(): Boolean = runCatching { facade.hasFinePermission() || facade.hasCoarsePermission() }.getOrDefault(false)

    fun hasFinePermission(): Boolean = runCatching { facade.hasFinePermission() }.getOrDefault(false)

    fun isProviderEnabled(): Boolean = runCatching {
        facade.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            facade.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    fun isGpsEnabled(): Boolean = runCatching { facade.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

    fun isNetworkEnabled(): Boolean = runCatching { facade.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)

    /**
     * Mendapatkan lokasi. Pertama mencoba membaca `getLastKnownLocation` jika segar.
     * Jika tidak ada lokasi segar atau [forceRefresh] bernilai true, melakukan
     * pencarian aktif lewat `getCurrentLocation` (timeout 20 detik).
     */
    suspend fun currentFix(batteryPct: Int?, forceRefresh: Boolean = false): LocationResult {
        return runCatching {
            if (!hasPermission()) {
                lastSearchResult = "Gagal: Izin tidak diberikan"
                return@runCatching LocationResult.PermissionMissing
            }

            if (!hasFinePermission()) {
                lastSearchResult = "Gagal: Hanya izin perkiraan"
                return@runCatching LocationResult.ApproximateOnly
            }

            if (!isProviderEnabled()) {
                lastSearchResult = "Gagal: Provider mati"
                return@runCatching LocationResult.ProviderDisabled
            }

            val now = clock.now()

            // 1. Coba getLastKnownLocation bila tidak dipaksa pencarian aktif baru
            if (!forceRefresh) {
                val bestKnown = PREFERRED_PROVIDERS
                    .mapNotNull { provider ->
                        val loc = facade.getLastKnownLocation(provider)
                        if (loc != null) recordFixTime(provider, loc.timeMs)
                        loc
                    }
                    .filter { isFresh(it, now) }
                    .minByOrNull { accuracyOf(it) }

                if (bestKnown != null) {
                    lastSearchResult = "Sukses (LastKnown)"
                    return@runCatching LocationResult.Ready(toFix(bestKnown, now, batteryPct))
                }
            }

            // 2. Pencarian Aktif (20 detik) pada GPS lalu Network
            val activeGps = facade.getCurrentLocation(LocationManager.GPS_PROVIDER, ACTIVE_SEARCH_GPS_TIMEOUT_MS)
            if (activeGps != null) {
                recordFixTime(LocationManager.GPS_PROVIDER, activeGps.timeMs)
                lastSearchResult = "Sukses (GPS Aktif)"
                return@runCatching LocationResult.Ready(toFix(activeGps, clock.now(), batteryPct))
            }

            val activeNet = facade.getCurrentLocation(LocationManager.NETWORK_PROVIDER, ACTIVE_SEARCH_NET_TIMEOUT_MS)
            if (activeNet != null) {
                recordFixTime(LocationManager.NETWORK_PROVIDER, activeNet.timeMs)
                lastSearchResult = "Sukses (Network Aktif)"
                return@runCatching LocationResult.Ready(toFix(activeNet, clock.now(), batteryPct))
            }

            lastSearchResult = "Waktu habis (Timeout 20s)"
            LocationResult.NoFixAfterTimeout
        }.getOrElse { e ->
            lastSearchResult = "Error: ${e.message}"
            LocationResult.NoFixAfterTimeout
        }
    }

    /** Memulai pembaruan lokasi pasif periodik saat UI terlihat. */
    fun startPassiveLocationUpdates(
        intervalMs: Long = PASSIVE_INTERVAL_MS,
        onLocationReceived: (LocationFix) -> Unit,
    ) {
        runCatching {
            stopPassiveLocationUpdates()
            if (!hasPermission() || !isProviderEnabled()) return

            val callback: (LocationData) -> Unit = { loc ->
                val now = clock.now()
                recordFixTime(loc.provider, loc.timeMs)
                onLocationReceived(toFix(loc, now, null))
            }

            if (isGpsEnabled()) {
                facade.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, callback)?.let {
                    passiveTokens.add(it)
                }
            }
            if (isNetworkEnabled()) {
                facade.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, intervalMs, 0f, callback)?.let {
                    passiveTokens.add(it)
                }
            }
        }
    }

    /** Menghentikan pembaruan lokasi pasif agar tidak menyedot baterai. */
    fun stopPassiveLocationUpdates() {
        runCatching {
            passiveTokens.forEach { runCatching { it.cancel() } }
            passiveTokens.clear()
        }
    }

    private fun recordFixTime(provider: String, timeMs: Long) {
        if (provider == LocationManager.GPS_PROVIDER) {
            lastGpsFixTimeMs = timeMs
        } else if (provider == LocationManager.NETWORK_PROVIDER) {
            lastNetworkFixTimeMs = timeMs
        }
    }

    private fun isFresh(location: LocationData, now: Long): Boolean {
        val ageSeconds = (now - location.timeMs).coerceAtLeast(0L) / 1000L
        return ageSeconds <= MeshConfig.LOCATION_MAX_AGE_SEC
    }

    private fun accuracyOf(location: LocationData): Float =
        location.accuracyMeters ?: Float.MAX_VALUE

    private fun toFix(location: LocationData, now: Long, batteryPct: Int?): LocationFix {
        val ageSeconds = ((now - location.timeMs).coerceAtLeast(0L) / 1000L).toInt()
        return LocationFix(
            point = LatLon(location.latitude, location.longitude),
            accuracyMeters = location.accuracyMeters?.toInt(),
            ageSeconds = ageSeconds,
            batteryPct = batteryPct,
        )
    }

    companion object {
        val PREFERRED_PROVIDERS =
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        const val ACTIVE_SEARCH_GPS_TIMEOUT_MS = 20_000L
        const val ACTIVE_SEARCH_NET_TIMEOUT_MS = 10_000L
        const val PASSIVE_INTERVAL_MS = 5_000L
    }
}
