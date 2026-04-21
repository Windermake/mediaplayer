package com.example.mediaplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.activity.viewModels
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.example.mediaplayer.ui.theme.MediaplayerTheme

class MainActivity : ComponentActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaplayerTheme {
                PlayerApp(viewModel = playerViewModel)
            }
        }
    }
}

@Composable
private fun PlayerApp(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val openDocument = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.openSingle(uri)
        }
    }
    val openMultipleDocuments = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        viewModel.openPlaylist(uris)
    }
    val fullscreenEnabled = uiState.isFullscreen && uiState.currentItem != null

    ApplyFullscreenMode(enabled = fullscreenEnabled)

    if (fullscreenEnabled) {
        FullscreenPlayerScreen(
            player = viewModel.player,
            uiState = uiState,
            onSeekStart = viewModel::onSeekStart,
            onSeekChange = viewModel::onSeekChange,
            onSeekFinish = viewModel::onSeekFinish,
            onSeekBack = viewModel::seekBack,
            onTogglePlayPause = viewModel::togglePlayPause,
            onSeekForward = viewModel::seekForward,
            onExitFullscreen = viewModel::exitFullscreen
        )
    } else {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MediaPlayerScreen(
                    player = viewModel.player,
                    uiState = uiState,
                    onOpenFile = { openDocument.launch(arrayOf("audio/*", "video/*")) },
                    onOpenFiles = { openMultipleDocuments.launch(arrayOf("audio/*", "video/*")) },
                    onSeekStart = viewModel::onSeekStart,
                    onSeekChange = viewModel::onSeekChange,
                    onSeekFinish = viewModel::onSeekFinish,
                    onSeekBack = viewModel::seekBack,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSeekForward = viewModel::seekForward,
                    onToggleFullscreen = viewModel::toggleFullscreen,
                    onSelectTrack = viewModel::selectTrack
                )
            }
        }
    }
}

@Composable
private fun MediaPlayerScreen(
    player: Player?,
    uiState: PlayerUiState,
    onOpenFile: () -> Unit,
    onOpenFiles: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekFinish: (Float) -> Unit,
    onSeekBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSelectTrack: (Int) -> Unit
) {
    val currentItem = uiState.currentItem
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

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (currentItem == null) {
                    EmptyPlayerState(
                        isControllerReady = uiState.isControllerReady,
                        onOpenFile = onOpenFile,
                        onOpenFiles = onOpenFiles
                    )
                } else {
                    InlinePlayerSurface(player = player, hasVideo = uiState.hasVideo)
                    Spacer(modifier = Modifier.height(16.dp))
                    SeekBarSection(
                        durationMs = uiState.durationMs,
                        currentPositionMs = uiState.currentPositionMs,
                        sliderPositionMs = uiState.sliderPositionMs,
                        onSeekStart = onSeekStart,
                        onSeekChange = onSeekChange,
                        onSeekFinish = onSeekFinish
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PlaybackControlsSection(
                        isPlaying = uiState.isPlaying,
                        onSeekBack = onSeekBack,
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekForward = onSeekForward
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FileActionsSection(
                        hasVideo = uiState.hasVideo,
                        onOpenFile = onOpenFile,
                        onOpenFiles = onOpenFiles,
                        onToggleFullscreen = onToggleFullscreen
                    )
                }
            }
        }

        if (currentItem != null) {
            PlaybackInfoCard(
                fileName = currentItem.name,
                playbackState = uiState.playbackState,
                durationMs = uiState.durationMs,
                hasVideo = uiState.hasVideo,
                currentPositionMs = uiState.currentPositionMs
            )
            PlaylistCard(
                playlist = uiState.playlist,
                currentIndex = uiState.currentIndex,
                onSelectTrack = onSelectTrack
            )
        }
    }
}

@Composable
private fun FullscreenPlayerScreen(
    player: Player?,
    uiState: PlayerUiState,
    onSeekStart: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekFinish: (Float) -> Unit,
    onSeekBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        PlayerSurface(
            player = player,
            hasVideo = uiState.hasVideo,
            modifier = Modifier.fillMaxSize(),
            useRoundedCorners = false
        )

        Text(
            text = uiState.currentItem?.name.orEmpty(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.68f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SeekBarSection(
                durationMs = uiState.durationMs,
                currentPositionMs = uiState.currentPositionMs,
                sliderPositionMs = uiState.sliderPositionMs,
                onSeekStart = onSeekStart,
                onSeekChange = onSeekChange,
                onSeekFinish = onSeekFinish
            )
            PlaybackControlsSection(
                isPlaying = uiState.isPlaying,
                onSeekBack = onSeekBack,
                onTogglePlayPause = onTogglePlayPause,
                onSeekForward = onSeekForward
            )
            Button(
                onClick = onExitFullscreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.exit_fullscreen))
            }
        }
    }
}

@Composable
private fun EmptyPlayerState(
    isControllerReady: Boolean,
    onOpenFile: () -> Unit,
    onOpenFiles: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (isControllerReady) {
                stringResource(R.string.no_media_selected)
            } else {
                stringResource(R.string.player_loading)
            },
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
private fun FileActionsSection(
    hasVideo: Boolean,
    onOpenFile: () -> Unit,
    onOpenFiles: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onOpenFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.open_other_media))
        }
        Button(
            onClick = onOpenFiles,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.open_playlist))
        }
        if (hasVideo) {
            Button(
                onClick = onToggleFullscreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.enter_fullscreen))
            }
        }
    }
}

@Composable
private fun InlinePlayerSurface(player: Player?, hasVideo: Boolean) {
    PlayerSurface(
        player = player,
        hasVideo = hasVideo,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        useRoundedCorners = true
    )
}

@Composable
private fun PlayerSurface(
    player: Player?,
    hasVideo: Boolean,
    modifier: Modifier = Modifier,
    useRoundedCorners: Boolean
) {
    val shape = if (useRoundedCorners) RoundedCornerShape(20.dp) else RoundedCornerShape(0.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (player == null) {
            Text(
                text = stringResource(R.string.player_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
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
        }

        if (player != null && !hasVideo) {
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

@Composable
private fun ApplyFullscreenMode(enabled: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = context.findActivity()?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, !enabled)
            val controller = WindowCompat.getInsetsController(window, view)
            if (enabled) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
