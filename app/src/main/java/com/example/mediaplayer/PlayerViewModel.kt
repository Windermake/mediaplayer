package com.example.mediaplayer

import android.app.Application
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingPlaylist: List<PlaylistItem>? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val player: Player?
        get() = mediaController

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromPlayer()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromPlayer()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update { state ->
                state.copy(errorMessage = error.localizedMessage ?: error.message)
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer(player)
        }
    }

    init {
        connectController()
        startProgressUpdates()
    }

    fun openSingle(uri: Uri) {
        openPlaylist(listOf(uri))
    }

    fun openPlaylist(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val items = uris.map { resolver.toPlaylistItem(it) }
        pendingPlaylist = items
        _uiState.update { state ->
            state.copy(
                playlist = items,
                currentIndex = 0,
                playbackState = Player.STATE_IDLE,
                durationMs = C.TIME_UNSET,
                currentPositionMs = 0L,
                sliderPositionMs = 0f,
                isSeeking = false,
                hasVideo = false,
                errorMessage = null
            )
        }
        mediaController?.let { loadPlaylist(it, items) }
    }

    fun onSeekStart() {
        _uiState.update { state -> state.copy(isSeeking = true) }
    }

    fun onSeekChange(positionMs: Float) {
        _uiState.update { state -> state.copy(sliderPositionMs = positionMs) }
    }

    fun onSeekFinish(positionMs: Float) {
        val targetPosition = positionMs.toLong()
        mediaController?.seekTo(targetPosition)
        _uiState.update { state ->
            state.copy(
                currentPositionMs = targetPosition,
                sliderPositionMs = positionMs,
                isSeeking = false
            )
        }
    }

    fun seekBack() {
        mediaController?.seekBack()
        syncFromPlayer()
    }

    fun seekForward() {
        mediaController?.seekForward()
        syncFromPlayer()
    }

    fun togglePlayPause() {
        val player = mediaController ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
        syncFromPlayer(player)
    }

    fun selectTrack(index: Int) {
        val playlist = _uiState.value.playlist
        if (index !in playlist.indices) return
        mediaController?.apply {
            seekTo(index, 0L)
            play()
        }
        _uiState.update { state ->
            state.copy(
                currentIndex = index,
                currentPositionMs = 0L,
                sliderPositionMs = 0f,
                isSeeking = false
            )
        }
    }

    fun toggleFullscreen() {
        _uiState.update { state -> state.copy(isFullscreen = !state.isFullscreen) }
    }

    fun exitFullscreen() {
        _uiState.update { state -> state.copy(isFullscreen = false) }
    }

    private fun connectController() {
        val app = getApplication<Application>()
        val sessionToken = SessionToken(
            app,
            ComponentName(app, PlaybackService::class.java)
        )
        val future = MediaController.Builder(app, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(playerListener)
                    pendingPlaylist?.let { loadPlaylist(controller, it) }
                    syncFromPlayer(controller)
                    _uiState.update { state ->
                        state.copy(isControllerReady = true, errorMessage = null)
                    }
                }.onFailure { error ->
                    _uiState.update { state ->
                        state.copy(errorMessage = error.localizedMessage ?: error.message)
                    }
                }
            },
            ContextCompat.getMainExecutor(app)
        )
    }

    private fun loadPlaylist(player: Player, items: List<PlaylistItem>) {
        player.setMediaItems(items.map { it.toMediaItem() })
        player.prepare()
        player.play()
        pendingPlaylist = null
        syncFromPlayer(player)
    }

    private fun startProgressUpdates() {
        viewModelScope.launch {
            while (isActive) {
                if (!_uiState.value.isSeeking) {
                    syncFromPlayer()
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun syncFromPlayer(player: Player? = mediaController) {
        if (player == null) return
        _uiState.update { state ->
            val currentIndex = if (state.playlist.isEmpty()) {
                0
            } else {
                player.currentMediaItemIndex.coerceIn(0, state.playlist.lastIndex)
            }
            val position = player.currentPosition
            state.copy(
                currentIndex = currentIndex,
                playbackState = player.playbackState,
                durationMs = player.duration,
                currentPositionMs = position,
                sliderPositionMs = if (state.isSeeking) state.sliderPositionMs else position.toFloat(),
                isPlaying = player.isPlaying,
                hasVideo = player.videoSize.width > 0 && player.videoSize.height > 0
            )
        }
    }

    override fun onCleared() {
        mediaController?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        mediaController = null
        controllerFuture = null
        super.onCleared()
    }

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
}

private fun PlaylistItem.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(uri.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .build()
        )
        .build()
}

private fun ContentResolver.resolveDisplayName(uri: Uri): String {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columnIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(columnIndex)
        }
    }
    return uri.lastPathSegment ?: uri.toString()
}

private fun ContentResolver.toPlaylistItem(uri: Uri): PlaylistItem {
    takePersistableReadPermissionSafely(uri)
    return PlaylistItem(
        uri = uri,
        name = resolveDisplayName(uri)
    )
}

private fun ContentResolver.takePersistableReadPermissionSafely(uri: Uri) {
    runCatching {
        takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
