package com.example.mediaplayer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player

data class PlaylistItem(
    val uri: Uri,
    val name: String
)

data class PlayerUiState(
    val playlist: List<PlaylistItem> = emptyList(),
    val currentIndex: Int = 0,
    val playbackState: Int = Player.STATE_IDLE,
    val durationMs: Long = C.TIME_UNSET,
    val currentPositionMs: Long = 0L,
    val sliderPositionMs: Float = 0f,
    val isSeeking: Boolean = false,
    val isPlaying: Boolean = false,
    val hasVideo: Boolean = false,
    val isControllerReady: Boolean = false,
    val isFullscreen: Boolean = false,
    val errorMessage: String? = null
) {
    val currentItem: PlaylistItem?
        get() = playlist.getOrNull(currentIndex)
}
