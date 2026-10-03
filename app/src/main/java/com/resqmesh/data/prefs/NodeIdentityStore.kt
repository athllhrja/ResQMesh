package com.resqmesh.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.NodeId
import kotlin.random.Random

class NodeIdentityStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedNodeId: NodeId? = null

    /** Getter NodeId domain object */
    val nodeId: NodeId
        get() = cachedNodeId ?: synchronized(this) {
            cachedNodeId ?: loadOrGenerateNodeId().also { cachedNodeId = it }
        }

    /** Format Raw Hex 6 karakter, contoh: "A83F2C" */
    val rawHex: String get() = nodeId.hex

    /** Format String berawalan NODE-, contoh: "NODE-A83F2C" */
    val formattedString: String get() = nodeId.toString()

    /** Representation 3-byte array untuk transport BLE / byte codec */
    val bytes: ByteArray get() = nodeId.toByteArray()

    val nextSeq: Int
        get() = prefs.getInt(KEY_SEQ, 0)

    fun nextMessageId(): MessageId {
        synchronized(this) {
            val seq = nextSeq
            prefs.edit().putInt(KEY_SEQ, (seq + 1) and 0xFFFFFF).apply()
            return MessageId.of(nodeId, seq)
        }
    }

    fun regenerate(): NodeId {
        synchronized(this) {
            prefs.edit()
                .remove(KEY_NODE_ID)
                .putInt(KEY_SEQ, 0)
                .apply()
            val newId = generateAndPersist()
            cachedNodeId = newId
            return newId
        }
    }

    private fun loadOrGenerateNodeId(): NodeId {
        val savedHex = prefs.getString(KEY_NODE_ID, null)
        return if (savedHex != null) {
            NodeId.fromHex(savedHex)
        } else {
            generateAndPersist()
        }
    }

    private fun generateAndPersist(): NodeId {
        val generated = NodeId.random(Random.Default)
        prefs.edit()
            .putString(KEY_NODE_ID, generated.toString())
            .putInt(KEY_SEQ, 0)
            .apply()
        return generated
    }

    companion object {
        const val FILE_NAME = "resqmesh_identity"
        const val KEY_NODE_ID = "node_id"
        const val KEY_SEQ = "message_seq"
    }
}
