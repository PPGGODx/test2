package com.hybridmusic.player.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val playlistId: String,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "videoId"],
    indices = [Index(value = ["videoId"])]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val videoId: String,
    val position: Int = 0
)

data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "playlistId",
        entityColumn = "videoId",
        associateBy = Junction(PlaylistSongCrossRef::class)
    )
    val songs: List<SongEntity>
)
