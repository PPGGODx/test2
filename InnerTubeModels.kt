package com.hybridmusic.player.data.remote.innertube

import com.hybridmusic.player.data.local.entity.SongEntity
import com.hybridmusic.player.data.local.entity.SyncStatus

data class TrackItem(
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
    val isFavorite: Boolean = false
) {
    val isDownloadedLocally: Boolean
        get() = !localAudioPath.isNullOrBlank()

    val isSyncedToCloud: Boolean
        get() = !driveFileId.isNullOrBlank()

    fun toEntity(): SongEntity = SongEntity(
        videoId = videoId,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        thumbnailUrl = thumbnailUrl,
        localAudioPath = localAudioPath,
        localThumbPath = localThumbPath,
        driveFileId = driveFileId,
        syncStatus = syncStatus,
        isFavorite = isFavorite
    )

    companion object {
        fun fromEntity(entity: SongEntity): TrackItem = TrackItem(
            videoId = entity.videoId,
            title = entity.title,
            artist = entity.artist,
            album = entity.album,
            durationMs = entity.durationMs,
            thumbnailUrl = entity.thumbnailUrl,
            localAudioPath = entity.localAudioPath,
            localThumbPath = entity.localThumbPath,
            driveFileId = entity.driveFileId,
            syncStatus = entity.syncStatus,
            isFavorite = entity.isFavorite
        )
    }
}

data class AudioStreamInfo(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long = 0L,
    val userAgent: String = InnerTubeClient.DEFAULT_VR_USER_AGENT,
    val expiresAtMs: Long
)
