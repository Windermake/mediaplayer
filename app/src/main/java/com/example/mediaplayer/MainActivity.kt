package com.example.mediaplayer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.mediaplayer.ui.theme.MediaplayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaplayerTheme {
                PlayerApp()
            }
        }
    }
}

@Composable
private fun PlayerApp() {
    val context = LocalContext.current
    val player = remember(context) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
            playWhenReady = true
        }
    }
    var playlist by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var durationMs by remember { mutableLongStateOf(C.TIME_UNSET) }
    var hasVideo by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(0) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPositionMs by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }

    val openDocument = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            val item = context.contentResolver.toPlaylistItem(uri)
            playlist = listOf(item)
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
        }
    }
    val openMultipleDocuments = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val items = uris.map { context.contentResolver.toPlaylistItem(it) }
            playlist = items
            currentIndex = 0
            player.setMediaItems(items.map { MediaItem.fromUri(it.uri) })
            player.prepare()
            player.play()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                durationMs = player.duration
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                durationMs = player.duration
                currentPositionMs = player.currentPosition
            }

            override fun onEvents(player: Player, events: Player.Events) {
                durationMs = player.duration
                currentPositionMs = player.currentPosition
                hasVideo = player.videoSize.width > 0 && player.videoSize.height > 0
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                isPlaying = player.isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, isSeeking) {
        while (true) {
            if (!isSeeking) {
                currentPositionMs = player.currentPosition
                durationMs = player.duration
                sliderPositionMs = player.currentPosition.toFloat()
            }
            delay(500)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MediaPlayerScreen(
                player = player,
                playlist = playlist,
                currentIndex = currentIndex,
                playbackState = playbackState,
                durationMs = durationMs,
                currentPositionMs = currentPositionMs,
                sliderPositionMs = sliderPositionMs,
                isPlaying = isPlaying,
                hasVideo = hasVideo,
                onOpenFile = { openDocument.launch(arrayOf("audio/*", "video/*")) },
                onOpenFiles = { openMultipleDocuments.launch(arrayOf("audio/*", "video/*")) },
                onSeekStart = { isSeeking = true },
                onSeekChange = { sliderPositionMs = it },
                onSeekFinish = {
                    player.seekTo(it.toLong())
                    currentPositionMs = it.toLong()
                    sliderPositionMs = it
                    isSeeking = false
                },
                onSeekBack = { player.seekBack() },
                onTogglePlayPause = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
                onSeekForward = { player.seekForward() },
                onSelectTrack = { index ->
                    currentIndex = index
                    player.seekTo(index, 0L)
                    player.play()
                }
            )
        }
    }
}

@Composable
private fun MediaPlayerScreen(
    player: ExoPlayer,
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    playbackState: Int,
    durationMs: Long,
    currentPositionMs: Long,
    sliderPositionMs: Float,
    isPlaying: Boolean,
    hasVideo: Boolean,
    onOpenFile: () -> Unit,
    onOpenFiles: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekFinish: (Float) -> Unit,
    onSeekBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSelectTrack: (Int) -> Unit
) {
    val currentItem = playlist.getOrNull(currentIndex)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Android Media Player",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.popular_formats),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (currentItem == null) {
                    EmptyPlayerState(
                        onOpenFile = onOpenFile,
                        onOpenFiles = onOpenFiles
                    )
                } else {
                    VideoSurface(player = player, hasVideo = hasVideo)
                    Spacer(modifier = Modifier.height(16.dp))
                    SeekBarSection(
                        durationMs = durationMs,
                        currentPositionMs = currentPositionMs,
                        sliderPositionMs = sliderPositionMs,
                        onSeekStart = onSeekStart,
                        onSeekChange = onSeekChange,
                        onSeekFinish = onSeekFinish
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PlaybackControlsSection(
                        isPlaying = isPlaying,
                        onSeekBack = onSeekBack,
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekForward = onSeekForward
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onOpenFile) {
                            Text(text = stringResource(R.string.open_other_media))
                        }
                        Button(onClick = onOpenFiles) {
                            Text(text = stringResource(R.string.open_playlist))
                        }
                    }
                }
            }
        }

        if (currentItem != null) {
            PlaybackInfoCard(
                fileName = currentItem.name,
                playbackState = playbackState,
                durationMs = durationMs,
                hasVideo = hasVideo,
                currentPositionMs = currentPositionMs
            )
            PlaylistCard(
                playlist = playlist,
                currentIndex = currentIndex,
                onSelectTrack = onSelectTrack
            )
        }
    }
}

