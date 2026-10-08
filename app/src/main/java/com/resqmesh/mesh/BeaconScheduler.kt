package com.resqmesh.mesh

import android.util.Log
import com.resqmesh.core.MeshConfig
import com.resqmesh.data.codec.FrameCodec
import com.resqmesh.domain.model.MeshFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

fun interface FramePublisher {
    fun publish(payload: ByteArray)
}

/**
 * Duty cycle TX. Satu frame per slot, diselingi jeda acak supaya Advertising
 * antar node tidak selalu bentrok pada slot yang sama.
 *
 * Frame masuk melalui [MeshFrame], lalu di-encode menjadi 27 byte wire payload
 * tepat sebelum dikirim. Encoder yang gagal berarti frame di-drop, bukan
 * diteruskan mentah-mentah ke transport.
 */
class BeaconScheduler(
    private val scope: CoroutineScope,
    private val codec: FrameCodec,
    private val publisher: FramePublisher,
    private val random: Random = Random.Default,
    private val selfId: com.resqmesh.domain.model.NodeId? = null,
    private val logger: com.resqmesh.experiment.ExperimentLogger? = null,
) {
    private val queue = RelayQueue()
    private var job: Job? = null
    @Volatile private var beaconIntervalMs = MeshConfig.BEACON_INTERVAL_MS
    private var lastBeaconSentMs = 0L

    val queueSize: Int get() = queue.size()

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                try {
                    val frame = queue.poll()
                    if (frame == null) {
                        val now = System.currentTimeMillis()
                        if (selfId != null && (now - lastBeaconSentMs >= beaconIntervalMs)) {
                            val beaconFrame = com.resqmesh.domain.model.BeaconFrame(
                                nodeId = selfId,
                                statusFlags = com.resqmesh.domain.model.NodeStatusFlags.MESH_ACTIVE or com.resqmesh.domain.model.NodeStatusFlags.SCANNING,
                                batteryPct = -1,
                                pendingCount = 0,
                                defaultTtl = MeshConfig.DEFAULT_TTL,
                                gattPeerCount = 0,
                                nodeSeq = now / 1000L,
                            )
                            val wire = runCatching { codec.encodeBeacon(beaconFrame) }.getOrNull()
                            if (wire != null) {
                                publisher.publish(wire)
                                lastBeaconSentMs = now
                            }
                        }
                        delay(jitter(MeshConfig.SCHEDULER_JITTER_MS))
                        continue
                    }
                    val wire = runCatching { codec.encodeMessage(frame) }.getOrNull() ?: continue
                    val interval = intervalFor(frame)
                    val repeats = if (frame.isSos) {
                        MeshConfig.REPEAT_COUNT_URGENT
                    } else {
                        MeshConfig.REPEAT_COUNT_NORMAL
                    }
                    repeat(repeats) {
                        publisher.publish(wire)
                        if (selfId != null) {
                            logger?.logEvent(
                                nodeId = selfId,
                                event = if (frame.isAck) com.resqmesh.experiment.ExperimentEvent.ACK_TX else com.resqmesh.experiment.ExperimentEvent.TX,
                                messageKey = frame.messageId.value,
                                fragIndex = frame.fragIndex,
                                hop = frame.hopCount,
                                ttl = frame.ttl,
                            )
                        }
                        delay(jitter(interval))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "Gagal pada iterasi loop BeaconScheduler: ${e.message}", e)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        queue.clear()
    }

    fun setSosActive(active: Boolean) {
        beaconIntervalMs = if (active) {
            MeshConfig.BEACON_INTERVAL_SOS_MS
        } else {
            MeshConfig.BEACON_INTERVAL_MS
        }
    }

    fun enqueueUrgent(frame: MeshFrame) = enqueue(frame, urgent = true)

    fun enqueueNormal(frame: MeshFrame) = enqueue(frame, urgent = false)

    fun enqueueFrames(frames: List<MeshFrame>, urgent: Boolean = false) {
        frames.forEach { enqueue(it, urgent) }
    }

    fun nextBeaconDelayMs(): Long = jitter(beaconIntervalMs)

    private fun enqueue(frame: MeshFrame, urgent: Boolean) {
        if (urgent) queue.pushUrgent(frame) else queue.pushNormal(frame)
    }

    private fun intervalFor(frame: MeshFrame): Long = when {
        frame.isSos -> MeshConfig.BEACON_INTERVAL_SOS_MS
        frame.isAck -> MeshConfig.ACK_INTERVAL_MS
        else -> MeshConfig.RELAY_INTERVAL_MS
    }

    private fun jitter(base: Long): Long =
        base + random.nextLong(0, MeshConfig.SCHEDULER_JITTER_MS)

    companion object {
        private const val TAG = "BeaconScheduler"
    }
}
