package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.core.TimeProvider
import com.resqmesh.data.db.message.MessageDao
import com.resqmesh.data.db.seen.SeenMessageDao
import com.resqmesh.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Manajer Store-and-Forward dan Carrying SOS (Requirement F12 & Tugas T2):
 * - Menyimpan paket SOS origin dan relay ke buffer sementara (Room DB status PENDING_FORWARD / CARRYING).
 * - Memicu re-forward otomatis saat peer baru didiscover oleh BLE scanner.
 * - Origin: re-advertise SOS miliknya secara periodik dengan backoff eksponensial sampai ACK / dibatalkan.
 * - Relay: simpan SOS dengan status CARRYING dan pancarkan ulang secara periodik / saat tetangga baru ditemukan.
 * - Menerapkan Trickle suppression (jitter delay + suppression k-duplicate) untuk mencegah badai paket.
 */
class StoreAndForwardQueue(
    private val messageDao: MessageDao,
    private val seenDao: SeenMessageDao,
    private val meshManager: MeshManager,
    private val clock: TimeProvider,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val random: Random = Random.Default,
) {
    private var loopJob: Job? = null
    private val lastSentMap = ConcurrentHashMap<Long, Long>()
    private val intervalMap = ConcurrentHashMap<Long, Long>()
    private val carryCountMap = ConcurrentHashMap<Long, Int>()

    fun start() {
        if (loopJob != null) return
        loopJob = scope.launch {
            while (isActive) {
                runCatching {
                    flushPendingOutbox()
                    processOriginSos()
                    processCarriedSos()
                }
                delay(MeshConfig.SOS_ORIGIN_RETRANSMIT_INITIAL_MS)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        lastSentMap.clear()
        intervalMap.clear()
        carryCountMap.clear()
    }

    fun onNewPeerDiscovered() {
        scope.launch {
            flushPendingOutbox()
            processCarriedSos()
        }
    }

    suspend fun cancelSelfSos(): Int {
        val count = meshManager.cancelSelfSos()
        lastSentMap.clear()
        intervalMap.clear()
        return count
    }

    suspend fun flushPendingOutbox(): Int {
        val pending = messageDao.pendingForwards(limit = 16)
        var replayed = 0

        for (message in pending) {
            if (message.ttl <= 0) {
                messageDao.markExpired(message.messageKey)
                continue
            }

            val entry = seenDao.find(message.messageKey)
            val limit = if (message.isSos) {
                MeshConfig.SOS_FLOOD_FORWARD_LIMIT
            } else {
                MeshConfig.NORMAL_FORWARD_LIMIT
            }

            if (entry != null && entry.forwardCount >= limit) {
                messageDao.markExpired(message.messageKey)
                continue
            }

            meshManager.replay(message)
            replayed++
        }

        return replayed
    }

    suspend fun processOriginSos() {
        val cutoff = clock.now() - MeshConfig.SOS_ORIGIN_RETRANSMIT_WINDOW_MS
        val activeSos = messageDao.activeSelfSosMessages(meshManager.selfId.value, cutoff)
        val now = clock.now()

        for (message in activeSos) {
            val lastSent = lastSentMap.getOrDefault(message.messageKey, 0L)
            val interval = intervalMap.getOrDefault(
                message.messageKey,
                MeshConfig.SOS_ORIGIN_RETRANSMIT_INITIAL_MS,
            )

            if (now - lastSent >= interval) {
                meshManager.reAdvertiseSelfSos(message)
                lastSentMap[message.messageKey] = now

                val nextInterval = (interval * 1.5).toLong()
                    .coerceAtMost(MeshConfig.SOS_ORIGIN_RETRANSMIT_MAX_MS)
                intervalMap[message.messageKey] = nextInterval
            }
        }
    }

    suspend fun processCarriedSos() {
        val cutoff = clock.now() - MeshConfig.SOS_CARRY_RETENTION_MS
        val carried = messageDao.activeCarriedSosMessages(meshManager.selfId.value, cutoff)

        for (message in carried) {
            if (message.ttl <= 0) {
                messageDao.markExpired(message.messageKey)
                continue
            }

            val beforeCount = seenDao.find(message.messageKey)?.forwardCount ?: 0

            val jitter = random.nextLong(
                MeshConfig.SOS_TRICKLE_JITTER_MIN_MS,
                MeshConfig.SOS_TRICKLE_JITTER_MAX_MS + 1,
            )
            delay(jitter)

            val afterCount = seenDao.find(message.messageKey)?.forwardCount ?: 0
            val newDuplicatesHeard = afterCount - beforeCount

            if (newDuplicatesHeard >= MeshConfig.SOS_TRICKLE_K) {
                continue
            }

            meshManager.replayRelay(message)
            val currentCarry = (carryCountMap[message.messageKey] ?: 0) + 1
            carryCountMap[message.messageKey] = currentCarry

            if (currentCarry >= MeshConfig.SOS_MAX_CARRY_REPEATS) {
                messageDao.markForwarded(
                    key = message.messageKey,
                    status = MessageStatus.IN_TRANSIT.wire,
                    ttl = message.ttl,
                    hopCount = message.hopCount,
                    now = clock.now(),
                )
            }
        }
    }
}
