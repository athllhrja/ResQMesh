package com.resqmesh.data.location

import android.content.Context
import com.resqmesh.domain.model.LocationFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level Location Provider wrapper adhering to Requirement F3.
 * Fetches a one-time location snapshot (latitude, longitude, accuracy, timestamp)
 * upon SOS creation with graceful fallback/safe defaults in case of missing permissions,
 * disabled providers, or stale fixes, preventing any crashes.
 */
class LocationProvider(
    private val context: Context,
) {
    private val locationSource = LocationSource(context)

    /**
     * Captures a one-time offline location snapshot.
     * Returns [LocationFix] if available and fresh, or `null` (safe default) on failure.
     */
    suspend fun fetchSnapshot(): LocationFix? = withContext(Dispatchers.IO) {
        val battery = BatteryReader.read(context)
        when (val result = locationSource.currentFix(battery)) {
            is LocationResult.Ready -> result.fix
            else -> null
        }
    }

    /** Checks whether fine or coarse location permission is granted. */
    fun hasPermission(): Boolean = locationSource.hasPermission()

    /** Checks whether GPS or Network location provider is enabled. */
    fun isProviderEnabled(): Boolean = locationSource.isProviderEnabled()
}
