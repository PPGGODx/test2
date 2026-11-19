package com.hybridmusic.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.hybridmusic.player.R
import com.hybridmusic.player.data.local.entity.PlaylistWithSongs
import com.hybridmusic.player.data.local.entity.SyncStatus
import com.hybridmusic.player.data.remote.cloud.DriveFolderConnection
import com.hybridmusic.player.data.remote.innertube.TrackItem
import com.hybridmusic.player.player.PlaybackUiState
import com.hybridmusic.player.ui.components.CrimsonEqualizerBars
import com.hybridmusic.player.ui.components.NetworkStatusPill
import com.hybridmusic.player.ui.components.SpicetifyCrimsonGlassBackground
import com.hybridmusic.player.ui.components.SpicetifyGlassArtworkFrame
import com.hybridmusic.player.ui.components.SpicetifyGlassCard
import com.hybridmusic.player.ui.theme.SpicetifyCrimsonPalette
import com.hybridmusic.player.ui.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val isOnline by viewModel.isOnline.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val offlineSongs by viewModel.offlineSongs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val connectedDriveFolder by viewModel.connectedDriveFolder.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var trackForPlaylistDialog by remember { mutableStateOf<TrackItem?>(null) }

    // Google Drive Native Storage Access Framework Launchers (Zero Google Cloud setup!)
    val openDriveFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.onDriveFolderSelected(uri)
    }

    val importDriveAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.importAudioFromDrive(uri)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        viewModel.exportBackupToDrive(uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.importBackupFromDrive(uri)
    }

    // Handle system Back button when inside a Playlist Detail screen
    BackHandler(enabled = selectedPlaylistId != null) {
        selectedPlaylistId = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Spicetify Crimson Frosted-Glass Obsidian Backdrop
        SpicetifyCrimsonGlassBackground()

        // 2. Transparent Glass Scaffold
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    SpicetifyGlassTopBar(
                        isOnline = isOnline,
                        connectedFolder = connectedDriveFolder,
                        onDrivePillClick = {
                            selectedPlaylistId = null
                            selectedTab = 3
                        }
                    )
                    statusBanner?.let { banner ->
                        SpicetifyGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            activeCrimson = true,
                            onClick = { viewModel.dismissStatusBanner() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = banner,
                                    fontSize = 12.sp,
                                    color = SpicetifyCrimsonPalette.PureIce,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "ปิด",
                                    tint = SpicetifyCrimsonPalette.SilverMist,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    playbackState.currentTrack?.let { currentTrack ->
                        SpicetifyGlassMiniPlayer(
                            track = currentTrack,
                            isPlaying = playbackState.isPlaying,
                            isBuffering = playbackState.isBuffering,
                            progressRatio = if (playbackState.durationMs > 0L) {
                                (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f,
                            onToggleFavorite = { viewModel.toggleFavorite(currentTrack) },
                            onTogglePlayPause = { viewModel.playerController.togglePlayPause() },
                            onNext = { viewModel.playerController.playNextInQueue() },
                            onExpand = { showFullPlayer = true }
                        )
                    }

                    SpicetifyGlassNavDock(
                        selectedTab = selectedTab,
                        offlineBadgeCount = offlineSongs.size,
                        playlistBadgeCount = playlists.size,
                        onSelectTab = { tabIndex ->
                            selectedPlaylistId = null
                            selectedTab = tabIndex
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val openedPlaylist = selectedPlaylistId?.let { id ->
                    playlists.firstOrNull { it.playlist.playlistId == id }
                }

                if (openedPlaylist != null) {
                    PlaylistDetailScreen(
                        playlistWithSongs = openedPlaylist,
                        librarySongs = librarySongs,
                        searchResults = searchResults,
                        activeVideoId = playbackState.currentTrack?.videoId,
                        isPlaying = playbackState.isPlaying,
                        onBack = { selectedPlaylistId = null },
                        onPlayAll = { tracks ->
                            if (tracks.isNotEmpty()) viewModel.playTrack(tracks.first(), tracks)
                        },
                        onShufflePlay = { tracks -> viewModel.shuffleAndPlay(tracks) },
                        onDownloadAll = { tracks -> viewModel.downloadAllTracks(tracks) },
                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onRemoveSongFromPlaylist = { videoId ->
                            viewModel.removeSongFromPlaylist(openedPlaylist.playlist.playlistId, videoId)
                        },
                        onAddSongToPlaylist = { track ->
                            viewModel.addSongToPlaylist(openedPlaylist.playlist.playlistId, track)
                        },
                        onRenamePlaylist = { newName ->
                            viewModel.renamePlaylist(openedPlaylist.playlist.playlistId, newName)
                        },
                        onDeletePlaylist = {
                            viewModel.deletePlaylist(openedPlaylist.playlist.playlistId)
                            selectedPlaylistId = null
                        },
                        onDownloadTrack = viewModel::downloadTrack,
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                } else {
                    when (selectedTab) {
                        0 -> ExploreTab(
                            isOnline = isOnline,
                            searchQuery = searchQuery,
                            searchResults = searchResults,
                            isSearching = isSearching,
                            librarySongs = librarySongs,
                            playbackError = playbackState.errorMessage,
                            activeVideoId = playbackState.currentTrack?.videoId,
                            isPlaying = playbackState.isPlaying,
                            onDismissPlaybackError = { viewModel.playerController.clearError() },
                            onQueryChange = viewModel::onSearchQueryChange,
                            onSearch = { viewModel.search() },
                            onQuickTagClick = { tag ->
                                viewModel.onSearchQueryChange(tag)
                                viewModel.search(tag)
                            },
                            onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                            onDownloadTrack = viewModel::downloadTrack,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onAddToPlaylist = { trackForPlaylistDialog = it }
                        )

                        1 -> OfflineLibraryTab(
                            offlineSongs = offlineSongs,
                            allLibrarySongs = librarySongs,
                            favoriteSongs = favoriteSongs,
                            activeVideoId = playbackState.currentTrack?.videoId,
                            isPlaying = playbackState.isPlaying,
                            onScanZongFolder = { viewModel.scanZongFolder(showBanner = true) },
                            onPlayAll = { tracks ->
                                if (tracks.isNotEmpty()) viewModel.playTrack(tracks.first(), tracks)
                            },
                            onShufflePlay = { tracks -> viewModel.shuffleAndPlay(tracks) },
                            onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                            onDownloadTrack = viewModel::downloadTrack,
                            onDeleteOffline = viewModel::deleteOfflineTrack,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onAddToPlaylist = { trackForPlaylistDialog = it }
                        )

                        2 -> PlaylistsTab(
                            playlists = playlists,
                            onOpenPlaylist = { playlistId -> selectedPlaylistId = playlistId },
                            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onPlayTrack = { track, list -> viewModel.playTrack(track, list) }
                        )

                        3 -> GoogleDriveTab(
                            connectedFolder = connectedDriveFolder,
                            totalLibraryCount = librarySongs.size,
                            offlineCount = offlineSongs.size,
                            driveBackedUpCount = librarySongs.count { it.isSyncedToCloud },
                            isSyncing = isSyncing,
                            onSelectDriveFolder = {
                                openDriveFolderLauncher.launch(viewModel.getSuggestedDriveUri())
                            },
                            onDisconnectDriveFolder = viewModel::disconnectDriveFolder,
                            onSyncDriveNow = viewModel::syncGoogleDriveNow,
                            onImportAudioFromDrive = {
                                importDriveAudioLauncher.launch(arrayOf("audio/*"))
                            },
                            onExportBackupFile = {
                                exportBackupLauncher.launch("PlayZongX_Backup.json")
                            },
                            onImportBackupFile = {
                                importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showFullPlayer && playbackState.currentTrack != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFullPlayer = false },
            sheetState = sheetState,
            containerColor = Color(0xF212060A),
            contentColor = SpicetifyCrimsonPalette.PureIce,
            scrimColor = Color(0xCC040203)
        ) {
            FullPlayerSheet(
                state = playbackState,
                onToggleFavorite = {
                    playbackState.currentTrack?.let { viewModel.toggleFavorite(it) }
                },
                onDownloadTrack = {
                    playbackState.currentTrack?.let { viewModel.downloadTrack(it) }
                },
                onTogglePlayPause = { viewModel.playerController.togglePlayPause() },
                onSeek = { viewModel.playerController.seekTo(it) },
                onNext = { viewModel.playerController.playNextInQueue() },
                onPrevious = { viewModel.playerController.playPreviousInQueue() },
                onToggleShuffle = { viewModel.playerController.toggleShuffle() },
                onCycleRepeat = { viewModel.playerController.cycleRepeatMode() },
                onSetSleepTimer = { viewModel.playerController.setSleepTimer(it) }
            )
        }
    }

    trackForPlaylistDialog?.let { selectedTrack ->
        AddToPlaylistDialog(
            track = selectedTrack,
            playlists = playlists,
            onDismiss = { trackForPlaylistDialog = null },
            onSelectPlaylist = { playlistId ->
                viewModel.addSongToPlaylist(playlistId, selectedTrack)
                trackForPlaylistDialog = null
            },
            onCreateAndAdd = { newName ->
                viewModel.createPlaylist(newName, initialTrack = selectedTrack)
                trackForPlaylistDialog = null
            }
        )
    }
}

@Composable
private fun SpicetifyGlassTopBar(
    isOnline: Boolean,
    connectedFolder: DriveFolderConnection?,
    onDrivePillClick: () -> Unit
) {
    SpicetifyGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        activeCrimson = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.playzongx_logo),
                    contentDescription = "PlayZongX Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, SpicetifyCrimsonPalette.CrimsonCore, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "PlayZongX",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = SpicetifyCrimsonPalette.PureIce
                    )
                    Text(
                        text = "Spicetify Crimson Glass",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = SpicetifyCrimsonPalette.CrimsonBright
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                NetworkStatusPill(isOnline = isOnline)
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = if (connectedFolder != null) {
                    SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.22f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                border = BorderStroke(
                    1.dp,
                    if (connectedFolder != null) SpicetifyCrimsonPalette.CrimsonCore
                    else Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier.clickable { onDrivePillClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AddToDrive,
                        contentDescription = "Google Drive",
                        tint = if (connectedFolder != null) SpicetifyCrimsonPalette.CrimsonBright else SpicetifyCrimsonPalette.PureIce,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (connectedFolder != null) "Drive พร้อมใช้" else "Google Drive",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SpicetifyCrimsonPalette.PureIce,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SpicetifyGlassNavDock(
    selectedTab: Int,
    offlineBadgeCount: Int,
    playlistBadgeCount: Int,
    onSelectTab: (Int) -> Unit
) {
    val navItems = listOf(
        Triple("หน้าหลัก", Icons.Default.Explore, 0),
        Triple("คลัง ($offlineBadgeCount)", Icons.Default.LibraryMusic, 1),
        Triple("เพลย์ลิสต์ ($playlistBadgeCount)", Icons.Default.QueueMusic, 2),
        Triple("Drive", Icons.Default.AddToDrive, 3)
    )

    SpicetifyGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { (label, icon, index) ->
                val isSelected = selectedTab == index
                val pillBg = if (isSelected) {
                    Brush.horizontalGradient(
                        listOf(
                            SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.34f),
                            SpicetifyCrimsonPalette.RubyDeep.copy(alpha = 0.42f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(15.dp))
                        .background(pillBg)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    1.dp,
                                    SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.75f),
                                    RoundedCornerShape(15.dp)
                                )
                            } else Modifier
                        )
                        .clickable { onSelectTab(index) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) SpicetifyCrimsonPalette.CrimsonBright else SpicetifyCrimsonPalette.SilverMist,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) SpicetifyCrimsonPalette.PureIce else SpicetifyCrimsonPalette.SilverMist
                    )
                }
            }
        }
    }
}

@Composable
private fun SpicetifyGlassMiniPlayer(
    track: TrackItem,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progressRatio: Float,
    onToggleFavorite: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit
) {
    SpicetifyGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        activeCrimson = true,
        onClick = onExpand
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val miniImageModel: Any = remember(track.localThumbPath, track.thumbnailUrl) {
                    val f = track.localThumbPath?.let { File(it) }
                    if (f != null && f.exists()) f else track.thumbnailUrl
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                ) {
                    AsyncImage(
                        model = miniImageModel,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CrimsonEqualizerBars(isPlaying = isPlaying)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SpicetifyCrimsonPalette.PureIce,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpicetifyCrimsonPalette.SilverMist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) SpicetifyCrimsonPalette.CrimsonCore else SpicetifyCrimsonPalette.SilverMist
                    )
                }
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = SpicetifyCrimsonPalette.CrimsonCore
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SpicetifyCrimsonPalette.CrimsonCore)
                            .clickable { onTogglePlayPause() }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = SpicetifyCrimsonPalette.PureIce
                    )
                }
            }

            // Spicetify Sleek Crimson Laser Progress Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressRatio)
                        .height(2.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    SpicetifyCrimsonPalette.CrimsonCore,
                                    SpicetifyCrimsonPalette.RoseNeon
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun ExploreTab(
    isOnline: Boolean,
    searchQuery: String,
    searchResults: List<TrackItem>,
    isSearching: Boolean,
    librarySongs: List<TrackItem>,
    playbackError: String?,
    activeVideoId: String?,
    isPlaying: Boolean,
    onDismissPlaybackError: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onQuickTagClick: (String) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onDownloadTrack: (TrackItem) -> Unit,
    onToggleFavorite: (TrackItem) -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit
) {
    val libraryMap = remember(librarySongs) { librarySongs.associateBy { it.videoId } }
    val mergedResults = remember(searchResults, libraryMap) {
        searchResults.map { libraryMap[it.videoId] ?: it }
    }
    val quickGenres = listOf(
        "ฮิตติดชาร์ต" to "Top Thai Hits Music",
        "เพลงไทยมาแรง" to "เพลงไทยใหม่ล่าสุด",
        "Lo-Fi Chill" to "Lofi hip hop chill beats",
        "Rock & Metal" to "Thai Rock Hits",
        "Anime & J-Pop" to "J-Pop Anime Hits",
        "R&B Soul" to "Thai R&B Chill"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        if (!isOnline) {
            SpicetifyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WifiOff, contentDescription = null, tint = SpicetifyCrimsonPalette.OfflineCrimson)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "โหมดออฟไลน์ • แสดงเพลงที่บันทึกไว้ในเครื่อง",
                        fontSize = 12.sp,
                        color = SpicetifyCrimsonPalette.PureIce
                    )
                }
            }
        }

        playbackError?.let { err ->
            SpicetifyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                activeCrimson = true,
                onClick = onDismissPlaybackError
            ) {
                Text(
                    text = err,
                    modifier = Modifier.padding(10.dp),
                    fontSize = 12.sp,
                    color = SpicetifyCrimsonPalette.RoseNeon
                )
            }
        }

        // Spicetify Sleek Glass Search Bar
        SpicetifyGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(50)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "คุณต้องการฟังเพลงอะไร?",
                        fontSize = 13.sp,
                        color = SpicetifyCrimsonPalette.SilverMist
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = SpicetifyCrimsonPalette.PureIce,
                    unfocusedTextColor = SpicetifyCrimsonPalette.PureIce,
                    cursorColor = SpicetifyCrimsonPalette.CrimsonCore
                ),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SpicetifyCrimsonPalette.CrimsonBright)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "ล้าง", tint = SpicetifyCrimsonPalette.SilverMist)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
        }

        // Spicetify Mood / Genre Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickGenres.forEach { (label, query) ->
                val isSelected = searchQuery.equals(query, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) SpicetifyCrimsonPalette.CrimsonCore else Color(0x551E0D14),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) SpicetifyCrimsonPalette.CrimsonBright else Color.White.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier.clickable { onQuickTagClick(query) }
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else SpicetifyCrimsonPalette.PureIce
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = SpicetifyCrimsonPalette.CrimsonCore)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "กำลังค้นหาเพลง...",
                        fontSize = 12.sp,
                        color = SpicetifyCrimsonPalette.SilverMist
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Spicetify Signature 2x2 Quick-Pick Glass Grid at Top
                if (mergedResults.size >= 4) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "แนะนำสำหรับคุณ",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SpicetifyCrimsonPalette.PureIce
                            )
                            val topFour = mergedResults.take(4)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                topFour.take(2).forEach { track ->
                                    SpicetifyQuickPickCard(
                                        track = track,
                                        isCurrentTrack = track.videoId == activeVideoId,
                                        onClick = { onPlayTrack(track, mergedResults) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                topFour.drop(2).take(2).forEach { track ->
                                    SpicetifyQuickPickCard(
                                        track = track,
                                        isCurrentTrack = track.videoId == activeVideoId,
                                        onClick = { onPlayTrack(track, mergedResults) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "รายการเพลงทั้งหมด",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SpicetifyCrimsonPalette.PureIce
                            )
                        }
                    }
                }

                itemsIndexed(mergedResults, key = { _, t -> t.videoId }) { index, track ->
                    SpicetifyTrackRow(
                        index = index + 1,
                        track = track,
                        isCurrentlyPlaying = track.videoId == activeVideoId && isPlaying,
                        isCurrentTrack = track.videoId == activeVideoId,
                        onPlay = { onPlayTrack(track, mergedResults) },
                        onDownload = { onDownloadTrack(track) },
                        onDelete = null,
                        onRemoveFromPlaylist = null,
                        onToggleFavorite = { onToggleFavorite(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpicetifyQuickPickCard(
    track: TrackItem,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SpicetifyGlassCard(
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(10.dp),
        activeCrimson = isCurrentTrack,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = track.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrentTrack) SpicetifyCrimsonPalette.CrimsonBright else SpicetifyCrimsonPalette.PureIce,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            )
        }
    }
}

@Composable
private fun OfflineLibraryTab(
    offlineSongs: List<TrackItem>,
    allLibrarySongs: List<TrackItem>,
    favoriteSongs: List<TrackItem>,
    activeVideoId: String?,
    isPlaying: Boolean,
    onScanZongFolder: () -> Unit,
    onPlayAll: (List<TrackItem>) -> Unit,
    onShufflePlay: (List<TrackItem>) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onDownloadTrack: (TrackItem) -> Unit,
    onDeleteOffline: (String) -> Unit,
    onToggleFavorite: (TrackItem) -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit
) {
    var filterMode by remember { mutableIntStateOf(0) }

    val displayedList = when (filterMode) {
        0 -> offlineSongs
        1 -> allLibrarySongs
        else -> favoriteSongs
    }

    val filters = listOf(
        "ออฟไลน์ในเครื่อง (${offlineSongs.size})",
        "ทั้งหมด (${allLibrarySongs.size})",
        "ถูกใจ (${favoriteSongs.size})"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        SpicetifyGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            shape = RoundedCornerShape(14.dp),
            activeCrimson = true
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = SpicetifyCrimsonPalette.CrimsonBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "โฟลเดอร์เก็บเพลงในเครื่อง: Music/Zong",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpicetifyCrimsonPalette.PureIce
                        )
                        Text(
                            text = "ฝังปกเพลง + ชื่อเพลง + ศิลปิน ลงไฟล์ .m4a อัตโนมัติ",
                            fontSize = 10.sp,
                            color = SpicetifyCrimsonPalette.SilverMist
                        )
                    }
                }
                IconButton(onClick = onScanZongFolder, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "สแกนโฟลเดอร์ Zong",
                        tint = SpicetifyCrimsonPalette.CrimsonBright,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEachIndexed { idx, title ->
                val selected = filterMode == idx
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) SpicetifyCrimsonPalette.CrimsonCore else Color(0x551E0D14),
                    border = BorderStroke(
                        1.dp,
                        if (selected) SpicetifyCrimsonPalette.CrimsonBright else Color.White.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier.clickable { filterMode = idx }
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else SpicetifyCrimsonPalette.PureIce
                    )
                }
            }
        }

        if (displayedList.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onPlayAll(displayedList) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpicetifyCrimsonPalette.CrimsonCore,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("เล่นทั้งหมด", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onShufflePlay(displayedList) },
                    border = BorderStroke(1.dp, SpicetifyCrimsonPalette.CrimsonCore),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = SpicetifyCrimsonPalette.CrimsonBright)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("สุ่มเล่น", color = SpicetifyCrimsonPalette.PureIce, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (displayedList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SpicetifyGlassCard(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "ยังไม่มีเพลงในหมวดนี้ • ค้นหาเพลงแล้วกดปุ่มดาวน์โหลดเพื่อฟังแบบออฟไลน์",
                        modifier = Modifier.padding(20.dp),
                        fontSize = 13.sp,
                        color = SpicetifyCrimsonPalette.SilverMist
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(displayedList, key = { _, t -> t.videoId }) { index, track ->
                    SpicetifyTrackRow(
                        index = index + 1,
                        track = track,
                        isCurrentlyPlaying = track.videoId == activeVideoId && isPlaying,
                        isCurrentTrack = track.videoId == activeVideoId,
                        onPlay = { onPlayTrack(track, displayedList) },
                        onDownload = { onDownloadTrack(track) },
                        onDelete = if (track.isDownloadedLocally) {
                            { onDeleteOffline(track.videoId) }
                        } else null,
                        onRemoveFromPlaylist = null,
                        onToggleFavorite = { onToggleFavorite(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistWithSongs>,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Button(
            onClick = { showCreateDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = SpicetifyCrimsonPalette.CrimsonCore,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "สร้างเพลย์ลิสต์ใหม่",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SpicetifyGlassCard(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "ยังไม่มีเพลย์ลิสต์ • กดปุ่มด้านบนเพื่อสร้างเพลย์ลิสต์แรกของคุณ",
                        modifier = Modifier.padding(20.dp),
                        fontSize = 13.sp,
                        color = SpicetifyCrimsonPalette.SilverMist
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playlists, key = { it.playlist.playlistId }) { item ->
                    val tracks = item.songs.map { TrackItem.fromEntity(it) }
                    SpicetifyGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenPlaylist(item.playlist.playlistId) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlaylistMosaicThumbnail(tracks = tracks, sizeDp = 58)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SpicetifyCrimsonPalette.PureIce,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${tracks.size} เพลง • แตะเพื่อเปิดดูด้านใน",
                                    fontSize = 12.sp,
                                    color = SpicetifyCrimsonPalette.SilverMist
                                )
                            }
                            if (tracks.isNotEmpty()) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(SpicetifyCrimsonPalette.CrimsonCore)
                                        .clickable { onPlayTrack(tracks.first(), tracks) }
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "เล่นเพลย์ลิสต์",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            IconButton(onClick = { onDeletePlaylist(item.playlist.playlistId) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "ลบเพลย์ลิสต์",
                                    tint = SpicetifyCrimsonPalette.OfflineCrimson
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "เปิดเพลย์ลิสต์",
                                tint = SpicetifyCrimsonPalette.CrimsonBright
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xF5180A10),
            title = { Text("สร้างเพลย์ลิสต์ใหม่", color = SpicetifyCrimsonPalette.PureIce) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("ตั้งชื่อเพลย์ลิสต์...") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName)
                            newPlaylistName = ""
                        }
                        showCreateDialog = false
                    }
                ) {
                    Text("สร้าง", color = SpicetifyCrimsonPalette.CrimsonBright, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("ยกเลิก", color = SpicetifyCrimsonPalette.SilverMist)
                }
            }
        )
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlistWithSongs: PlaylistWithSongs,
    librarySongs: List<TrackItem>,
    searchResults: List<TrackItem>,
    activeVideoId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: (List<TrackItem>) -> Unit,
    onShufflePlay: (List<TrackItem>) -> Unit,
    onDownloadAll: (List<TrackItem>) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onRemoveSongFromPlaylist: (String) -> Unit,
    onAddSongToPlaylist: (TrackItem) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onDownloadTrack: (TrackItem) -> Unit,
    onToggleFavorite: (TrackItem) -> Unit
) {
    val tracks = remember(playlistWithSongs) {
        playlistWithSongs.songs.map { TrackItem.fromEntity(it) }
    }
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(playlistWithSongs.playlist.name) {
        mutableStateOf(playlistWithSongs.playlist.name)
    }

    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Top Back & Actions Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onBack() }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "ย้อนกลับ",
                    tint = SpicetifyCrimsonPalette.PureIce
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "เพลย์ลิสต์ทั้งหมด",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SpicetifyCrimsonPalette.PureIce
                )
            }

            Row {
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "เปลี่ยนชื่อ",
                        tint = SpicetifyCrimsonPalette.CrimsonBright
                    )
                }
                IconButton(onClick = onDeletePlaylist) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "ลบเพลย์ลิสต์",
                        tint = SpicetifyCrimsonPalette.OfflineCrimson
                    )
                }
            }
        }

        // Spicetify Crimson Glass Playlist Hero Banner
        SpicetifyGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            activeCrimson = true
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlaylistMosaicThumbnail(tracks = tracks, sizeDp = 88)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PLAYLIST",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = SpicetifyCrimsonPalette.CrimsonBright
                        )
                        Text(
                            text = playlistWithSongs.playlist.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpicetifyCrimsonPalette.PureIce,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val durMin = totalDurationMs / 60_000L
                        Text(
                            text = if (durMin > 0) "${tracks.size} เพลง • ประมาณ $durMin นาที" else "${tracks.size} เพลง",
                            fontSize = 12.sp,
                            color = SpicetifyCrimsonPalette.SilverMist
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onPlayAll(tracks) },
                        enabled = tracks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpicetifyCrimsonPalette.CrimsonCore,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("เล่นทั้งหมด", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { onShufflePlay(tracks) },
                        enabled = tracks.isNotEmpty(),
                        border = BorderStroke(1.dp, SpicetifyCrimsonPalette.CrimsonCore),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = SpicetifyCrimsonPalette.CrimsonBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("สุ่มเล่น", color = SpicetifyCrimsonPalette.PureIce, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAddSongsDialog = true },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = SpicetifyCrimsonPalette.PureIce,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("เพิ่มเพลง", color = SpicetifyCrimsonPalette.PureIce, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { onDownloadAll(tracks) },
                        enabled = tracks.isNotEmpty(),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = SpicetifyCrimsonPalette.CrimsonBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("โหลดทั้งหมด", color = SpicetifyCrimsonPalette.PureIce, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SpicetifyGlassCard(modifier = Modifier.padding(20.dp)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "เพลย์ลิสต์นี้ยังว่างอยู่",
                            fontWeight = FontWeight.Bold,
                            color = SpicetifyCrimsonPalette.PureIce
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "กดปุ่ม "+ เพิ่มเพลง" ด้านบนเพื่อเลือกเพลงเข้าเพลย์ลิสต์ได้เลย",
                            fontSize = 12.sp,
                            color = SpicetifyCrimsonPalette.SilverMist
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(tracks, key = { _, t -> t.videoId }) { index, track ->
                    SpicetifyTrackRow(
                        index = index + 1,
                        track = track,
                        isCurrentlyPlaying = track.videoId == activeVideoId && isPlaying,
                        isCurrentTrack = track.videoId == activeVideoId,
                        onPlay = { onPlayTrack(track, tracks) },
                        onDownload = { onDownloadTrack(track) },
                        onDelete = null,
                        onRemoveFromPlaylist = { onRemoveSongFromPlaylist(track.videoId) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onAddToPlaylist = null
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = Color(0xF5180A10),
            title = { Text("เปลี่ยนชื่อเพลย์ลิสต์", color = SpicetifyCrimsonPalette.PureIce) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenamePlaylist(renameText)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("บันทึก", color = SpicetifyCrimsonPalette.CrimsonBright, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("ยกเลิก", color = SpicetifyCrimsonPalette.SilverMist)
                }
            }
        )
    }

    if (showAddSongsDialog) {
        val existingIds = remember(tracks) { tracks.map { it.videoId }.toSet() }
        val candidates = remember(librarySongs, searchResults, existingIds) {
            (librarySongs + searchResults)
                .distinctBy { it.videoId }
                .filter { it.videoId !in existingIds }
        }

        AlertDialog(
            onDismissRequest = { showAddSongsDialog = false },
            containerColor = Color(0xF5180A10),
            title = {
                Text(
                    "เพิ่มเพลงเข้า "${playlistWithSongs.playlist.name}"",
                    color = SpicetifyCrimsonPalette.PureIce
                )
            },
            text = {
                if (candidates.isEmpty()) {
                    Text(
                        "ไม่มีเพลงใหม่ให้เพิ่ม (ไปที่หน้าหลักเพื่อค้นหาเพลงเพิ่มเติมก่อน)",
                        color = SpicetifyCrimsonPalette.SilverMist,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidates, key = { it.videoId }) { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable { onAddSongToPlaylist(candidate) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = candidate.thumbnailUrl,
                                    contentDescription = candidate.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SpicetifyCrimsonPalette.PureIce,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = candidate.artist,
                                        fontSize = 11.sp,
                                        color = SpicetifyCrimsonPalette.SilverMist,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "เพิ่ม",
                                    tint = SpicetifyCrimsonPalette.CrimsonBright
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddSongsDialog = false }) {
                    Text("เสร็จสิ้น", color = SpicetifyCrimsonPalette.CrimsonBright)
                }
            }
        )
    }
}

@Composable
private fun PlaylistMosaicThumbnail(tracks: List<TrackItem>, sizeDp: Int) {
    val size = sizeDp.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.35f),
                        SpicetifyCrimsonPalette.RubyDeep.copy(alpha = 0.65f)
                    )
                )
            )
            .border(1.dp, SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
    ) {
        if (tracks.isEmpty()) {
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = null,
                tint = SpicetifyCrimsonPalette.PureIce,
                modifier = Modifier.size(size / 2)
            )
        } else if (tracks.size < 4) {
            AsyncImage(
                model = tracks.first().thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val half = size / 2
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.height(half)) {
                    AsyncImage(
                        model = tracks[0].thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(half)
                    )
                    AsyncImage(
                        model = tracks[1].thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(half)
                    )
                }
                Row(modifier = Modifier.height(half)) {
                    AsyncImage(
                        model = tracks[2].thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(half)
                    )
                    AsyncImage(
                        model = tracks[3].thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(half)
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleDriveTab(
    connectedFolder: DriveFolderConnection?,
    totalLibraryCount: Int,
    offlineCount: Int,
    driveBackedUpCount: Int,
    isSyncing: Boolean,
    onSelectDriveFolder: () -> Unit,
    onDisconnectDriveFolder: () -> Unit,
    onSyncDriveNow: () -> Unit,
    onImportAudioFromDrive: () -> Unit,
    onExportBackupFile: () -> Unit,
    onImportBackupFile: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Direct Google Drive Folder Connection Card (Zero Google Cloud setup!)
        item {
            SpicetifyGlassCard(
                modifier = Modifier.fillMaxWidth(),
                activeCrimson = connectedFolder != null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AddToDrive,
                            contentDescription = null,
                            tint = SpicetifyCrimsonPalette.CrimsonBright,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "เชื่อมต่อ Google Drive โดยตรง",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SpicetifyCrimsonPalette.PureIce
                            )
                            Text(
                                text = "ไม่ต้องตั้งค่า Google Cloud / SHA-1 ใช้งานได้ทันที",
                                fontSize = 11.sp,
                                color = SpicetifyCrimsonPalette.CrimsonBright
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "เลือกโฟลเดอร์บน Google Drive ของคุณเพื่อซิงก์เพลย์ลิสต์ รายชื่อเพลง และสำรองไฟล์เสียง (.m4a) เก็บไว้เปิดฟังข้ามเครื่องได้ตลอดเวลา",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpicetifyCrimsonPalette.SilverMist
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (connectedFolder == null) {
                        Button(
                            onClick = onSelectDriveFolder,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpicetifyCrimsonPalette.CrimsonCore,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "เลือกโฟลเดอร์ Google Drive",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = SpicetifyCrimsonPalette.OnlineEmerald
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "เชื่อมต่อแล้ว: ${connectedFolder.folderName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SpicetifyCrimsonPalette.PureIce
                                    )
                                    Text(
                                        text = "ไฟล์เพลงที่ดาวน์โหลดจะสำรองเข้าโฟลเดอร์นี้ให้อัตโนมัติ",
                                        fontSize = 11.sp,
                                        color = SpicetifyCrimsonPalette.SilverMist
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onSyncDriveNow,
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SpicetifyCrimsonPalette.CrimsonCore,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = null)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ซิงก์ทันที", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onDisconnectDriveFolder,
                                border = BorderStroke(1.dp, SpicetifyCrimsonPalette.OfflineCrimson),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("ยกเลิกโฟลเดอร์", color = SpicetifyCrimsonPalette.OfflineCrimson)
                            }
                        }
                    }
                }
            }
        }

        // 2. Direct File Actions via Google Drive Picker
        item {
            SpicetifyGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "จัดการไฟล์ผ่าน Google Drive โดยตรง",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpicetifyCrimsonPalette.PureIce
                    )

                    OutlinedButton(
                        onClick = onImportAudioFromDrive,
                        border = BorderStroke(1.dp, SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.75f)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = SpicetifyCrimsonPalette.CrimsonBright
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "นำเข้าไฟล์เพลงจาก Google Drive (.mp3 / .m4a)",
                            color = SpicetifyCrimsonPalette.PureIce,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onExportBackupFile,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = SpicetifyCrimsonPalette.CrimsonBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ส่งออกสำรอง", color = SpicetifyCrimsonPalette.PureIce, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onImportBackupFile,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = null,
                                tint = SpicetifyCrimsonPalette.CrimsonBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("กู้คืนสำรอง", color = SpicetifyCrimsonPalette.PureIce, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Library & Drive Telemetry Summary
        item {
            SpicetifyGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "สถานะคลังเพลงและ Google Drive",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpicetifyCrimsonPalette.CrimsonBright
                    )
                    Text("• เพลงทั้งหมดในคลัง: $totalLibraryCount เพลง", fontSize = 13.sp, color = SpicetifyCrimsonPalette.PureIce)
                    Text("• บันทึกออฟไลน์ในเครื่อง: $offlineCount เพลง", fontSize = 13.sp, color = SpicetifyCrimsonPalette.PureIce)
                    Text("• สำรองบน Google Drive แล้ว: $driveBackedUpCount เพลง", fontSize = 13.sp, color = SpicetifyCrimsonPalette.PureIce)
                }
            }
        }
    }
}

@Composable
private fun SpicetifyTrackRow(
    index: Int,
    track: TrackItem,
    isCurrentlyPlaying: Boolean,
    isCurrentTrack: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDelete: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (() -> Unit)?
) {
    val imageModel: Any = remember(track.localThumbPath, track.thumbnailUrl) {
        val localFile = track.localThumbPath?.let { File(it) }
        if (localFile != null && localFile.exists()) localFile else track.thumbnailUrl
    }

    SpicetifyGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        activeCrimson = isCurrentTrack,
        onClick = onPlay
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 9.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spicetify Index Number or Animated Crimson Equalizer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(26.dp)
            ) {
                if (isCurrentTrack) {
                    CrimsonEqualizerBars(isPlaying = isCurrentlyPlaying)
                } else {
                    Text(
                        text = "%02d".format(index),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SpicetifyCrimsonPalette.SilverMist
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (isCurrentTrack) SpicetifyCrimsonPalette.CrimsonCore else Color.White.copy(alpha = 0.18f),
                        RoundedCornerShape(10.dp)
                    )
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentTrack) SpicetifyCrimsonPalette.CrimsonBright else SpicetifyCrimsonPalette.PureIce,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (track.isDownloadedLocally) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = "Offline Ready",
                            tint = SpicetifyCrimsonPalette.OnlineEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (track.isSyncedToCloud) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "Backed up on Google Drive",
                            tint = SpicetifyCrimsonPalette.CrimsonBright,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    val subtitle = if (track.durationMs > 0L) {
                        "${track.artist} • ${formatTimeMs(track.durationMs)}"
                    } else {
                        track.artist
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpicetifyCrimsonPalette.SilverMist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (track.isFavorite) SpicetifyCrimsonPalette.CrimsonCore else SpicetifyCrimsonPalette.SilverMist,
                    modifier = Modifier.size(19.dp)
                )
            }

            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = SpicetifyCrimsonPalette.SilverMist,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (track.syncStatus == SyncStatus.DOWNLOADING) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                    color = SpicetifyCrimsonPalette.CrimsonCore
                )
            } else if (!track.isDownloadedLocally) {
                IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download offline",
                        tint = SpicetifyCrimsonPalette.CrimsonBright,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove offline file",
                        tint = SpicetifyCrimsonPalette.OfflineCrimson,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (onRemoveFromPlaylist != null) {
                IconButton(onClick = onRemoveFromPlaylist, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        contentDescription = "Remove from playlist",
                        tint = SpicetifyCrimsonPalette.OfflineCrimson,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FullPlayerSheet(
    state: PlaybackUiState,
    onToggleFavorite: () -> Unit,
    onDownloadTrack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit
) {
    val track = state.currentTrack ?: return
    var showTimerDialog by remember { mutableStateOf(false) }

    val sourceBadge = when {
        track.isDownloadedLocally -> "เล่นจากไฟล์ออฟไลน์ในเครื่อง"
        track.isSyncedToCloud -> "สตรีมจาก Google Drive"
        else -> "สตรีมออนไลน์ความคมชัดสูง"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.55f))
        ) {
            Text(
                text = sourceBadge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SpicetifyCrimsonPalette.CrimsonBright
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Spicetify Sleek Rounded-Square Frosted-Glass Artwork Frame
        val fullImageModel: Any = remember(track.localThumbPath, track.thumbnailUrl) {
            val f = track.localThumbPath?.let { File(it) }
            if (f != null && f.exists()) f else track.thumbnailUrl
        }
        SpicetifyGlassArtworkFrame(
            imageModel = fullImageModel,
            title = track.title,
            isPlaying = state.isPlaying,
            artworkSize = 235.dp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = SpicetifyCrimsonPalette.PureIce,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpicetifyCrimsonPalette.SilverMist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (track.isFavorite) SpicetifyCrimsonPalette.CrimsonCore else SpicetifyCrimsonPalette.SilverMist
                )
            }

            if (!track.isDownloadedLocally) {
                IconButton(onClick = onDownloadTrack) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = SpicetifyCrimsonPalette.CrimsonBright
                    )
                }
            }
        }

        state.errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = err, color = SpicetifyCrimsonPalette.OfflineCrimson, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(10.dp))
        val maxDuration = state.durationMs.coerceAtLeast(1L).toFloat()
        Slider(
            value = state.positionMs.coerceIn(0L, state.durationMs.coerceAtLeast(0L)).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..maxDuration,
            colors = SliderDefaults.colors(
                thumbColor = SpicetifyCrimsonPalette.CrimsonCore,
                activeTrackColor = SpicetifyCrimsonPalette.CrimsonCore,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTimeMs(state.positionMs),
                fontSize = 12.sp,
                color = SpicetifyCrimsonPalette.CrimsonBright
            )
            Text(
                formatTimeMs(state.durationMs),
                fontSize = 12.sp,
                color = SpicetifyCrimsonPalette.SilverMist
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffleEnabled) SpicetifyCrimsonPalette.CrimsonCore else SpicetifyCrimsonPalette.SilverMist
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = SpicetifyCrimsonPalette.PureIce,
                    modifier = Modifier.size(34.dp)
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                SpicetifyCrimsonPalette.CrimsonCore,
                                SpicetifyCrimsonPalette.RubyDeep
                            )
                        )
                    )
                    .clickable { onTogglePlayPause() }
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = SpicetifyCrimsonPalette.PureIce,
                    modifier = Modifier.size(34.dp)
                )
            }
            IconButton(onClick = onCycleRepeat) {
                val icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                val tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) SpicetifyCrimsonPalette.CrimsonCore else SpicetifyCrimsonPalette.SilverMist
                Icon(icon, contentDescription = "Repeat", tint = tint)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
            onClick = { showTimerDialog = true },
            border = BorderStroke(1.dp, SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(50)
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = SpicetifyCrimsonPalette.CrimsonBright)
            Spacer(modifier = Modifier.width(6.dp))
            val label = state.sleepTimerRemainingSec?.let {
                "ปิดเพลงอัตโนมัติใน ${it / 60} นาที ${it % 60} วิ"
            } ?: "ตั้งเวลาปิดเพลงอัตโนมัติ"
            Text(label, fontSize = 12.sp, color = SpicetifyCrimsonPalette.PureIce)
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            containerColor = Color(0xF5180A10),
            title = { Text("ตั้งเวลาปิดเพลงอัตโนมัติ", color = SpicetifyCrimsonPalette.PureIce) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        TextButton(
                            onClick = {
                                onSetSleepTimer(mins)
                                showTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$mins นาที", color = SpicetifyCrimsonPalette.CrimsonBright)
                        }
                    }
                    TextButton(
                        onClick = {
                            onSetSleepTimer(null)
                            showTimerDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ยกเลิกตัวตั้งเวลา", color = SpicetifyCrimsonPalette.OfflineCrimson)
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    track: TrackItem,
    playlists: List<PlaylistWithSongs>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onCreateAndAdd: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF5180A10),
        title = {
            Text(
                "เพิ่ม "${track.title}" ลงเพลย์ลิสต์",
                color = SpicetifyCrimsonPalette.PureIce
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                playlists.forEach { item ->
                    TextButton(
                        onClick = { onSelectPlaylist(item.playlist.playlistId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(item.playlist.name, color = SpicetifyCrimsonPalette.CrimsonBright)
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("หรือสร้างเพลย์ลิสต์ใหม่...") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newName.isNotBlank()) onCreateAndAdd(newName) else onDismiss()
                }
            ) {
                Text("ตกลง", color = SpicetifyCrimsonPalette.CrimsonBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ปิด", color = SpicetifyCrimsonPalette.SilverMist)
            }
        }
    )
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
