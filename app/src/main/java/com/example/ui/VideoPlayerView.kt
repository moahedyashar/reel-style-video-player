package com.example.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.LocalVideo
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    video: LocalVideo,
    isActive: Boolean,
    resizeMode: Int,
    playbackSpeed: Float,
    autoAdvance: Boolean,
    isImmersiveMode: Boolean,
    isMuted: Boolean,
    isAppInForeground: Boolean,
    modifier: Modifier = Modifier,
    onVideoSettled: () -> Unit = {},
    onVideoEnded: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPlayerReady by remember { mutableStateOf(false) }
    var isVideoPlaying by remember { mutableStateOf(isActive) }
    var showPauseOverlay by remember { mutableStateOf(false) }

    // Live Playback position states
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }

    // Synchronize play state with pager focus
    LaunchedEffect(isActive) {
        isVideoPlaying = isActive
        if (isActive) {
            onVideoSettled()
        }
    }

    // Initialize ExoPlayer once per view lifecycle with custom low-buffering configs
    val exoPlayer = remember(context) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000, // minBufferMs: Min buffer required before starting playback (set aggressively low for instant TikTok behavior)
                3000, // maxBufferMs: Max buffered media duration (capped low for rapid caching)
                150,  // bufferForPlaybackMs: Extremely low buffer size needed to start rendering frames instantly
                250   // bufferForPlaybackAfterRebufferMs: Fast resumption buffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isPlayerReady = (state == Player.STATE_READY || state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_ENDED) {
                            onVideoEnded()
                        }
                    }
                })
            }
    }

    // Explicitly release the player when this Composable is fully removed from composition
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    // React to speed change
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // React to auto-advance behavior
    LaunchedEffect(autoAdvance) {
        exoPlayer.repeatMode = if (autoAdvance) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
    }

    // React to volume / mute changes
    LaunchedEffect(isMuted, isAppInForeground) {
        exoPlayer.volume = if (isMuted || !isAppInForeground) 0f else 1f
    }

    // Manage Host Device Lifecycle events - kills audio emission and pauses instantly on background
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.playWhenReady = false
                    exoPlayer.volume = 0f // Make it completely silent
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isActive && isAppInForeground) {
                        exoPlayer.playWhenReady = isVideoPlaying
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    exoPlayer.playWhenReady = false
                    exoPlayer.volume = 0f
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Manage Media source lifecycle without prematurely releasing the player
    DisposableEffect(exoPlayer, video.uriString) {
        val mediaItem = MediaItem.fromUri(Uri.parse(video.uriString))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // React directly to pause/play requests
    LaunchedEffect(isVideoPlaying, isAppInForeground, isActive) {
        exoPlayer.playWhenReady = isVideoPlaying && isAppInForeground && isActive
    }

    // Position progress tracking loop
    LaunchedEffect(isVideoPlaying, isPlayerReady, isDraggingSlider) {
        if (isVideoPlaying && isPlayerReady && !isDraggingSlider) {
            while (true) {
                currentPosition = exoPlayer.currentPosition
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                delay(250)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isVideoPlaying = !isVideoPlaying
                showPauseOverlay = !isVideoPlaying
            }
    ) {
        // Platform ViewInterop video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
                view.resizeMode = resizeMode
            },
            onRelease = { view ->
                view.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator
        if (!isPlayerReady) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        // Tap pause overlay
        AnimatedVisibility(
            visible = showPauseOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Paused",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // CUSTOM PLAYBACK PROGRESS BAR (Hides completely in Immersive clean view)
        AnimatedVisibility(
            visible = !isImmersiveMode && isPlayerReady && totalDuration > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 120.dp, start = 16.dp, end = 16.dp) // Anchored above the title text
        ) {
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(totalDuration),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { newValue ->
                        isDraggingSlider = true
                        currentPosition = newValue.toLong()
                    },
                    onValueChangeFinished = {
                        exoPlayer.seekTo(currentPosition)
                        isDraggingSlider = false
                    },
                    valueRange = 0f..totalDuration.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                        thumbColor = Color.White
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}