@Composable
private fun EmptyPlayerState(
    onOpenFile: () -> Unit,
    onOpenFiles: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.no_media_selected),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onOpenFile) {
            Text(text = stringResource(R.string.open_media))
        }
        Button(onClick = onOpenFiles) {
            Text(text = stringResource(R.string.open_playlist))
        }
    }
}

@Composable
private fun SeekBarSection(
    durationMs: Long,
    currentPositionMs: Long,
    sliderPositionMs: Float,
    onSeekStart: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekFinish: (Float) -> Unit
) {
    val safeDuration = if (durationMs > 0 && durationMs != C.TIME_UNSET) durationMs.toFloat() else 1f
    Slider(
        value = sliderPositionMs.coerceIn(0f, safeDuration),
        onValueChange = {
            onSeekStart()
            onSeekChange(it)
        },
        onValueChangeFinished = {
            onSeekFinish(sliderPositionMs.coerceIn(0f, safeDuration))
        },
        valueRange = 0f..safeDuration
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = currentPositionMs.formatDuration(),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = durationMs.formatDuration(),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun PlaybackControlsSection(
    isPlaying: Boolean,
    onSeekBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onSeekBack,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = stringResource(R.string.seek_back))
        }
        Button(
            onClick = onTogglePlayPause,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(
                    if (isPlaying) R.string.pause_media else R.string.play_media
                )
            )
        }
        Button(
            onClick = onSeekForward,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = stringResource(R.string.seek_forward))
        }
    }
}

@Composable
private fun VideoSurface(player: ExoPlayer, hasVideo: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(
                color = Color.Black,
                shape = RoundedCornerShape(20.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = true
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.defaultArtwork = null
            }
        )

        if (!hasVideo) {
            Text(
                text = stringResource(R.string.audio_mode),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun PlaybackInfoCard(
    fileName: String,
    playbackState: Int,
    durationMs: Long,
    hasVideo: Boolean,
    currentPositionMs: Long
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InfoRow(label = stringResource(R.string.selected_file), value = fileName)
            InfoRow(
                label = stringResource(R.string.content_type),
                value = stringResource(if (hasVideo) R.string.video_mode else R.string.audio_mode)
            )
            InfoRow(
                label = stringResource(R.string.playback_status),
                value = playbackState.toReadableStatus()
            )
            InfoRow(
                label = stringResource(R.string.current_position),
                value = currentPositionMs.formatDuration()
            )
            InfoRow(
                label = stringResource(R.string.duration),
                value = durationMs.formatDuration()
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    onSelectTrack: (Int) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.playlist_title, playlist.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            playlist.forEachIndexed { index, item ->
                val isCurrent = index == currentIndex
                Button(
                    onClick = { onSelectTrack(index) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isCurrent) {
                            stringResource(R.string.current_track_prefix, index + 1, item.name)
                        } else {
                            "${index + 1}. ${item.name}"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun Int.toReadableStatus(): String = when (this) {
    Player.STATE_BUFFERING -> "Загрузка"
    Player.STATE_READY -> "Готов"
    Player.STATE_ENDED -> "Завершено"
    else -> "Ожидание"
}

private fun Long.formatDuration(): String {
    if (this <= 0L || this == C.TIME_UNSET) return "Неизвестно"
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private data class PlaylistItem(
    val uri: Uri,
    val name: String
)

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
