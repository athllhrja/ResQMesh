package com.resqmesh.mesh

import com.resqmesh.core.TimeProvider
import com.resqmesh.data.codec.FrameCodec
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.MeshFrame
import com.resqmesh.domain.model.MeshState
import com.resqmesh.domain.model.NodeId
import com.resqmesh.mesh.fake.InMemoryMessageDao
import com.resqmesh.mesh.fake.InMemoryMessageHopDao
import com.resqmesh.mesh.fake.InMemoryNodeDao
import com.resqmesh.mesh.fake.InMemorySeenFrameDao
import com.resqmesh.mesh.fake.InMemorySeenMessageDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeshManagerTest {

    private val clock = object : TimeProvider {
        override fun now(): Long = 1_000_000L
    }
    private val selfId = NodeId(0x123456)
    private val nodeDao = InMemoryNodeDao()
    private val messageDao = InMemoryMessageDao()
    private val hopDao = InMemoryMessageHopDao()
    private val codec = FrameCodec()

    @Test
    fun `start_idempotent_panggilan_ganda_tidak_membuat_transport_start_ganda`() = runTest {
        val transport = FakeMeshTransport()
        val scheduler = BeaconScheduler(
            scope = backgroundScope,
            codec = codec,
            publisher = { transport.advertise(it) },
        )
        val relayEngine = RelayEngine(
            selfId = selfId,
            duplicateGuard = DuplicateGuard(
                seenDao = InMemorySeenMessageDao(),
                seenFrameDao = InMemorySeenFrameDao(),
                clock = clock,
            ),
            ttlPolicy = TtlPolicy(),
            assembler = FragmentAssembler(clock),
            ackTracker = AckTracker(selfId, messageDao, hopDao, clock),
            messageDao = messageDao,
            hopDao = hopDao,
            nodeDao = nodeDao,
            clock = clock,
        )

        val peerLinks = object : PeerLinkRegistry {
            override fun connectedPeers(): Set<NodeId> = emptySet()
        }

        val manager = MeshManager(
            scope = backgroundScope,
            selfId = selfId,
            nodeDao = nodeDao,
            messageDao = messageDao,
            relayEngine = relayEngine,
            codec = codec,
            identity = MessageIdFactory { MessageId.of(selfId, 1) },
            clock = clock,
            transport = transport,
            scheduler = scheduler,
            forwardingPolicy = ForwardingPolicy(peerLinks),
        )

        manager.start()
        assertEquals(1, transport.startCount)
        assertTrue(manager.state.value.isRunning)

        // Panggilan kedua saat mesh sedang berjalan
        manager.start()
        assertEquals(1, transport.startCount)
        assertTrue(manager.state.value.isRunning)

        manager.stop()
        assertEquals(1, transport.stopCount)
        assertEquals(MeshState.Phase.STOPPED, manager.state.value.phase)
        assertFalse(manager.state.value.isRunning)
    }

    private class FakeMeshTransport : MeshTransport {
        var startCount = 0
        var stopCount = 0

        override fun start(
            onFrame: (MeshFrame, Int) -> Unit,
            onBeacon: (ByteArray, Int) -> Unit,
        ) {
            startCount++
        }

        override fun stop() {
            stopCount++
        }

        override fun advertise(payload: ByteArray) {}
        override fun sendOverLink(peerId: NodeId, frames: List<MeshFrame>) {}
    }
}
