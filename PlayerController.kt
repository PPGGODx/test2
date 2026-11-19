package com.hybridmusic.player.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.hybridmusic.player.data.remote.innertube.TrackItem
import com.hybridmusic.player.data.repository.MusicRepository
import com.hybridmusic.player.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PlaybackUiState(
    val currentTrack: TrackItem? = null,
    val queue: List<TrackItem> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sleepTimerRemainingSec: Int? = null,
    val errorMessage: String? = null
)

class PlayerController(
    private val context: Context,
    private val repository: MusicRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var retriedCurrentTrack = false

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    attachPlayerListener(controller)
                    startProgressUpdater()
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private suspend fun awaitMediaController(): MediaController? {
        mediaController?.let { return it }
        val future = controllerFuture ?: return null
        return suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    val ctrl = runCatching { future.get() }.getOrNull()
                    if (ctrl != null && mediaController == null) {
                        mediaController = ctrl
                        attachPlayerListener(ctrl)
                        startProgressUpdater()
                    }
                    if (continuation.isActive) {
                        continuation.resume(ctrl)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    }

    private fun attachPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(
                    isPlaying = isPlaying,
                    errorMessage = if (isPlaying) null else _uiState.value.errorMessage
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _uiState.value = _uiState.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = player.duration.coerceAtLeast(0L)
                )
                if (playbackState == Player.STATE_READY) {
                    retriedCurrentTrack = false
                }
                if (playbackState == Player.STATE_ENDED) {
                    playNextInQueue()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val track = _uiState.value.currentTrack
                if (track != null && !retriedCurrentTrack) {
                    retriedCurrentTrack = true
                    repository.invalidateStreamCache(track.videoId)
                    playTrackInternal(track, _uiState.value.queue, forceRefresh = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isBuffering = false,
                        isPlaying = false,
                        errorMessage = "เล่นเพลงไม่สำเร็จ (${error.errorCodeName}) กรุณาลองกดเล่นใหม่อีกครั้ง"
                    )
                }
            }
        })
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                mediaController?.let { player ->
                    if (player.isPlaying) {
                        _uiState.value = _uiState.value.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(400L)
            }
        }
    }

    fun playTrack(track: TrackItem, queue: List<TrackItem> = listOf(track)) {
        retriedCurrentTrack = false
        playTrackInternal(track, queue, forceRefresh = false)
    }

    private fun playTrackInternal(
        track: TrackItem,
        queue: List<TrackItem>,
        forceRefresh: Boolean
    ) {
        scope.launch {
            val normalizedQueue = if (queue.any { it.videoId == track.videoId }) {
                queue
            } else {
                listOf(track) + queue
            }

            _uiState.value = _uiState.value.copy(
                currentTrack = track,
                queue = normalizedQueue,
                isBuffering = true,
                errorMessage = null
            )

            val uri = repository.resolvePlayableUri(track, forceRefresh = forceRefresh)
            if (uri == null) {
                _uiState.value = _uiState.value.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = "ไม่สามารถดึงลิงก์เสียงได้ (ตรวจสอบอินเทอร์เน็ตหรือลองเลือกเพลงอื่น)"
                )
                return@launch
            }

            val controller = awaitMediaController()
            if (controller == null) {
                _uiState.value = _uiState.value.copy(
                    isBuffering = false,
                    errorMessage = "ระบบเล่นเพลงกำลังเริ่มต้น กรุณากดเล่นอีกครั้ง"
                )
                return@launch
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setArtworkUri(
                    if (track.thumbnailUrl.isNotBlank()) Uri.parse(track.thumbnailUrl) else null
                )
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(track.videoId)
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun togglePlayPause() {
        mediaController?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                _uiState.value.currentTrack?.let { playTrack(it, _uiState.value.queue) }
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    fun playNextInQueue() {
        val state = _uiState.value
        val current = state.currentTrack ?: return
        if (state.repeatMode == Player.REPEAT_MODE_ONE) {
            seekTo(0L)
            mediaController?.play()
            return
        }
        val list = state.queue
        if (list.isEmpty()) return

        val currentIndex = list.indexOfFirst { it.videoId == current.videoId }
        val nextTrack = if (state.shuffleEnabled && list.size > 1) {
            list.filter { it.videoId != current.videoId }.random()
        } else if (currentIndex in 0 until list.lastIndex) {
            list[currentIndex + 1]
        } else if (state.repeatMode == Player.REPEAT_MODE_ALL) {
            list.first()
        } else {
            return
        }
        playTrack(nextTrack, list)
    }

    fun playPreviousInQueue() {
        val state = _uiState.value
        val current = state.currentTrack ?: return
        if (state.positionMs > 4000L) {
            seekTo(0L)
            return
        }
        val list = state.queue
        val currentIndex = list.indexOfFirst { it.videoId == current.videoId }
        if (currentIndex > 0) {
            playTrack(list[currentIndex - 1], list)
        } else {
            seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val next = !_uiState.value.shuffleEnabled
        _uiState.value = _uiState.value.copy(shuffleEnabled = next)
    }

    fun cycleRepeatMode() {
        val nextMode = when (_uiState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _uiState.value = _uiState.value.copy(repeatMode = nextMode)
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _uiState.value = _uiState.value.copy(sleepTimerRemainingSec = null)
            return
        }
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                _uiState.value = _uiState.value.copy(sleepTimerRemainingSec = remaining)
                delay(1000L)
                remaining--
            }
            mediaController?.pause()
            _uiState.value = _uiState.value.copy(sleepTimerRemainingSec = null)
        }
    }
}
