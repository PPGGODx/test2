package com.hybridmusic.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

object SyncStatus {
    const val NONE = "NONE"
    const val DOWNLOADING = "DOWNLOADING"
    const val DOWNLOADED_LOCAL = "DOWNLOADED_LOCAL"
    const val UPLOADING = "UPLOADING"
    const val SYNCED_CLOUD = "SYNCED_CLOUD"
}

@Serializable
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val thumbnailUrl: String = "",
    val localAudioPath: String? = null,
    val localThumbPath: String? = null,
    val driveFileId: String? = null,
    val syncStatus: String = SyncStatus.NONE,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
