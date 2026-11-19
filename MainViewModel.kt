package com.hybridmusic.player.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hybridmusic.player.data.local.entity.PlaylistWithSongs
import com.hybridmusic.player.data.remote.cloud.DriveFolderConnection
import com.hybridmusic.player.data.remote.cloud.GoogleDriveSyncManager
import com.hybridmusic.player.data.remote.innertube.TrackItem
import com.hybridmusic.player.data.repository.MusicRepository
import com.hybridmusic.player.player.PlaybackUiState
import com.hybridmusic.player.player.PlayerController
import com.hybridmusic.player.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: MusicRepository,
    val playerController: PlayerController,
    private val driveSyncManager: GoogleDriveSyncManager,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val playbackState: StateFlow<PlaybackUiState> = playerController.uiState
    val connectedDriveFolder: StateFlow<DriveFolderConnection?> = driveSyncManager.connectedFolder

    val librarySongs: StateFlow<List<TrackItem>> = repository.librarySongsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineSongs: StateFlow<List<TrackItem>> = repository.offlineSongsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<TrackItem>> = repository.favoriteSongsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistWithSongs>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TrackItem>>(emptyList())
    val searchResults: StateFlow<List<TrackItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        search("Top Thai Hits Music")
    }

    fun dismissStatusBanner() {
        _statusBanner.value = null
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun search(queryOverride: String? = null) {
        val q = (queryOverride ?: _searchQuery.value).trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = repository.searchTracks(q)
            _isSearching.value = false
        }
    }

    fun scanZongFolder(showBanner: Boolean = true) {
        viewModelScope.launch {
            val count = repository.syncZongFolderWithDatabase()
            if (showBanner) {
                _statusBanner.value = if (count > 0) {
                    "พบเพลงใหม่ในโฟลเดอร์ Zong เพิ่มอีก $count เพลง (พร้อมปกและศิลปิน)"
                } else {
                    "สแกนโฟลเดอร์ Music/Zong เรียบร้อยแล้ว"
                }
            }
        }
    }

    fun playTrack(track: TrackItem, queue: List<TrackItem>) {
        playerController.playTrack(track, queue)
    }

    fun shuffleAndPlay(queue: List<TrackItem>) {
        if (queue.isEmpty()) return
        if (!playbackState.value.shuffleEnabled) {
            playerController.toggleShuffle()
        }
        playerController.playTrack(queue.random(), queue)
    }

    fun downloadTrack(track: TrackItem) {
        viewModelScope.launch {
            _statusBanner.value = "กำลังบันทึก \"${track.title}\" ลงโฟลเดอร์ Zong (พร้อมปกและศิลปิน)..."
            val res = repository.downloadTrackDirect(track)
            _statusBanner.value = res.getOrElse { it.message ?: "ดาวน์โหลดไม่สำเร็จ" }
        }
    }

    fun downloadAllTracks(tracks: List<TrackItem>) {
        val pending = tracks.filter { !it.isDownloadedLocally }
        if (pending.isEmpty()) {
            _statusBanner.value = "เพลงทั้งหมดในรายการนี้ถูกบันทึกในโฟลเดอร์ Zong แล้ว"
            return
        }
        viewModelScope.launch {
            _statusBanner.value = "กำลังบันทึก ${pending.size} เพลงลงโฟลเดอร์ Zong..."
            var okCount = 0
            for (t in pending) {
                if (repository.downloadTrackDirect(t).isSuccess) {
                    okCount++
                }
            }
            _statusBanner.value = "บันทึกเพลงลงโฟลเดอร์ Zong สำเร็จ $okCount / ${pending.size} เพลง"
        }
    }

    fun deleteOfflineTrack(videoId: String) {
        viewModelScope.launch {
            repository.removeOfflineFile(videoId)
            _statusBanner.value = "ลบไฟล์ออกจากโฟลเดอร์ Zong แล้ว"
        }
    }

    fun toggleFavorite(track: TrackItem) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }

    fun createPlaylist(name: String, initialTrack: TrackItem? = null) {
        viewModelScope.launch {
            val newId = repository.createPlaylist(name)
            if (newId != null && initialTrack != null) {
                repository.addSongToPlaylist(newId, initialTrack)
            }
            _statusBanner.value = "สร้างเพลย์ลิสต์ \"$name\" แล้ว"
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
            _statusBanner.value = "เปลี่ยนชื่อเพลย์ลิสต์เป็น \"$newName\" แล้ว"
        }
    }

    fun addSongToPlaylist(playlistId: String, track: TrackItem) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, track)
            _statusBanner.value = "เพิ่ม \"${track.title}\" ลงเพลย์ลิสต์แล้ว"
        }
    }

    fun removeSongFromPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, videoId)
            _statusBanner.value = "นำเพลงออกจากเพลย์ลิสต์แล้ว"
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            _statusBanner.value = "ลบเพลย์ลิสต์แล้ว"
        }
    }

    // Google Drive Direct Folder & File Operations
    fun getSuggestedDriveUri(): Uri? = driveSyncManager.getSuggestedDriveInitialUri()

    fun onDriveFolderSelected(treeUri: Uri?) {
        if (treeUri == null) return
        val res = driveSyncManager.connectFolderUri(treeUri)
        res.onSuccess { conn ->
            _statusBanner.value = "เชื่อมต่อโฟลเดอร์ Google Drive \"${conn.folderName}\" สำเร็จ"
            syncGoogleDriveNow()
        }.onFailure { err ->
            _statusBanner.value = "ไม่สามารถเชื่อมต่อโฟลเดอร์ได้: ${err.message}"
        }
    }

    fun disconnectDriveFolder() {
        driveSyncManager.disconnectFolder()
        _statusBanner.value = "ยกเลิกการเชื่อมต่อโฟลเดอร์ Google Drive แล้ว"
    }

    fun syncGoogleDriveNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            _statusBanner.value = "กำลังซิงก์ข้อมูลและสำรองเพลงลง Google Drive..."
            val res = repository.syncWithGoogleDriveNow()
            _isSyncing.value = false
            _statusBanner.value = res.getOrElse { it.message ?: "เกิดข้อผิดพลาดในการซิงก์ Google Drive" }
        }
    }

    fun importAudioFromDrive(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val res = repository.importAudioFromGoogleDrive(uri)
            res.onSuccess { track ->
                _statusBanner.value = "นำเข้า \"${track.title}\" จาก Google Drive สำเร็จ"
                playerController.playTrack(track, listOf(track))
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "นำเข้าไฟล์เพลงไม่สำเร็จ"
            }
        }
    }

    fun exportBackupToDrive(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val res = repository.exportBackupFileToDriveUri(uri)
            _statusBanner.value = res.getOrElse { it.message ?: "ส่งออกไม่สำเร็จ" }
        }
    }

    fun importBackupFromDrive(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val res = repository.importBackupFileFromDriveUri(uri)
            _statusBanner.value = res.getOrElse { it.message ?: "กู้คืนไม่สำเร็จ" }
        }
    }

    class Factory(
        private val repository: MusicRepository,
        private val playerController: PlayerController,
        private val driveSyncManager: GoogleDriveSyncManager,
        private val networkMonitor: NetworkMonitor
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                repository = repository,
                playerController = playerController,
                driveSyncManager = driveSyncManager,
                networkMonitor = networkMonitor
            ) as T
        }
    }
}
