package com.resqmesh.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtlPolicyTest {

    private val policy = TtlPolicy()

    @Test
    fun `meneruskan_selama_ttl_positif_dan_hop_di_bawah_initial`() {
        assertTrue(policy.shouldForward(ttl = 5, hop = 0, initialTtl = 5))
        assertTrue(policy.shouldForward(ttl = 1, hop = 4, initialTtl = 5))
    }

    @Test
    fun `tidak_meneruskan_ketika_ttl_sudah_nol`() {
        assertFalse(policy.shouldForward(ttl = 0, hop = 2, initialTtl = 5))
    }

    @Test
    fun `tidak_meneruskan_ketika_hop_sudah_mencapai_initial_ttl`() {
        assertFalse(policy.shouldForward(ttl = 3, hop = 5, initialTtl = 5))
    }

    @Test
    fun `nextTtl_turun_satu_dan_tidak_pernah_negatif`() {
        assertEquals(4, policy.nextTtl(5))
        assertEquals(0, policy.nextTtl(1))
        assertEquals(0, policy.nextTtl(0))
    }

    @Test
    fun `nextHop_naik_satu_dan_tidak_membatasi_hop_mentah`() {
        assertEquals(1, policy.nextHop(0))
        assertEquals(6, policy.nextHop(5))
    }
}
