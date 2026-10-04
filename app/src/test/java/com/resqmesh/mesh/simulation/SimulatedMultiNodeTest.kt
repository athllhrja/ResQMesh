package com.resqmesh.mesh.simulation

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.domain.model.LatLon
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.MessageStatus
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedMultiNodeTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout(90, TimeUnit.SECONDS)

    private val idA = NodeId(0x111111)
    private val idB = NodeId(0x222222)
    private val idC = NodeId(0x333333)
    private val idD = NodeId(0x444444)
    private val idE = NodeId(0x555555)

    private val lineNodes = listOf(idA, idB, idC, idD, idE)

    private val clock = object : TimeProvider {
        var time = 10_000L
        override fun now(): Long = time
    }

    private val fix = LocationFix(
        point = LatLon(latitude = -6.17539, longitude = 106.82715),
        accuracyMeters = 10,
        ageSeconds = 2,
        batteryPct = 85,
    )

    private fun testLineTopologyWithLoss(lossRate: Double) = runTest {
        val airwave = SimulatedAirwave(
            scope = backgroundScope,
            clock = clock,
            lossRate = lossRate,
            random = Random(42),
        )
        airwave.setLineTopology(lineNodes)

        val simA = airwave.createNode(idA, isResponder = false)
        val simB = airwave.createNode(idB, isResponder = false)
        val simC = airwave.createNode(idC, isResponder = false)
        val simD = airwave.createNode(idD, isResponder = false)
        val simE = airwave.createNode(idE, isResponder = true)

        listOf(simA, simB, simC, simD, simE).forEach { it.powerOn() }

        // Node A memicu SOS
        val sosId = simA.manager.submitSos(
            kind = SosKind.TRAPPED,
            fix = fix,
            victimCount = 2,
            hazards = 0,
            text = "Butuh bantuan di lokasi",
            ttl = 10,
        )

        // Majukan jam virtual sampai seluruh propagasi multi-hop selesai
        advanceTimeBy(20_000L)

        // Assert 1: SOS sampai ke Responder E dengan isi payload & koordinat utuh
        val receivedE = simE.messageDao.findByKey(sosId.value)
        assertNotNull("SOS harus sampai ke Responder E (lossRate=$lossRate)", receivedE)
        assertTrue("Payload bytes Responder E tidak boleh kosong", receivedE!!.payloadBytes.isNotEmpty())
        assertEquals("LatE4 Responder E harus cocok", (fix.point.latitude * MeshConfig.COORD_SCALE).toInt(), receivedE.latE4)
        assertEquals("LonE4 Responder E harus cocok", (fix.point.longitude * MeshConfig.COORD_SCALE).toInt(), receivedE.lonE4)

        // Assert 2: Hop count pada Responder E = 3 (jarak relay dari A)
        assertEquals("Hop count pada Responder E harus 3", 3, receivedE.hopCount)

        // Assert 3: ACK kembali ke Origin A
        val updatedA = simA.messageDao.findByKey(sosId.value)
        assertEquals("ACK harus kembali ke Origin A", MessageStatus.ACKED.wire, updatedA?.status)

        // Assert 4: Tidak terjadi badai paket (jumlah total pancaran terbatas)
        val totalBroadcasts = airwave.totalBroadcastCount.get()
        assertTrue(
            "Total pancaran harus terbatas (<250) tanpa badai paket, terkumpul: $totalBroadcasts",
            totalBroadcasts < 250,
        )
    }

    private fun test50SeedsForLossRate(lossRate: Double, minSosPct: Double, minAckPct: Double) = runTest {
        var sosSampaiUtuh = 0
        var sosKosongDiterima = 0
        var ackKembali = 0
        var ackPalsu = 0
        var totalBroadcastSum = 0L

        val expectedLatE4 = (fix.point.latitude * MeshConfig.COORD_SCALE).toInt()
        val expectedLonE4 = (fix.point.longitude * MeshConfig.COORD_SCALE).toInt()

        val totalSeeds = 50
        for (seed in 1..totalSeeds) {
            val airwave = SimulatedAirwave(
                scope = backgroundScope,
                clock = clock,
                lossRate = lossRate,
                random = Random(seed),
            )
            airwave.setLineTopology(lineNodes)

            val simA = airwave.createNode(idA, isResponder = false)
            val simB = airwave.createNode(idB, isResponder = false)
            val simC = airwave.createNode(idC, isResponder = false)
            val simD = airwave.createNode(idD, isResponder = false)
            val simE = airwave.createNode(idE, isResponder = true)

            listOf(simA, simB, simC, simD, simE).forEach { it.powerOn() }

            val sosId = simA.manager.submitSos(
                kind = SosKind.MEDICAL,
                fix = fix,
                victimCount = 1,
                hazards = 0,
                text = "Uji Seed #$seed",
                ttl = 10,
            )

            advanceTimeBy(20_000L)

            val receivedE = simE.messageDao.findByKey(sosId.value)
            val updatedA = simA.messageDao.findByKey(sosId.value)

            val isUtuh = receivedE != null &&
                !receivedE.isReassembly &&
                receivedE.payloadBytes.isNotEmpty() &&
                receivedE.latE4 == expectedLatE4 &&
                receivedE.lonE4 == expectedLonE4

            val isKosong = receivedE != null && (receivedE.payloadBytes.isEmpty() || receivedE.isReassembly)

            val isAckedA = updatedA?.status == MessageStatus.ACKED.wire

            if (isUtuh) {
                sosSampaiUtuh++
                assertEquals("Hop count pada Responder E harus tepat 3", 3, receivedE.hopCount)
            }
            if (isKosong) {
                sosKosongDiterima++
            }
            if (isAckedA) {
                ackKembali++
            }
            if (isAckedA && !isUtuh) {
                ackPalsu++
            }

            totalBroadcastSum += airwave.totalBroadcastCount.get()
        }

        val sosPct = (sosSampaiUtuh.toDouble() / totalSeeds) * 100.0
        val ackPct = (ackKembali.toDouble() / totalSeeds) * 100.0
        val avgBroadcasts = totalBroadcastSum.toDouble() / totalSeeds

        println("\n=== HASIL SIMULASI 50 SEED (Loss Rate: ${(lossRate * 100).toInt()}%) ===")
        println("  - sosSampaiUtuh          : ${"%.1f".format(sosPct)}% ($sosSampaiUtuh/$totalSeeds)")
        println("  - sosKosongDiterima      : $sosKosongDiterima (harus 0)")
        println("  - ackKembali             : ${"%.1f".format(ackPct)}% ($ackKembali/$totalSeeds)")
        println("  - ackPalsu               : $ackPalsu (harus 0)")
        println("  - Rata-rata Total Broadcasts: ${"%.1f".format(avgBroadcasts)}")

        assertEquals("sosKosongDiterima harus 0", 0, sosKosongDiterima)
        assertEquals("ackPalsu harus 0", 0, ackPalsu)
        assertTrue("sosSampaiUtuh (${"%.1f".format(sosPct)}%) harus >= $minSosPct%", sosPct >= minSosPct)
        assertTrue("ackKembali (${"%.1f".format(ackPct)}%) harus >= $minAckPct%", ackPct >= minAckPct)
        assertTrue("Rata-rata total broadcast ($avgBroadcasts) harus < 300", avgBroadcasts < 300.0)
    }

    @Test
    fun `skenario_topologi_garis_A_B_C_D_E_loss_0_persen`() = testLineTopologyWithLoss(0.0)

    @Test
    fun `skenario_topologi_garis_A_B_C_D_E_loss_20_persen`() = testLineTopologyWithLoss(0.20)

    @Test
    fun `skenario_topologi_garis_A_B_C_D_E_loss_40_persen`() = testLineTopologyWithLoss(0.40)

    @Test
    fun `simulasi_50_seed_acak_loss_20_persen`() = test50SeedsForLossRate(0.20, minSosPct = 80.0, minAckPct = 70.0)

    @Test
    fun `simulasi_50_seed_acak_loss_40_persen`() = test50SeedsForLossRate(0.40, minSosPct = 60.0, minAckPct = 35.0)

    @Test
    fun `skenario_node_relay_mati_lalu_hidup_lagi_membuktikan_carry`() = runTest {
        val airwave = SimulatedAirwave(
            scope = backgroundScope,
            clock = clock,
            lossRate = 0.0,
            random = Random(42),
        )
        airwave.setLineTopology(lineNodes)

        val simA = airwave.createNode(idA, isResponder = false)
        val simB = airwave.createNode(idB, isResponder = false)
        val simC = airwave.createNode(idC, isResponder = false)
        val simD = airwave.createNode(idD, isResponder = false)
        val simE = airwave.createNode(idE, isResponder = true)

        // Relay C mati di awal
        listOf(simA, simB, simD, simE).forEach { it.powerOn() }

        // Node A trigger SOS
        val sosId = simA.manager.submitSos(
            kind = SosKind.MEDICAL,
            fix = fix,
            victimCount = 1,
            hazards = 0,
            text = "Relay terputus",
            ttl = 10,
        )

        advanceTimeBy(10_000L)

        // Relay B menerima SOS dan menyimpannya sebagai CARRYING / AWAITING
        val storedB = simB.messageDao.findByKey(sosId.value)
        assertNotNull("Relay B harus menyimpan SOS", storedB)

        // Responder E belum menerima SOS karena Relay C mati
        val notYetE = simE.messageDao.findByKey(sosId.value)
        assertEquals(null, notYetE)

        // Relay C dihidupkan kembali (menyala dan mengirim beacon)
        simC.powerOn()
        simB.storeAndForward.onNewPeerDiscovered()
        advanceTimeBy(20_000L)

        // Setelah C menyala dan dipicu store-and-forward, SOS diteruskan B -> C -> D -> E
        val receivedE = simE.messageDao.findByKey(sosId.value)
        assertNotNull("SOS harus sampai ke E setelah Relay C dihidupkan", receivedE)
        assertTrue("Payload bytes Responder E tidak boleh kosong", receivedE!!.payloadBytes.isNotEmpty())

        // ACK kembali ke Origin A
        val updatedA = simA.messageDao.findByKey(sosId.value)
        assertEquals("ACK harus kembali ke Origin A setelah recovery", MessageStatus.ACKED.wire, updatedA?.status)
    }

    @Test
    fun `skenario_responder_yang_datang_belakangan`() = runTest {
        val airwave = SimulatedAirwave(
            scope = backgroundScope,
            clock = clock,
            lossRate = 0.0,
            random = Random(42),
        )
        airwave.setLineTopology(lineNodes)

        val simA = airwave.createNode(idA, isResponder = false)
        val simB = airwave.createNode(idB, isResponder = false)
        val simC = airwave.createNode(idC, isResponder = false)
        val simD = airwave.createNode(idD, isResponder = false)
        val simE = airwave.createNode(idE, isResponder = true)

        // Responder E belum menyala
        listOf(simA, simB, simC, simD).forEach { it.powerOn() }

        val sosId = simA.manager.submitSos(
            kind = SosKind.FIRE,
            fix = fix,
            victimCount = 3,
            hazards = 0,
            text = "Kebakaran di lokasi",
            ttl = 10,
        )

        advanceTimeBy(10_000L)

        // Node D membawa SOS
        val carriedD = simD.messageDao.findByKey(sosId.value)
        assertNotNull("Node D harus menyimpan SOS carried", carriedD)

        // Responder E baru muncul belakangan
        simE.powerOn()
        simD.storeAndForward.onNewPeerDiscovered()
        advanceTimeBy(20_000L)

        // Node D mendeteksi Responder E dari beacon dan meneruskan SOS carried ke E
        val receivedE = simE.messageDao.findByKey(sosId.value)
        assertNotNull("Responder E yang datang belakangan harus menerima SOS", receivedE)
        assertTrue("Payload bytes Responder E tidak boleh kosong", receivedE!!.payloadBytes.isNotEmpty())

        // Responder E menghasilkan ACK dan kembali ke A
        val updatedA = simA.messageDao.findByKey(sosId.value)
        assertEquals("ACK harus kembali ke Origin A", MessageStatus.ACKED.wire, updatedA?.status)
    }
}
