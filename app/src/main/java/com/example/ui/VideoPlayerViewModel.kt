package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LocalVideo
import com.example.data.VideoRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class VideoUiEvent {
    data class ScrollToPage(val index: Int) : VideoUiEvent()
    data class ShowToast(val message: String) : VideoUiEvent()
}

class VideoPlayerViewModel(private val repository: VideoRepository) : ViewModel() {

    private val _folderUri = MutableStateFlow<String?>(null)
    val folderUri: StateFlow<String?> = _folderUri.asStateFlow()

    private val _scannedVideos = MutableStateFlow<List<LocalVideo>>(emptyList())
    val scannedVideos: StateFlow<List<LocalVideo>> = _scannedVideos.asStateFlow()

    private val _playlist = MutableStateFlow<List<LocalVideo>>(emptyList())
    val playlist: StateFlow<List<LocalVideo>> = _playlist.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _eventFlow = MutableSharedFlow<VideoUiEvent>()
    val eventFlow: SharedFlow<VideoUiEvent> = _eventFlow.asSharedFlow()

    // NEW OPTION STATES:
    // Limit of videos in current scroll playlist session (-1 implies All Videos)
    private val _playlistLimit = MutableStateFlow(-1)
    val playlistLimit: StateFlow<Int> = _playlistLimit.asStateFlow()

    // Filter out already watched videos from the playlist
    private val _filterWatched = MutableStateFlow(false)
    val filterWatched: StateFlow<Boolean> = _filterWatched.asStateFlow()

    // Persistent volume / mute state (Material 3 standard)
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Live configurable playback options (Material 3)
    private val _videoScaleMode = MutableStateFlow(3) // Default: Crop & ZOOM (3), otherwise Fit Letterbox (0)
    val videoScaleMode: StateFlow<Int> = _videoScaleMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _autoAdvance = MutableStateFlow(true)
    val autoAdvance: StateFlow<Boolean> = _autoAdvance.asStateFlow()

    private val _isImmersiveMode = MutableStateFlow(false)
    val isImmersiveMode: StateFlow<Boolean> = _isImmersiveMode.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private var currentFolderUriString: String? = null

    fun loadPersistedFolder(context: Context) {
        val prefs = context.getSharedPreferences("video_player_prefs", Context.MODE_PRIVATE)
        _playlistLimit.value = prefs.getInt("playlist_limit", -1)
        _filterWatched.value = prefs.getBoolean("filter_watched", false)
        _isMuted.value = prefs.getBoolean("is_muted", false)
        
        val persistedUriStr = prefs.getString("folder_uri", null)
        if (persistedUriStr != null) {
            if (hasPersistedPermission(context, persistedUriStr)) {
                _folderUri.value = persistedUriStr
                currentFolderUriString = persistedUriStr
                scanAndGeneratePlaylist(context, persistedUriStr)
            } else {
                prefs.edit().remove("folder_uri").apply()
            }
        }
    }

    private fun hasPersistedPermission(context: Context, uriStr: String?): Boolean {
        if (uriStr == null) return false
        val uri = Uri.parse(uriStr)
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persistedUriPermissions = context.contentResolver.persistedUriPermissions
        return persistedUriPermissions.any { it.uri == uri && (it.persistedTime != 0L) }
    }

    fun importFolder(context: Context, folderUri: Uri) {
        viewModelScope.launch {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(folderUri, takeFlags)

                val uriStr = folderUri.toString()
                _folderUri.value = uriStr
                currentFolderUriString = uriStr

                val prefs = context.getSharedPreferences("video_player_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("folder_uri", uriStr).apply()

                _eventFlow.emit(VideoUiEvent.ShowToast("Folder imported successfully!"))

                scanAndGeneratePlaylist(context, uriStr)
            } catch (e: Exception) {
                e.printStackTrace()
                _eventFlow.emit(VideoUiEvent.ShowToast("Failed to import folder: ${e.message}"))
            }
        }
    }

