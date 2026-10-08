package com.resqmesh.data.location

import com.resqmesh.core.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationSourceTest {

    private lateinit var clock: MutableClock
    private lateinit var facade: FakeLocationManagerFacade
    private lateinit var source: LocationSource

    private class MutableClock(var timeMs: Long = 100_000L) : TimeProvider {
        override fun now(): Long = timeMs
    }

    private class FakeLocationManagerFacade : LocationManagerFacade {
        var finePermission = true
        var coarsePermission = true
        var gpsEnabled = true
        var networkEnabled = true

        var lastKnownGpsLocation: LocationData? = null
        var lastKnownNetworkLocation: LocationData? = null

        var activeGpsLocation: LocationData? = null
        var activeNetworkLocation: LocationData? = null

        val registeredListeners = mutableListOf<FakeListenerToken>()

        override fun hasFinePermission(): Boolean = finePermission
        override fun hasCoarsePermission(): Boolean = coarsePermission

        override fun isProviderEnabled(provider: String): Boolean = when (provider) {
            "gps" -> gpsEnabled
            "network" -> networkEnabled
            else -> false
        }

        override fun getLastKnownLocation(provider: String): LocationData? = when (provider) {
            "gps" -> lastKnownGpsLocation
            "network" -> lastKnownNetworkLocation
            else -> null
        }

        override suspend fun getCurrentLocation(provider: String, timeoutMs: Long): LocationData? = when (provider) {
            "gps" -> activeGpsLocation
            "network" -> activeNetworkLocation
            else -> null
        }

        override fun requestLocationUpdates(
            provider: String,
            intervalMs: Long,
            minDistanceMeters: Float,
            onLocationChanged: (LocationData) -> Unit,
        ): LocationListenerToken {
            val token = FakeListenerToken(provider, onLocationChanged)
            registeredListeners.add(token)
            return token
        }
    }

    private class FakeListenerToken(
        val provider: String,
        val callback: (LocationData) -> Unit,
    ) : LocationListenerToken {
        var cancelled = false
        override fun cancel() {
            cancelled = true
        }
    }

    private class FakeContext : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): android.content.Context = this
    }

    @Before
    fun setUp() {
        clock = MutableClock()
        facade = FakeLocationManagerFacade()
        source = LocationSource(
            context = FakeContext(),
            clock = clock,
            facade = facade,
        )
    }

    @Test
    fun `currentFix_kembalikan_PermissionMissing_saat_semua_izin_ditolak`() = runTest {
        facade.finePermission = false
        facade.coarsePermission = false

        val result = source.currentFix(batteryPct = 80)
        assertEquals(LocationResult.PermissionMissing, result)
    }

    @Test
    fun `currentFix_kembalikan_ApproximateOnly_saat_hanya_izin_coarse_diberikan`() = runTest {
        facade.finePermission = false
        facade.coarsePermission = true

        val result = source.currentFix(batteryPct = 80)
        assertEquals(LocationResult.ApproximateOnly, result)
    }

    @Test
    fun `currentFix_kembalikan_ProviderDisabled_saat_gps_dan_network_mati`() = runTest {
        facade.finePermission = true
        facade.coarsePermission = true
        facade.gpsEnabled = false
        facade.networkEnabled = false

        val result = source.currentFix(batteryPct = 80)
        assertEquals(LocationResult.ProviderDisabled, result)
    }

    @Test
    fun `currentFix_kembalikan_Ready_saat_last_known_masih_segar`() = runTest {
        val loc = LocationData("gps", clock.timeMs - 10_000L, -6.175, 106.827, 10f)
        facade.lastKnownGpsLocation = loc

        val result = source.currentFix(batteryPct = 80)
        assertTrue(result is LocationResult.Ready)
        val fix = (result as LocationResult.Ready).fix
        assertEquals(-6.175, fix.point.latitude, 0.0001)
        assertEquals(106.827, fix.point.longitude, 0.0001)
    }

    @Test
    fun `currentFix_lakukan_pencarian_aktif_saat_last_known_usang_atau_forceRefresh`() = runTest {
        val staleLoc = LocationData("gps", clock.timeMs - 300_000L, -6.175, 106.827, 10f)
        facade.lastKnownGpsLocation = staleLoc

        val activeLoc = LocationData("gps", clock.timeMs, -6.200, 106.850, 5f)
        facade.activeGpsLocation = activeLoc

        val result = source.currentFix(batteryPct = 80, forceRefresh = true)
        assertTrue(result is LocationResult.Ready)
        val fix = (result as LocationResult.Ready).fix
        assertEquals(-6.200, fix.point.latitude, 0.0001)
        assertEquals(106.850, fix.point.longitude, 0.0001)
    }

    @Test
    fun `currentFix_kembalikan_NoFixAfterTimeout_saat_pencarian_aktif_timeout`() = runTest {
        facade.lastKnownGpsLocation = null
        facade.activeGpsLocation = null
        facade.activeNetworkLocation = null

        val result = source.currentFix(batteryPct = 80, forceRefresh = true)
        assertEquals(LocationResult.NoFixAfterTimeout, result)
    }

    @Test
    fun `startPassiveLocationUpdates_mendaftarkan_listener_dan_stopPassiveLocationUpdates_membatalkannya`() {
        source.startPassiveLocationUpdates { }
        assertEquals(2, facade.registeredListeners.size) // GPS + Network

        source.stopPassiveLocationUpdates()
        assertTrue(facade.registeredListeners.all { it.cancelled })
    }
}
