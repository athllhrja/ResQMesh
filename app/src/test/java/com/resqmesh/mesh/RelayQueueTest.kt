package com.resqmesh.mesh

import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MsgFlag
import com.resqmesh.domain.model.NodeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayQueueTest {

    private fun frame(seq: Int, fragIndex: Int = 0): MeshFrame = MeshFrame(
        messageId = MessageId.of(NodeId(0xAABBCC), seq),
        destination = NodeId.BROADCAST_ID,
        ttl = 10,
        hopCount = 0,
        flags = MsgFlag.SOS,
        fragIndex = fragIndex,
        fragCount = 2,
        totalPayloadLen = 16,
        payloadChunk = ByteArray(9),
    )

    @Test
    fun `kedua fragmen sos_loc yang sama-sama keluar`() {
        val queue = RelayQueue()

        queue.pushUrgent(frame(seq = 1, fragIndex = 0))
        queue.pushUrgent(frame(seq = 1, fragIndex = 1))

        assertEquals(2, queue.size())
        assertEquals(0, queue.poll()?.fragIndex)
        assertEquals(1, queue.poll()?.fragIndex)
    }

    @Test
    fun `fragmen identik tidak di advertise dua kali`() {
        val queue = RelayQueue()

        queue.pushUrgent(frame(seq = 1, fragIndex = 0))
        queue.pushUrgent(frame(seq = 1, fragIndex = 0))

        assertEquals(1, queue.size())
    }

    @Test
    fun `fragmen yang sama boleh masuk lagi setelah keluar`() {
        val queue = RelayQueue()

        queue.pushUrgent(frame(seq = 1, fragIndex = 0))
        queue.poll()
        queue.pushUrgent(frame(seq = 1, fragIndex = 0))

        assertEquals(1, queue.size())
    }

    @Test
    fun `dua insiden berbeda tidak saling menimpa`() {
        val queue = RelayQueue()

        queue.pushUrgent(frame(seq = 1, fragIndex = 0))
        queue.pushUrgent(frame(seq = 2, fragIndex = 0))

        assertEquals(2, queue.size())
    }

    @Test
    fun `urgent lebih dulu daripada normal`() {
        val queue = RelayQueue()

        queue.pushNormal(frame(seq = 9, fragIndex = 0))
        queue.pushUrgent(frame(seq = 1, fragIndex = 0))

        assertEquals(1, queue.poll()?.messageId?.seq)
        assertEquals(9, queue.poll()?.messageId?.seq)
    }

    @Test
    fun `clear mengosongkan kedua jalur`() {
        val queue = RelayQueue()

        queue.pushUrgent(frame(seq = 1, fragIndex = 0))
        queue.pushNormal(frame(seq = 2, fragIndex = 0))
        queue.clear()

        assertEquals(0, queue.size())
        assertNull(queue.poll())
    }
}
