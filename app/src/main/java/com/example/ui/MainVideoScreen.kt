package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LocalVideo
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MainVideoScreen(
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val playlist by viewModel.playlist.collectAsState()
    val scannedVideos by viewModel.scannedVideos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val folderUri by viewModel.folderUri.collectAsState()

    // Config options from StateFlow
    val playlistLimit by viewModel.playlistLimit.collectAsState()
    val filterWatched by viewModel.filterWatched.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isAppInForeground by viewModel.isAppInForeground.collectAsState()
    val videoScaleMode by viewModel.videoScaleMode.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val autoAdvance by viewModel.autoAdvance.collectAsState()
    val isImmersiveMode by viewModel.isImmersiveMode.collectAsState()

    var showLimitsMenu by remember { mutableStateOf(false) }

    // Selector for SAF folder import
    val openDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importFolder(context, uri)
            }
        }
    )

    // Vertical custom view pager
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { playlist.size }
    )

    // Sync scrolling events from ViewModel
    LaunchedEffect(key1 = playlist.size) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is VideoUiEvent.ScrollToPage -> {
                    if (event.index in playlist.indices) {
                        try {
                            pagerState.animateScrollToPage(event.index)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                is VideoUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Recover previous storage path on app load
    LaunchedEffect(key1 = Unit) {
        viewModel.loadPersistedFolder(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C)) // Ultra dark backdrop
    ) {
        if (playlist.isEmpty()) {
            // Setup & Folder selection screen if no videos are present
            EmptyStateLayout(
                isLoading = isLoading,
                playlistLimit = playlistLimit,
                onLimitChanged = { limit -> viewModel.setPlaylistLimit(context, limit) },
                onImportClicked = { openDirectoryLauncher.launch(null) }
            )
        } else {
            // Horizontal/vertical TikTok dynamic pager loop
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { pageIndex ->
                if (pageIndex in playlist.indices) {
                    val video = playlist[pageIndex]
                    val isActive = pagerState.currentPage == pageIndex

                    Box(modifier = Modifier.fillMaxSize()) {
                        // IMMERSIVE FULL-WIDTH PLAYBACK VIEW WITH ROTATING PRE-BUFFER
                        VideoPlayerView(
                            video = video,
                            isActive = isActive,
                            resizeMode = videoScaleMode,
                            playbackSpeed = playbackSpeed,
                            autoAdvance = autoAdvance,
                            isImmersiveMode = isImmersiveMode,
                            isMuted = isMuted,
                            isAppInForeground = isAppInForeground,
                            onVideoSettled = {
                                viewModel.markAsWatched(video)
                            },
                            onVideoEnded = {
                                if (autoAdvance) {
                                    val nextPage = pagerState.currentPage + 1
                                    if (nextPage < playlist.size) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(nextPage)
                                        }
                                    } else {
                                        Toast.makeText(context, "Completed smart session!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )

                        // -------------------------------------------------------------
                        // HIGH CONTRAST SCRIM GRADIANS (Hidden when in immersive view to protect video frame)
                        // -------------------------------------------------------------
                        AnimatedVisibility(
                            visible = !isImmersiveMode,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Top shadow scrim for readability
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.82f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )

                                // Bottom shadow scrim for info typography readability
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.85f)
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        // -------------------------------------------------------------
                        // MINI TRANSLUCENT OVERLAY FLOATER FOR EXITING IMMERSIVE VIEW
                        // -------------------------------------------------------------
                        AnimatedVisibility(
                            visible = isImmersiveMode,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.setImmersiveMode(false) },
                                modifier = Modifier
                                    .testTag("exit_immersive_btn")
                                    .background(Color.Black.copy(alpha = 0.65f), shape = CircleShape)
                                    .size(44.dp),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Exit Immersive Show Controls",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // -------------------------------------------------------------
                        // TOP HEADER NAVIGATION (Hidden when in immersive view)
                        // -------------------------------------------------------------
                        AnimatedVisibility(
                            visible = !isImmersiveMode,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Vertical Smart Player",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Scrolled ${pageIndex + 1}/${playlist.size} (Limit: ${if (playlistLimit > 0) playlistLimit else "All"})",
                                        color = Color.White.copy(alpha = 0.73f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Playlist limit menu trigger
                                Box {
                                    IconButton(
                                        onClick = { showLimitsMenu = true },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.White.copy(alpha = 0.12f), shape = CircleShape)
                                    ) {
                                        Text(
                                            text = if (playlistLimit > 0) "${playlistLimit}x" else "All",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showLimitsMenu,
                                        onDismissRequest = { showLimitsMenu = false },
                                        modifier = Modifier.background(Color(0xFF202020))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("All videos from folder", color = Color.White) },
                                            onClick = {
                                                viewModel.setPlaylistLimit(context, -1)
                                                showLimitsMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Shuffle Subset of 3", color = Color.White) },
                                            onClick = {
                                                viewModel.setPlaylistLimit(context, 3)
                                                showLimitsMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Shuffle Subset of 5", color = Color.White) },
                                            onClick = {
                                                viewModel.setPlaylistLimit(context, 5)
                                                showLimitsMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Shuffle Subset of 15", color = Color.White) },
                                            onClick = {
                                                viewModel.setPlaylistLimit(context, 15)
                                                showLimitsMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Shuffle Subset of 30", color = Color.White) },
                                            onClick = {
                                                viewModel.setPlaylistLimit(context, 30)
                                                showLimitsMenu = false
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Reload and force smart shuffle
                                IconButton(
                                    onClick = { viewModel.forceReshuffle(context) },
                                    modifier = Modifier
                                        .testTag("reshuffle_button")
                                        .size(40.dp)
                                        .background(Color.White.copy(alpha = 0.12f), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Force smart reshuffle",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Import another folder via SAF
                                IconButton(
                                    onClick = { openDirectoryLauncher.launch(null) },
                                    modifier = Modifier
                                        .testTag("choose_folder_button")
                                        .size(40.dp)
                                        .background(Color.White.copy(alpha = 0.12f), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Choose directory",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        // -------------------------------------------------------------
                        // BOTTOM METADATA INFORMATION (Hidden when in immersive view)
                        // -------------------------------------------------------------
                        AnimatedVisibility(
                            visible = !isImmersiveMode,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(0.72f)
                                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Column {
                                Text(
                                    text = video.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Color.White.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = formatSize(video.size),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (video.mimeType.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = video.mimeType.substringAfter("/").uppercase(Locale.US),
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // -------------------------------------------------------------
                        // RIGHT EXTRA OPTIONS PANEL (Hidden when in immersive view)
                        // -------------------------------------------------------------
                        AnimatedVisibility(
                            visible = !isImmersiveMode,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .fillMaxHeight(0.65f)
                                .padding(end = 16.dp, bottom = 24.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Bottom,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                // 1. IMMERSIVE / CLEAN VIEW MODE TOGGER
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = { viewModel.setImmersiveMode(true) },
                                        modifier = Modifier
                                            .testTag("immersive_btn")
                                            .size(48.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VisibilityOff,
                                            contentDescription = "Immersive Mode",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = "Clean View",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )
                                }

                                // 1.1 MUTE / UNMUTE TRIGGER CONTROL
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = { viewModel.setMuted(context, !isMuted) },
                                        modifier = Modifier
                                            .testTag("mute_btn")
                                            .size(48.dp)
                                            .background(
                                                if (isMuted) MaterialTheme.colorScheme.error.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f),
                                                shape = CircleShape
                                            ),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                            contentDescription = "Mute/Unmute audio",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isMuted) "Muted" else "Sound On",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )
                                }

                                // 1.2 SMART HIDE WATCHED OR SHUFFLE ALL DIRECTORY VIDEOS
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = { viewModel.setFilterWatched(context, !filterWatched) },
                                        modifier = Modifier
                                            .testTag("filter_on_watched_btn")
                                            .size(48.dp)
                                            .background(
                                                if (filterWatched) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f),
                                                shape = CircleShape
                                            ),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "Filter Watched Videos",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = if (filterWatched) "Smart Hide" else "Shuffle All",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )
                                }

                                // 2. VIDEO SCALE MODE (ZOOM COVERS vs WHOLE VIDEO VISIBLE FIT)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            // Toggle Scale mode: 3 is ZOOM, 0 is FIT (defined in Android Media3 AspectRatioFrameLayout)
                                            val nextScaleMode = if (videoScaleMode == 3) 0 else 3
                                            viewModel.setVideoScaleMode(nextScaleMode)
                                        },
                                        modifier = Modifier
                                            .testTag("scale_mode_btn")
                                            .size(48.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = "Aspect Ratio Fit or Cover",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = if (videoScaleMode == 3) "Cover Fill" else "Whole Fit",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )
                                }

                                // 3. PLAYBACK SPEED SELECTOR (1.0x -> 1.5x -> 2.0x -> 0.75x)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            val nextSpeed = when (playbackSpeed) {
                                                1.0f -> 1.25f
                                                1.25f -> 1.5f
                                                1.5f -> 2.0f
                                                2.0f -> 0.75f
                                                else -> 1.0f
                                            }
                                            viewModel.setPlaybackSpeed(nextSpeed)
                                        },
                                        modifier = Modifier
                                            .testTag("playback_speed_btn")
                                            .size(48.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = "Playback Speed control",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = "${playbackSpeed}x Speed",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )
                                }

                                // 4. AUTO ADVANCE TOGGLE
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            viewModel.setAutoAdvance(!autoAdvance)
                                        },
                                        modifier = Modifier
                                            .testTag("auto_advance_btn")
                                            .size(48.dp)
                                            .background(
                                                if (autoAdvance) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f),
                                                shape = CircleShape
                                            ),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Autorenew,
                                            contentDescription = "Auto Advance",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = if (autoAdvance) "Auto Play" else "Loop One",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )
                                }

                                // 5. WATCH RESET / STATS MANAGER ACTION
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = { viewModel.resetHistoryAndReShuffle(context) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Clear History Reset",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = "Reset History",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                    )
                                }

                                // 6. HARD PHYSICAL DELETE BUTTON ACTION OVERLAY
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteVideo(context, video, pageIndex)
                                        },
                                        modifier = Modifier
                                            .testTag("delete_button")
                                            .size(48.dp)
                                            .background(
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                                shape = CircleShape
                                            ),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Video physical file",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = "Delete File",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateLayout(
    isLoading: Boolean,
    playlistLimit: Int,
    onLimitChanged: (Int) -> Unit,
    onImportClicked: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scanning & shuffling files...",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Folder icon graphics
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color.White.copy(alpha = 0.05f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder Import symbol",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Vertical Smart Player",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Select a local folder. You can restrict the playlist size or load everything. Watched tracks are stored in Room Database and won't repeat until the folder is fully completed.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PLAYLIST SIZING FILTER SECTION (Does NOT load all videos of the folder option)
                Text(
                    text = "PLAYLIST SESSION TARGET LIMIT:",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val limits = listOf(-1, 3, 5, 15, 30)
                    limits.forEach { limit ->
                        val limitLabel = if (limit == -1) "All" else "$limit"
                        val isSelected = playlistLimit == limit

                        Button(
                            onClick = { onLimitChanged(limit) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                                contentColor = if (isSelected) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(32.dp)
                        ) {
                            Text(text = limitLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onImportClicked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(32.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    modifier = Modifier.testTag("choose_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Scan Local Directory",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.04f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Smart Loop Mandates Integrated",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "✔ Whole Fit vs Crop aspect toggles for full video views\n" +
                                    "✔ Immersive Clean Mode to hide all overlays and buttons\n" +
                                    "✔ Configurable video playlist sessions (limits folder loading)\n" +
                                    "✔ Built-in seeking progress timeline\n" +
                                    "✔ Configurable 0.75x–2.0x playback speed & auto play advance",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups !in units.indices) return "$bytes B"
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
