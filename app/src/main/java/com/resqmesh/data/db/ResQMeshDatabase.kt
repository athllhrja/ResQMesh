package com.resqmesh.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.resqmesh.data.db.message.MessageDao
import com.resqmesh.data.db.message.MessageEntity
import com.resqmesh.data.db.message.MessageHopDao
import com.resqmesh.data.db.message.MessageHopEntity
import com.resqmesh.data.db.node.NodeDao
import com.resqmesh.data.db.node.NodeEntity
import com.resqmesh.data.db.seen.SeenFrameDao
import com.resqmesh.data.db.seen.SeenFrameEntity
import com.resqmesh.data.db.seen.SeenMessageDao
import com.resqmesh.data.db.seen.SeenMessageEntity

@Database(
    entities = [
        NodeEntity::class,
        MessageEntity::class,
        MessageHopEntity::class,
        SeenMessageEntity::class,
        SeenFrameEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ResQMeshDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun messageDao(): MessageDao
    abstract fun messageHopDao(): MessageHopDao
    abstract fun seenMessageDao(): SeenMessageDao
    abstract fun seenFrameDao(): SeenFrameDao

    companion object {
        const val NAME = "resqmesh.db"

        fun build(context: Context): ResQMeshDatabase =
            Room.databaseBuilder(context, ResQMeshDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
