package com.resqmesh.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DatabaseMigrationTest {

    @Test
    fun verifikasi_migrasi_1_2_definisi_valid() {
        val migration = ResQMeshDatabase.MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
        assertNotNull(migration)
    }
}
