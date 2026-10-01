package com.resqmesh.domain.model

data class MeshState(
    val phase: Phase,
    val selfId: NodeId,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val neighborCount: Int = 0,
    val gattPeerCount: Int = 0,
    val pendingForwardCount: Int = 0,
    val isSosActive: Boolean = false,
    val lastError: String? = null,
) {
    enum class Phase { STOPPED, IDLE, SCANNING, ACTIVE, ERROR }

    val isRunning: Boolean get() = phase == Phase.SCANNING || phase == Phase.ACTIVE

    companion object {
        fun stopped(selfId: NodeId) = MeshState(Phase.STOPPED, selfId)
    }
}
