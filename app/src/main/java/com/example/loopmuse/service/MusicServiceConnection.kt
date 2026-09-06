package com.example.loopmuse.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.PlaybackScope
import com.example.loopmuse.data.RepeatMode
import com.example.loopmuse.data.SelectionItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MusicServiceConnection(private val context: Context) {
    
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentTrack = MutableStateFlow<MusicFile?>(null)
    val currentTrack: StateFlow<MusicFile?> = _currentTrack

    private val _allSongs = MutableStateFlow<List<MusicFile>>(emptyList())
    val allSongs: StateFlow<List<MusicFile>> = _allSongs

    private val _playedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val playedSongIds: StateFlow<Set<String>> = _playedSongIds

    private val _repeatMode = MutableStateFlow(RepeatMode.SHUFFLE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode

    private val _playbackScope = MutableStateFlow(PlaybackScope.ALL)
    val playbackScope: StateFlow<PlaybackScope> = _playbackScope

    private val _isSingleRepeat = MutableStateFlow(false)
    val isSingleRepeat: StateFlow<Boolean> = _isSingleRepeat

    private val _pendingScope = MutableStateFlow<PlaybackScope?>(null)
    val pendingScope: StateFlow<PlaybackScope?> = _pendingScope

    private val _pendingRepeatMode = MutableStateFlow<RepeatMode?>(null)
    val pendingRepeatMode: StateFlow<RepeatMode?> = _pendingRepeatMode

    private val _queueEnded = MutableSharedFlow<Unit>()
    val queueEnded: SharedFlow<Unit> = _queueEnded
    
    private val _songCounts = MutableStateFlow("Loading...")
    val songCounts: StateFlow<String> = _songCounts
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlaybackService.LocalBinder
            musicService = binder.getService()
            isBound = true
            _isConnected.value = true
            
            // Start observing service state
            musicService?.let { service ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).apply {
                    launch {
                        service.isPlaying.collect { playing ->
                            _isPlaying.value = playing
                        }
                    }
                    launch {
                        service.currentTrack.collect { track ->
                            _currentTrack.value = track
                        }
                    }
                    launch {
                        service.playedSongIds.collect { ids ->
                            _playedSongIds.value = ids
                        }
                    }
                    launch {
                        service.repeatMode.collect { mode ->
                            _repeatMode.value = mode
                        }
                    }
                    launch {
                        service.playbackScope.collect { scope ->
                            _playbackScope.value = scope
                        }
                    }
                    launch {
                        service.isSingleRepeat.collect { val_ ->
                            _isSingleRepeat.value = val_
                        }
                    }
                    launch {
                        service.pendingScope.collect { scope ->
                            _pendingScope.value = scope
                        }
                    }
                    launch {
                        service.pendingRepeatMode.collect { mode ->
                            _pendingRepeatMode.value = mode
                        }
                    }
                    launch {
                        service.queueEnded.collect {
                            _queueEnded.emit(Unit)
                        }
                    }
                    launch {
                        service.songCounts.collect { counts ->
                            _songCounts.value = counts
                            // Update allSongs when counts change (implying a scan finished)
                            _allSongs.value = service.getAllSongs()
                        }
                    }
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
            _isConnected.value = false
        }
    }
    
    fun bindService() {
        val intent = Intent(context, MusicPlaybackService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        context.startService(intent)
    }
    
    fun unbindService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            _isConnected.value = false
        }
    }
    
    fun setSelectedItems(items: List<SelectionItem>) {
        musicService?.setSelectedItems(items)
    }

    fun getSelectedItems(): List<SelectionItem> {
        return musicService?.getSelectedItems() ?: emptyList()
    }
    
    fun refreshSongCounts() {
        musicService?.forceUpdateSongCounts()
    }
    
    suspend fun playRandomUnplayedSong(): Boolean {
        return musicService?.playRandomUnplayedSong() ?: false
    }
    
    fun togglePlayPause() {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY_PAUSE
        }
        context.startService(intent)
    }
    
    fun playNext() {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_NEXT
        }
        context.startService(intent)
    }

    fun playPrevious() {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PREVIOUS
        }
        context.startService(intent)
    }
    
    fun stopService() {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_STOP
        }
        context.startService(intent)
    }
    
    fun clearPlaybackHistory() {
        musicService?.clearPlaybackHistory()
    }

    fun toggleSort(criteria: SortCriteria) {
        musicService?.toggleSort(criteria)
    }

    fun getSortInfo(): Pair<SortCriteria, SortOrder> {
        return musicService?.getSortInfo() ?: (SortCriteria.DATE to SortOrder.DESCENDING)
    }

    fun isPlayed(id: String): Boolean = musicService?.isPlayed(id) ?: false

    fun setRepeatMode(mode: RepeatMode) {
        musicService?.setRepeatMode(mode)
    }

    fun setPlaybackScope(scope: PlaybackScope) {
        musicService?.setPlaybackScope(scope)
    }

    fun setRecentLimit(limit: Int) {
        musicService?.setRecentLimit(limit)
    }

    fun toggleSingleRepeat() {
        musicService?.toggleSingleRepeat()
    }

    fun playTrackById(id: String) {
        musicService?.playTrackById(id)
    }

    fun getAllSongs(): List<MusicFile> {
        return musicService?.getAllSongs() ?: emptyList()
    }
}