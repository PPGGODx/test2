package com.hybridmusic.player.data.repository

import android.content.Context
import android.net.Uri
import com.hybridmusic.player.data.local.ZongStorageManager
import com.hybridmusic.player.data.local.dao.MusicDao
import com.hybridmusic.player.data.local.entity.PlaylistEntity
import com.hybridmusic.player.data.local.entity.PlaylistSongCrossRef
import com.hybridmusic.player.data.local.entity.PlaylistWithSongs
import com.hybridmusic.player.data.local.entity.SongEntity
import com.hybridmusic.player.data.local.entity.SyncStatus
import com.hybridmusic.player.data.remote.cloud.CloudLibrarySnapshot
import com.hybridmusic.player.data.remote.cloud.GoogleDriveSyncManager
import com.hybridmusic.player.data.remote.innertube.AudioStreamInfo
import com.hybridmusic.player.data.remote.innertube.InnerTubeClient
import com.hybridmusic.player.data.remote.innertube.TrackItem
import com.hybridmusic.player.util.M4aTagWriter
import com.hybridmusic.player.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao,
    private val innerTubeClient: InnerTubeClient,
    private val driveSyncManager: GoogleDriveSyncManager,
    private val zongStorageManager: ZongStorageManager,
    private val networkMonitor: NetworkMonitor,
    private val httpClient: OkHttpClient
) {
    private val audioDir: File by lazy {
        File(context.filesDir, "offline_audio").apply { mkdirs() }
    }
    private val thumbDir: File by lazy {
        File(context.filesDir, "offline_thumbs").apply { mkdirs() }
    }

    val librarySongsFlow: Flow<List<TrackItem>> =
        musicDao.getLibrarySongsFlow().map { list -> list.map { TrackItem.fromEntity(it) } }

    val offlineSongsFlow: Flow<List<TrackItem>> =
        musicDao.getOfflineAvailableSongsFlow().map { list -> list.map { TrackItem.fromEntity(it) } }

    val favoriteSongsFlow: Flow<List<TrackItem>> =
        musicDao.getFavoriteSongsFlow().map { list -> list.map { TrackItem.fromEntity(it) } }

    val playlistsFlow: Flow<List<PlaylistWithSongs>> = musicDao.getPlaylistsWithSongsFlow()

    fun invalidateStreamCache(videoId: String) {
        innerTubeClient.invalidateStreamCache(videoId)
    }

    suspend fun searchTracks(query: String): List<TrackItem> {
        val localSongsMap = musicDao.getAllSongsOnce().associateBy { it.videoId }
        val remoteResults = innerTubeClient.searchSongs(query)
        if (remoteResults.isEmpty()) {
            networkMonitor.refreshStatus()
            return musicDao.searchOfflineSongs(query).map { TrackItem.fromEntity(it) }
        }
        return remoteResults.map { track ->
            val saved = localSongsMap[track.videoId]
            if (saved != null) {
                track.copy(
                    localAudioPath = saved.localAudioPath,
                    localThumbPath = saved.localThumbPath,
                    driveFileId = saved.driveFileId,
                    syncStatus = saved.syncStatus,
                    isFavorite = saved.isFavorite
                )
            } else {
                track
            }
        }
    }

    /**
     * 3-Tier Playback Resolution:
     * Tier 1: Local offline file in Music/Zong folder
     * Tier 2: Connected Google Drive folder backup
     * Tier 3: Live YouTube Music InnerTube multi-client stream
     */
    suspend fun resolvePlayableUri(track: TrackItem, forceRefresh: Boolean = false): Uri? =
        withContext(Dispatchers.IO) {
            val dbSong = musicDao.getSongById(track.videoId)

            // Tier 1: Local offline storage (Music/Zong)
            val localPath = dbSong?.localAudioPath ?: track.localAudioPath
            if (!localPath.isNullOrBlank()) {
                if (localPath.startsWith("content://")) {
                    val uri = Uri.parse(localPath)
                    val exists = runCatching {
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.close()
                        true
                    }.getOrDefault(false)
                    if (exists) return@withContext uri
                } else {
                    val file = File(localPath)
                    if (file.exists() && file.length() > 0L) {
                        return@withContext Uri.fromFile(file)
                    }
                }
            }

            // Tier 2: Connected Google Drive folder restore
            val driveId = dbSong?.driveFileId ?: track.driveFileId
            if (!driveId.isNullOrBlank()) {
                val destFile = File(audioDir, "${track.videoId}.m4a")
                val restored = driveSyncManager.downloadAudioFile(driveId, destFile)
                if (restored) {
                    musicDao.updateLocalDownload(
                        videoId = track.videoId,
                        audioPath = destFile.absolutePath,
                        thumbPath = dbSong?.localThumbPath,
                        status = SyncStatus.SYNCED_CLOUD
                    )
                    return@withContext Uri.fromFile(destFile)
                }
            }

            // Tier 3: Live InnerTube multi-client stream
            val stream = innerTubeClient.getAudioStream(track.videoId, forceRefresh = forceRefresh)
            if (stream != null) {
                return@withContext Uri.parse(stream.url)
            }

            null
        }

    suspend fun downloadTrackDirect(track: TrackItem): Result<String> = withContext(Dispatchers.IO) {
        val existing = musicDao.getSongById(track.videoId)
        val entity = (existing ?: track.toEntity()).copy(
            syncStatus = SyncStatus.DOWNLOADING
        )
        musicDao.upsertSong(entity)

        val ok = performSongDownloadNow(track.videoId)
        if (ok) {
            Result.success("บันทึก \"${track.title}\" ลงโฟลเดอร์ Zong (พร้อมปกและศิลปิน) สำเร็จ")
        } else {
            Result.failure(IllegalStateException("ดาวน์โหลด \"${track.title}\" ไม่สำเร็จ กรุณาลองใหม่อีกครั้ง"))
        }
    }

    suspend fun performSongDownloadNow(videoId: String): Boolean = withContext(Dispatchers.IO) {
        val song = musicDao.getSongById(videoId) ?: return@withContext false
        val stream = innerTubeClient.getAudioStream(videoId, forceRefresh = false)
            ?: innerTubeClient.getAudioStream(videoId, forceRefresh = true)
            ?: run {
                musicDao.updateSyncStatus(videoId, SyncStatus.NONE)
                return@withContext false
            }

        val rawTempAudio = File(audioDir, "${videoId}_raw.m4a")
        val taggedTempAudio = File(audioDir, "${videoId}_tagged.m4a")
        val thumbFile = File(thumbDir, "$videoId.jpg")

        val audioOk = downloadStreamChunked(stream, rawTempAudio)
        if (!audioOk) {
            rawTempAudio.delete()
            musicDao.updateSyncStatus(videoId, SyncStatus.NONE)
            return@withContext false
        }

        // Download album artwork
        var coverBytes: ByteArray? = null
        if (song.thumbnailUrl.isNotBlank() && downloadSimpleBinary(song.thumbnailUrl, thumbFile)) {
            coverBytes = runCatching { thumbFile.readBytes() }.getOrNull()
        }

        // Embed Cover Art + Title + Artist into the .m4a file
        M4aTagWriter.embedMetadata(
            inputFile = rawTempAudio,
            outputFile = taggedTempAudio,
            title = song.title,
            artist = song.artist,
            album = song.album.ifBlank { ZongStorageManager.ZONG_FOLDER_NAME },
            coverBytes = coverBytes
        )
        rawTempAudio.delete()

        // Save the tagged file into device's Music/Zong folder
        val zongSavedPath = zongStorageManager.saveTaggedSongToZongFolder(
            taggedAudioFile = taggedTempAudio,
            title = song.title,
            artist = song.artist,
            album = song.album.ifBlank { ZongStorageManager.ZONG_FOLDER_NAME }
        ) ?: taggedTempAudio.absolutePath

        if (zongSavedPath != taggedTempAudio.absolutePath) {
            taggedTempAudio.delete()
        }

        // Auto-upload to connected Google Drive folder if active
        var driveUri = song.driveFileId
        if (driveSyncManager.connectedFolder.value != null && driveUri == null) {
            val uploadSource = File(audioDir, "${videoId}_drive.m4a")
            runCatching {
                if (zongSavedPath.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(zongSavedPath))?.use { inp ->
                        uploadSource.outputStream().use { out -> inp.copyTo(out) }
                    }
                } else {
                    File(zongSavedPath).copyTo(uploadSource, overwrite = true)
                }
                if (uploadSource.exists()) {
                    driveUri = driveSyncManager.uploadAudioFile(videoId, song.title, uploadSource)
                }
            }
            uploadSource.delete()
        }

        val finalStatus = if (driveUri != null) {
            SyncStatus.SYNCED_CLOUD
        } else {
            SyncStatus.DOWNLOADED_LOCAL
        }

        musicDao.updateLocalDownload(
            videoId = videoId,
            audioPath = zongSavedPath,
            thumbPath = if (thumbFile.exists()) thumbFile.absolutePath else null,
            status = finalStatus
        )
        if (driveUri != null && song.driveFileId == null) {
            musicDao.updateCloudSync(videoId, driveUri, SyncStatus.SYNCED_CLOUD)
        }

        true
    }

    /**
     * Scans device's Music/Zong folder and imports any existing songs (with embedded cover + title + artist)
     * into the local database so they appear in the library automatically.
     */
    suspend fun syncZongFolderWithDatabase(): Int = withContext(Dispatchers.IO) {
        val existingPaths = musicDao.getAllSongsOnce()
            .mapNotNull { it.localAudioPath }
            .toSet()
        val scannedSongs = zongStorageManager.scanExistingZongSongs()
        var importedCount = 0
        for (song in scannedSongs) {
            val path = song.localAudioPath ?: continue
            if (path !in existingPaths) {
                musicDao.upsertSong(song)
                importedCount++
            }
        }
        importedCount
    }

    /**
     * Downloads googlevideo.com streams in 512KB byte-range chunks with matching User-Agent
     * so YouTube CDN never throttles or cuts off mid-download.
     */
    private fun downloadStreamChunked(stream: AudioStreamInfo, destFile: File): Boolean {
        destFile.parentFile?.mkdirs()
        val tempFile = File(destFile.parentFile, "${destFile.name}.part")
        if (tempFile.exists()) tempFile.delete()

        val chunkSize = 512L * 1024L // 512 KB per range request
        var downloadedBytes = 0L
        val totalBytes = stream.contentLength

        return runCatching {
            FileOutputStream(tempFile).use { output ->
                if (totalBytes > 0L) {
                    while (downloadedBytes < totalBytes) {
                        val endByte = minOf(downloadedBytes + chunkSize - 1L, totalBytes - 1L)
                        val req = Request.Builder()
                            .url(stream.url)
                            .header("User-Agent", stream.userAgent)
                            .header("Range", "bytes=$downloadedBytes-$endByte")
                            .get()
                            .build()

                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.code != 200 && resp.code != 206) {
                                return false
                            }
                            val bytes = resp.body?.bytes() ?: return false
                            if (bytes.isEmpty()) return false
                            output.write(bytes)
                            downloadedBytes += bytes.size
                        }
                    }
                } else {
                    val req = Request.Builder()
                        .url(stream.url)
                        .header("User-Agent", stream.userAgent)
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return false
                        val bodyStream = resp.body?.byteStream() ?: return false
                        bodyStream.copyTo(output)
                    }
                }
                output.flush()
            }

            if (tempFile.exists() && tempFile.length() > 1024L) {
                if (destFile.exists()) destFile.delete()
                tempFile.renameTo(destFile)
                true
            } else {
                tempFile.delete()
                false
            }
        }.getOrElse {
            tempFile.delete()
            false
        }
    }

    private fun downloadSimpleBinary(url: String, destFile: File): Boolean {
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val stream = resp.body?.byteStream() ?: return false
                destFile.parentFile?.mkdirs()
                destFile.outputStream().use { out ->
                    stream.copyTo(out)
                }
                destFile.exists() && destFile.length() > 0L
            }
        }.getOrDefault(false)
    }

    suspend fun removeOfflineFile(videoId: String) = withContext(Dispatchers.IO) {
        val song = musicDao.getSongById(videoId) ?: return@withContext
        zongStorageManager.deleteFromZongFolder(song.localAudioPath)
        song.localThumbPath?.let { File(it).delete() }
        if (song.driveFileId != null) {
            musicDao.updateLocalDownload(videoId, null, null, SyncStatus.SYNCED_CLOUD)
        } else {
            musicDao.deleteSongById(videoId)
        }
    }

    suspend fun toggleFavorite(track: TrackItem) {
        val existing = musicDao.getSongById(track.videoId)
        if (existing == null) {
            musicDao.upsertSong(track.toEntity().copy(isFavorite = true))
        } else {
            musicDao.updateFavorite(track.videoId, !existing.isFavorite)
        }
    }

    // Playlists
    suspend fun createPlaylist(name: String): String? {
        if (name.isBlank()) return null
        val id = UUID.randomUUID().toString()
        val playlist = PlaylistEntity(
            playlistId = id,
            name = name.trim()
        )
        musicDao.upsertPlaylist(playlist)
        return id
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        if (newName.isBlank()) return
        musicDao.renamePlaylist(playlistId, newName.trim())
    }

    suspend fun addSongToPlaylist(playlistId: String, track: TrackItem) {
        if (musicDao.getSongById(track.videoId) == null) {
            musicDao.upsertSong(track.toEntity())
        }
        musicDao.upsertCrossRef(
            PlaylistSongCrossRef(
                playlistId = playlistId,
                videoId = track.videoId
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: String, videoId: String) {
        musicDao.removeSongFromPlaylist(playlistId, videoId)
    }

    suspend fun deletePlaylist(playlistId: String) {
        musicDao.deletePlaylist(playlistId)
    }

    // Google Drive Folder & File Sync Operations
    suspend fun importAudioFromGoogleDrive(audioUri: Uri): Result<TrackItem> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = driveSyncManager.resolveDisplayNameFromUri(audioUri)
            val cleanTitle = rawName.substringBeforeLast(".").ifBlank { "Google Drive Audio" }
            val videoId = "gdrive_" + UUID.randomUUID().toString().take(8)
            val destFile = File(audioDir, "$videoId.m4a")

            context.contentResolver.openInputStream(audioUri)?.use { input ->
                destFile.outputStream().use { out ->
                    input.copyTo(out)
                }
            } ?: error("ไม่สามารถอ่านไฟล์เสียงจาก Google Drive ได้")

            val songEntity = SongEntity(
                videoId = videoId,
                title = cleanTitle,
                artist = "Google Drive Audio",
                localAudioPath = destFile.absolutePath,
                driveFileId = audioUri.toString(),
                syncStatus = SyncStatus.SYNCED_CLOUD
            )
            musicDao.upsertSong(songEntity)
            TrackItem.fromEntity(songEntity)
        }
    }

    suspend fun exportBackupFileToDriveUri(targetUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val snapshot = CloudLibrarySnapshot(
            exportedAt = System.currentTimeMillis(),
            songs = musicDao.getAllSongsOnce(),
            playlists = musicDao.getAllPlaylistsOnce(),
            crossRefs = musicDao.getAllCrossRefsOnce()
        )
        val ok = driveSyncManager.exportSnapshotToSingleUri(targetUri, snapshot)
        if (ok) {
            Result.success("บันทึกไฟล์สำรองข้อมูลลง Google Drive เรียบร้อย (${snapshot.songs.size} เพลง)")
        } else {
            Result.failure(IllegalStateException("บันทึกไฟล์ลง Google Drive ไม่สำเร็จ"))
        }
    }

    suspend fun importBackupFileFromDriveUri(sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val snapshot = driveSyncManager.readSnapshotFromUri(sourceUri)
            ?: return@withContext Result.failure(IllegalStateException("ไฟล์สำรองไม่ถูกต้องหรือไม่สามารถอ่านได้"))
        mergeRemoteSnapshot(snapshot)
        Result.success("กู้คืนข้อมูลจาก Google Drive สำเร็จ (${snapshot.songs.size} เพลง, ${snapshot.playlists.size} เพลย์ลิสต์)")
    }

    suspend fun syncWithGoogleDriveNow(): Result<String> = withContext(Dispatchers.IO) {
        if (driveSyncManager.connectedFolder.value == null) {
            return@withContext Result.failure(IllegalStateException("กรุณาเลือกโฟลเดอร์ Google Drive ก่อนเริ่มซิงก์"))
        }

        // 1. Pull remote snapshot from connected Google Drive folder & merge
        val remoteSnapshot = driveSyncManager.downloadMetadataSnapshot()
        if (remoteSnapshot != null) {
            mergeRemoteSnapshot(remoteSnapshot)
        }

        // 2. Upload local audio files that are not yet backed up in the Google Drive folder
        val pendingSongs = musicDao.getPendingCloudUploadSongs()
        var uploadedCount = 0
        for (song in pendingSongs) {
            val path = song.localAudioPath ?: continue
            val uploadSource = File(audioDir, "${song.videoId}_sync.m4a")
            val copied = runCatching {
                if (path.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(path))?.use { inp ->
                        uploadSource.outputStream().use { out -> inp.copyTo(out) }
                    }
                } else {
                    val f = File(path)
                    if (f.exists()) f.copyTo(uploadSource, overwrite = true)
                }
                uploadSource.exists() && uploadSource.length() > 0L
            }.getOrDefault(false)

            if (!copied) {
                uploadSource.delete()
                continue
            }

            musicDao.updateSyncStatus(song.videoId, SyncStatus.UPLOADING)
            val driveUri = driveSyncManager.uploadAudioFile(song.videoId, song.title, uploadSource)
            uploadSource.delete()

            if (driveUri != null) {
                musicDao.updateCloudSync(song.videoId, driveUri, SyncStatus.SYNCED_CLOUD)
                uploadedCount++
            } else {
                musicDao.updateSyncStatus(song.videoId, SyncStatus.DOWNLOADED_LOCAL)
            }
        }

        // 3. Push updated full library & playlists JSON back to Google Drive folder
        val updatedSnapshot = CloudLibrarySnapshot(
            exportedAt = System.currentTimeMillis(),
            songs = musicDao.getAllSongsOnce(),
            playlists = musicDao.getAllPlaylistsOnce(),
            crossRefs = musicDao.getAllCrossRefsOnce()
        )
        val metadataOk = driveSyncManager.uploadMetadataSnapshot(updatedSnapshot)
        if (metadataOk) {
            Result.success("ซิงก์ Google Drive สำเร็จ (อัปโหลดไฟล์ใหม่ $uploadedCount เพลง)")
        } else {
            Result.failure(IllegalStateException("เขียนไฟล์สำรองลงโฟลเดอร์ Google Drive ไม่สำเร็จ"))
        }
    }

    private suspend fun mergeRemoteSnapshot(remoteSnapshot: CloudLibrarySnapshot) {
        val localMap = musicDao.getAllSongsOnce().associateBy { it.videoId }
        val mergedSongs = remoteSnapshot.songs.map { remote ->
            val local = localMap[remote.videoId]
            if (local != null) {
                local.copy(
                    driveFileId = local.driveFileId ?: remote.driveFileId,
                    isFavorite = local.isFavorite || remote.isFavorite,
                    syncStatus = if (local.driveFileId != null || remote.driveFileId != null) {
                        SyncStatus.SYNCED_CLOUD
                    } else {
                        local.syncStatus
                    }
                )
            } else {
                remote.copy(
                    localAudioPath = null,
                    localThumbPath = null,
                    syncStatus = if (remote.driveFileId != null) SyncStatus.SYNCED_CLOUD else SyncStatus.NONE
                )
            }
        }
        musicDao.upsertSongs(mergedSongs)
        remoteSnapshot.playlists.forEach { musicDao.upsertPlaylist(it) }
        remoteSnapshot.crossRefs.forEach { musicDao.upsertCrossRef(it) }
    }
}
