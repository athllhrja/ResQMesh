package com.resqmesh.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `MessageEntity` ADD COLUMN `incidentKey` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `MessageEntity` ADD COLUMN `sosKind` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `MessageEntity` ADD COLUMN `latE4` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `MessageEntity` ADD COLUMN `lonE4` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `MessageEntity` ADD COLUMN `accuracyM` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `MessageEntity` ADD COLUMN `fixAgeSec` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `MessageEntity` ADD COLUMN `originBatteryPct` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `MessageEntity` ADD COLUMN `victimCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `MessageEntity` ADD COLUMN `hazards` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `MessageEntity` ADD COLUMN `isSosLoc` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `SeenMessageEntity` ADD COLUMN `isSos` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_MessageEntity_incidentKey` ON `MessageEntity` (`incidentKey`)")
            }
        }

        fun build(context: Context): ResQMeshDatabase =
            Room.databaseBuilder(context, ResQMeshDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
