package com.hybridmusic.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hybridmusic.player.data.local.dao.MusicDao
import com.hybridmusic.player.data.local.entity.PlaylistEntity
import com.hybridmusic.player.data.local.entity.PlaylistSongCrossRef
import com.hybridmusic.player.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hybrid_music_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
