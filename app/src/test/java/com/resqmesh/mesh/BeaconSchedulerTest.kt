package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.data.codec.FrameCodec
import com.resqmesh.domain.model.Fragmenter
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MsgFlag
import com.resqmesh.domain.model.NodeId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class BeaconSchedulerTest {

    private val codec = FrameCodec()

    private fun messageFrame(payload: ByteArray = "PESAN".toByteArray()) = MeshFrame(
        messageId = MessageId.of(NodeId(0xA83F2C), 1),
        destination = NodeId(0xC72D4A),
        ttl = 5,
        hopCount = 0,
        flags = MsgFlag.ACK_REQUESTED,
        fragIndex = 0,
        fragCount = 1,
        totalPayloadLen = payload.size,
        payloadChunk = payload,
    )

    @Test
    fun `frame_yang_dikirim_adalah_wire_penuh_bukan_payload_mentah`() = runTest {
        val sent = mutableListOf<ByteArray>()
        val scheduler = BeaconScheduler(
            scope = backgroundScope,
            codec = codec,
            publisher = { sent += it },
            random = Random(7),
        )

        scheduler.start()
        scheduler.enqueueNormal(messageFrame())
        advanceTimeBy(4_000)

        assertEquals(MeshConfig.REPEAT_COUNT_NORMAL, sent.size)
        sent.forEach { bytes ->
            assertEquals(FrameCodec.MESSAGE_SIZE, bytes.size)
            assertEquals(messageFrame(), codec.decode(bytes))
        }
    }

    @Test
    fun `setiap_fragmen_dan_setiap_repeat_terkirim_sepenuhnya`() = runTest {
        val sent = mutableListOf<ByteArray>()
        val scheduler = BeaconScheduler(
            scope = backgroundScope,
            codec = codec,
            publisher = { sent += it },
            random = Random(11),
        )
        val payload = "BANTUAN DARURAT SEKARANG".toByteArray()
        val fragments = Fragmenter.fragmentsOf(messageFrame(payload), payload)

        scheduler.start()
        scheduler.enqueueNormal(fragments.first())
        scheduler.enqueueNormal(fragments.last())
        advanceTimeBy(4_000)

        val decoded = sent.map { codec.decode(it) }
        assertEquals(
            fragments.first().fragIndex to fragments.last().fragIndex,
            decoded.first().fragIndex to decoded.last().fragIndex,
        )
    }

    @Test
    fun `stop_membuang_antrean_dan_menghentikan_pengiriman`() = runTest {
        val sent = mutableListOf<ByteArray>()
        val scheduler = BeaconScheduler(
            scope = backgroundScope,
            codec = codec,
            publisher = { sent += it },
            random = Random(3),
        )

        scheduler.start()
        scheduler.enqueueNormal(messageFrame())
        scheduler.stop()
        advanceTimeBy(4_000)

        assertEquals(0, sent.size)
        assertEquals(0, scheduler.queueSize)
    }
}
