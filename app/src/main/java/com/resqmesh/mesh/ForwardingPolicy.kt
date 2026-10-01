package com.resqmesh.mesh

import com.resqmesh.domain.model.NodeId

/** Sumber registry peer GATT, diisi oleh lapisan BLE. */
interface PeerLinkRegistry {
    fun connectedPeers(): Set<NodeId>
    fun hasLinkTo(nodeId: NodeId): Boolean = nodeId in connectedPeers()
}

object NoOpPeerLinkRegistry : PeerLinkRegistry {
    override fun connectedPeers(): Set<NodeId> = emptySet()
}

enum class Route {
    TIER1_ADVERTISING,
    TIER2_GATT,
}

class ForwardingPolicy(
    private val peerLinks: PeerLinkRegistry,
) {
    fun decide(destination: NodeId): Route =
        if (destination.isBroadcast || !peerLinks.hasLinkTo(destination)) {
            Route.TIER1_ADVERTISING
        } else {
            Route.TIER2_GATT
        }
}