    fun scanAndGeneratePlaylist(context: Context, uriStr: String? = currentFolderUriString) {
        val targetUri = uriStr ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val videos = repository.scanVideosInFolder(context, targetUri)
                _scannedVideos.value = videos

                if (videos.isEmpty()) {
                    _playlist.value = emptyList()
                    _eventFlow.emit(VideoUiEvent.ShowToast("No video files found in selected folder."))
                    return@launch
                }

                // Shuffling Logic
                val finalShuffled = if (_filterWatched.value) {
                    val watchedSet = repository.getWatchedUris()
                    val unwatched = videos.filter { it.uriString !in watchedSet }
                    
                    if (unwatched.isNotEmpty()) {
                        unwatched.shuffled()
                    } else {
                        _eventFlow.emit(VideoUiEvent.ShowToast("All videos watched! Resetting playlist shuffle."))
                        repository.resetWatchedUris(videos.map { it.uriString })
                        videos.shuffled()
                    }
                } else {
                    // Shuffling works perfectly across ALL folder videos when filterWatched is off!
                    videos.shuffled()
                }

                // Enforce "doesn't take all videos of the folder" Playlist Limit if specified
                val limit = _playlistLimit.value
                val limitedList = if (limit > 0 && finalShuffled.size > limit) {
                    finalShuffled.take(limit)
                } else {
                    finalShuffled
                }

                _playlist.value = limitedList

            } catch (e: Exception) {
                e.printStackTrace()
                _eventFlow.emit(VideoUiEvent.ShowToast("Error scanning folder: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setPlaylistLimit(context: Context, limit: Int) {
        _playlistLimit.value = limit
        val prefs = context.getSharedPreferences("video_player_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("playlist_limit", limit).apply()
        scanAndGeneratePlaylist(context)
    }

    fun setFilterWatched(context: Context, filter: Boolean) {
        _filterWatched.value = filter
        val prefs = context.getSharedPreferences("video_player_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("filter_watched", filter).apply()
        scanAndGeneratePlaylist(context)
    }

    fun setMuted(context: Context, muted: Boolean) {
        _isMuted.value = muted
        val prefs = context.getSharedPreferences("video_player_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_muted", muted).apply()
    }

    fun forceReshuffle(context: Context) {
        viewModelScope.launch {
            scanAndGeneratePlaylist(context)
            _eventFlow.emit(VideoUiEvent.ScrollToPage(0))
            _eventFlow.emit(VideoUiEvent.ShowToast("Symmetric Reshuffle Completed!"))
        }
    }

    fun setVideoScaleMode(mode: Int) {
        _videoScaleMode.value = mode
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    fun setAutoAdvance(enabled: Boolean) {
        _autoAdvance.value = enabled
    }

    fun toggleImmersiveMode() {
        _isImmersiveMode.value = !_isImmersiveMode.value
    }

    fun setImmersiveMode(enabled: Boolean) {
        _isImmersiveMode.value = enabled
    }

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun markAsWatched(video: LocalVideo) {
        viewModelScope.launch {
            repository.addToWatched(video.uriString)
        }
    }

    fun deleteVideo(context: Context, video: LocalVideo, currentIndex: Int) {
        viewModelScope.launch {
            val success = repository.deleteVideoFile(context, video.uriString)
            if (success) {
                _eventFlow.emit(VideoUiEvent.ShowToast("Video physical file deleted."))
                
                val currentPlaylist = _playlist.value.toMutableList()
                val deletedIndex = currentPlaylist.indexOfFirst { it.uriString == video.uriString }
                
                if (deletedIndex != -1) {
                    currentPlaylist.removeAt(deletedIndex)
                    _playlist.value = currentPlaylist

                    val currentScanned = _scannedVideos.value.toMutableList()
                    currentScanned.removeAll { it.uriString == video.uriString }
                    _scannedVideos.value = currentScanned

                    if (currentPlaylist.isNotEmpty()) {
                        val nextIndex = if (currentIndex >= currentPlaylist.size) {
                            currentPlaylist.size - 1
                        } else {
                            currentIndex
                        }
                        _eventFlow.emit(VideoUiEvent.ScrollToPage(nextIndex))
                    } else {
                        _eventFlow.emit(VideoUiEvent.ShowToast("No more videos left in playlist."))
                    }
                }
            } else {
                _eventFlow.emit(VideoUiEvent.ShowToast("Failed to delete video from storage."))
            }
        }
    }

    fun resetHistoryAndReShuffle(context: Context) {
        val targetUri = currentFolderUriString ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.clearAllWatched()
                _eventFlow.emit(VideoUiEvent.ShowToast("Watched history cleared! Reshuffling..."))
                scanAndGeneratePlaylist(context, targetUri)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
