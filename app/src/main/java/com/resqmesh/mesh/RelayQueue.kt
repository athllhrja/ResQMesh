package com.resqmesh.mesh

import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.model.MeshFrame
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Android hanya mengizinkan satu advertiser per proses, sehingga semua frame
 * keluar diserialisasi lewat antrean ini.
 *
 * Latest-wins tetap dipakai untuk tekan-tekan tombol SOS yang sama berulang kali,
 * tetapi dedupe dilakukan per fragmen, bukan per frame. Alasannya SOS_LOC pecah
 * menjadi dua frame (9 + 7 byte) dan keduanya wajib keluar: kalau penyimpanan
 * urgent hanya mengingat satu frame terakhir, fragmen kedua akan menimpa
 * fragmen pertama dan koordinat tidak pernah sampai.
 */
class RelayQueue(private val capacity: Int = MeshConfig.MAX_RELAY_QUEUE) {

    private val urgent = ConcurrentLinkedQueue<MeshFrame>()
    private val normal = ConcurrentLinkedQueue<MeshFrame>()

    /** Fragmen yang masih antre, supaya frame identik tidak di-advertise ulang. */
    private val urgentSlots = ConcurrentHashMapSlotSet()

    fun pushUrgent(frame: MeshFrame) {
        if (frame.isAck) {
            normal.add(frame)
            return
        }
        val slot = MeshSlot(frame.messageId.value, frame.fragIndex)
        if (!urgentSlots.add(slot)) return
        while (urgent.size >= capacity) {
            val dropped = urgent.poll() ?: break
            urgentSlots.remove(MeshSlot(dropped.messageId.value, dropped.fragIndex))
        }
        urgent.add(frame)
    }

    fun pushNormal(frame: MeshFrame) {
        if (normal.size < capacity) normal.add(frame)
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

    /** Wrapper kecil supaya [add] mengembalikan hasil penempatan yang sebenarnya. */
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
