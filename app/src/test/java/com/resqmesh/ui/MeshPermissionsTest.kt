package com.resqmesh.ui

import org.junit.Assert.assertNotNull
import org.junit.Test

class MeshPermissionsTest {

    @Test
    fun `permission_arrays_tidak_null`() {
        assertNotNull(MeshPermissions.locationPermissions)
        assertNotNull(MeshPermissions.bluetoothPermissions)
        assertNotNull(MeshPermissions.notificationPermissions)
        assertNotNull(MeshPermissions.servicePermissions)
    }
}
