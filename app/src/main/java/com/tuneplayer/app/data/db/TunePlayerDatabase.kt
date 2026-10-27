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
    version = 1,
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
                ).build().also { instance = it }
            }
    }
}
