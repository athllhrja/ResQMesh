package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.model.MeshFrame
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Android hanya mengizinkan satu advertiser per proses, sehingga semua frame
 * keluar diserialisasi lewat antrean ini.
 *
 * Batas kapasitas dihitung berdasarkan jumlah pesan unik (messageKey). Saat
 * antrean penuh, seluruh fragmen milik pesan tertua dibuang sekaligus agar
 * tidak ada pesan yang terpotong separuh.
 */
class RelayQueue(private val capacity: Int = MeshConfig.MAX_RELAY_QUEUE) {

    private val urgent = ConcurrentLinkedQueue<MeshFrame>()
    private val normal = ConcurrentLinkedQueue<MeshFrame>()

    /** Fragmen yang masih antre, supaya frame identik tidak di-advertise ulang. */
    private val urgentSlots = ConcurrentHashMapSlotSet()

    fun pushUrgent(frame: MeshFrame) {
        if (frame.isAck) {
            pushNormal(frame)
            return
        }
        val slot = MeshSlot(frame.messageId.value, frame.fragIndex)
        if (!urgentSlots.add(slot)) return

        val messageKey = frame.messageId.value
        val currentMessageKeys = urgent.map { it.messageId.value }.distinct()

        if (currentMessageKeys.size >= capacity && messageKey !in currentMessageKeys) {
            val oldestKey = currentMessageKeys.first()
            dropMessageFromUrgent(oldestKey)
        }

        urgent.add(frame)
    }

    fun pushNormal(frame: MeshFrame) {
        val messageKey = frame.messageId.value
        val currentMessageKeys = normal.map { it.messageId.value }.distinct()

        if (currentMessageKeys.size >= capacity && messageKey !in currentMessageKeys) {
            val oldestKey = currentMessageKeys.first()
            normal.removeIf { it.messageId.value == oldestKey }
        }

        normal.add(frame)
    }

    private fun dropMessageFromUrgent(messageKey: Long) {
        urgent.removeIf {
            if (it.messageId.value == messageKey) {
                urgentSlots.remove(MeshSlot(it.messageId.value, it.fragIndex))
                true
            } else {
                false
            }
        }
    }

    fun poll(): MeshFrame? {
        val next = urgent.poll() ?: normal.poll()
        if (next != null) {
            urgentSlots.remove(MeshSlot(next.messageId.value, next.fragIndex))
        }
        return next
    }

    fun size(): Int = urgent.size + normal.size

    fun clear() {
        urgent.clear()
        normal.clear()
        urgentSlots.clear()
    }

    private data class MeshSlot(val messageKey: Long, val fragIndex: Int)

    private class ConcurrentHashMapSlotSet {
        private val slots = java.util.concurrent.ConcurrentHashMap<MeshSlot, Boolean>()

        fun add(slot: MeshSlot): Boolean = slots.putIfAbsent(slot, true) == null

        fun remove(slot: MeshSlot) {
            slots.remove(slot)
        }

        fun clear() {
            slots.clear()
        }
    }
}
