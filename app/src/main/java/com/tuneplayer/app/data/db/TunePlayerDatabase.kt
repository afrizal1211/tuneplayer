package com.tuneplayer.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongMap::class,
        PlayCountEntity::class,
        SearchHistoryEntity::class,
        EventEntity::class,
        FormatEntity::class,
        LyricsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TunePlayerDatabase : androidx.room.RoomDatabase() {
    abstract fun dao(): DatabaseDao

    companion object {
        @Volatile
        private var instance: TunePlayerDatabase? = null

        fun get(context: android.content.Context): TunePlayerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TunePlayerDatabase::class.java,
                    "tuneplayer.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }

        /**
         * v2: index event.timestamp for the recents query and cap the log.
         * Room auto-creates the new indices during migration; the prune
         * keeps old installs from carrying an ever-growing event table.
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_timestamp ON event(timestamp)")
                // Keep the newest 5,000 events; the stats screens read at most 100.
                db.execSQL(
                    "DELETE FROM event WHERE id NOT IN " +
                        "(SELECT id FROM event ORDER BY timestamp DESC LIMIT 5000)",
                )
            }
        }
    }
}
