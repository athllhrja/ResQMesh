package com.resqmesh.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.resqmesh.domain.model.MessageId
import com.resqmesh.domain.model.NodeId
import kotlin.random.Random

class NodeIdentityStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val nodeId: NodeId
        get() = NodeId.fromHex(
            prefs.getString(KEY_NODE_ID, null) ?: generateAndPersist().toString(),
        )

    val nextSeq: Int
        get() = prefs.getInt(KEY_SEQ, 0)

    fun nextMessageId(): MessageId {
        val seq = nextSeq
        prefs.edit().putInt(KEY_SEQ, (seq + 1) and 0xFFFFFF).apply()
        return MessageId.of(nodeId, seq)
    }

    fun regenerate(): NodeId {
        prefs.edit()
            .remove(KEY_NODE_ID)
            .putInt(KEY_SEQ, 0)
            .apply()
        return generateAndPersist()
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
