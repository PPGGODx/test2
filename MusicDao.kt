package com.hybridmusic.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hybridmusic.player.data.local.entity.PlaylistEntity
import com.hybridmusic.player.data.local.entity.PlaylistSongCrossRef
import com.hybridmusic.player.data.local.entity.PlaylistWithSongs
import com.hybridmusic.player.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY addedAt DESC")
    fun getAllSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE localAudioPath IS NOT NULL OR driveFileId IS NOT NULL ORDER BY addedAt DESC")
    fun getLibrarySongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE localAudioPath IS NOT NULL ORDER BY addedAt DESC")
    fun getOfflineAvailableSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun getFavoriteSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE localAudioPath IS NOT NULL AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%')")
    suspend fun searchOfflineSongs(query: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE videoId = :videoId LIMIT 1")
    suspend fun getSongById(videoId: String): SongEntity?

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE localAudioPath IS NOT NULL AND driveFileId IS NULL")
    suspend fun getPendingCloudUploadSongs(): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("UPDATE songs SET localAudioPath = :audioPath, localThumbPath = :thumbPath, syncStatus = :status WHERE videoId = :videoId")
    suspend fun updateLocalDownload(videoId: String, audioPath: String?, thumbPath: String?, status: String)

    @Query("UPDATE songs SET driveFileId = :driveFileId, syncStatus = :status WHERE videoId = :videoId")
    suspend fun updateCloudSync(videoId: String, driveFileId: String?, status: String)

    @Query("UPDATE songs SET syncStatus = :status WHERE videoId = :videoId")
    suspend fun updateSyncStatus(videoId: String, status: String)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE videoId = :videoId")
    suspend fun updateFavorite(videoId: String, isFavorite: Boolean)

    @Query("DELETE FROM songs WHERE videoId = :videoId")
    suspend fun deleteSongById(videoId: String)

    // Playlists
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getPlaylistsWithSongsFlow(): Flow<List<PlaylistWithSongs>>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_song_cross_ref")
    suspend fun getAllCrossRefsOnce(): List<PlaylistSongCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :newName, updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(playlistId: String, newName: String, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCrossRef(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeSongFromPlaylist(playlistId: String, videoId: String)
}
