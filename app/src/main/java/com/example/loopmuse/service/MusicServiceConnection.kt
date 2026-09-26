package com.example.loopmuse.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.SelectionItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MusicServiceConnection(private val context: Context) {
    
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private var serviceCollectionScope: CoroutineScope? = null
    
    private val _isConnected = MutableStateFlow(value = false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val _isPlaying = MutableStateFlow(value = false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentTrack = MutableStateFlow<MusicFile?>(null)
    val currentTrack: StateFlow<MusicFile?> = _currentTrack

    private val _allSongsInQueue = MutableStateFlow<List<MusicFile>>(emptyList())
    val allSongsInQueue: StateFlow<List<MusicFile>> = _allSongsInQueue

    private val _playlistState = MutableStateFlow<PlaylistState?>(null)
    val playlistState: StateFlow<PlaylistState?> = _playlistState

    private val _allPlaylists = MutableStateFlow<List<PlaylistState>>(emptyList())
    val allPlaylists: StateFlow<List<PlaylistState>> = _allPlaylists

    private val _isSelectionMode = MutableStateFlow(value = false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    private val _isSelectionPlayback = MutableStateFlow(value = false)
    val isSelectionPlayback: StateFlow<Boolean> = _isSelectionPlayback

    private val _isTasteMode = MutableStateFlow(value = false)
    val isTasteMode: StateFlow<Boolean> = _isTasteMode

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds

    private val _likedFingerprints = MutableStateFlow<Set<String>>(emptySet())
    val likedFingerprints: StateFlow<Set<String>> = _likedFingerprints

    private val _playedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val playedSongIds: StateFlow<Set<String>> = _playedSongIds

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _queueEnded = MutableSharedFlow<Unit>()
    val queueEnded: SharedFlow<Unit> = _queueEnded
    
    private val _songCounts = MutableStateFlow("0곡")
    val songCounts: StateFlow<String> = _songCounts
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlaybackService.LocalBinder
            musicService = binder.getService()
            isBound = true
            _isConnected.value = true
            
            musicService?.let { s ->
                serviceCollectionScope?.cancel()
                CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()).also { serviceCollectionScope = it }.apply {
                    launch { s.isPlaying.collect { _isPlaying.value = it } }
                    launch { s.currentTrack.collect { _currentTrack.value = it } }
                    launch { s.playlistState.collect { _playlistState.value = it } }
                    launch { s.allPlaylists.collect { _allPlaylists.value = it } }
                    launch { s.allSongsInQueue.collect { _allSongsInQueue.value = it } }
                    launch { s.isSelectionMode.collect { _isSelectionMode.value = it } }
                    launch { s.isSelectionPlayback.collect { _isSelectionPlayback.value = it } }
                    launch { s.isTasteMode.collect { _isTasteMode.value = it } }
                    launch { s.selectedIds.collect { _selectedIds.value = it } }
                    launch { s.playedSongIds.collect { _playedSongIds.value = it } }
                    launch { s.likedFingerprints.collect { _likedFingerprints.value = it } }
                    launch { s.currentPosition.collect { _currentPosition.value = it } }
                    launch { s.duration.collect { _duration.value = it } }
                    launch { s.queueEnded.collect { _queueEnded.emit(Unit) } }
                    launch { s.songCounts.collect { _songCounts.value = it } }
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceCollectionScope?.cancel()
            serviceCollectionScope = null
            musicService = null
            _isConnected.value = false
        }
    }
    
    fun bindService() {
        if (isBound) return
        val intent = Intent(context, MusicPlaybackService::class.java)
        isBound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        context.startService(intent)
    }
    
    fun unbindService() {
        if (isBound) {
            serviceCollectionScope?.cancel()
            serviceCollectionScope = null
            context.unbindService(serviceConnection)
            isBound = false
            _isConnected.value = false
            musicService = null
        }
    }
    
    fun setSelectedItems(items: List<SelectionItem>) { musicService?.setSelectedItems(items) }
    fun getSelectedItems(): List<SelectionItem> = musicService?.getSelectedItems() ?: emptyList()
    fun togglePlayPause() { context.startService(Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_PLAY_PAUSE }) }
    fun playNext() { context.startService(Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_NEXT }) }
    fun playPrevious() { context.startService(Intent(context, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_PREVIOUS }) }
    fun clearPlaybackHistory() { musicService?.clearPlaybackHistory() }
    fun globalReset() { musicService?.globalReset() }
    fun toggleSort(criteria: SortCriteria) { musicService?.toggleSort(criteria) }
    fun seekTo(position: Long) { musicService?.seekTo(position) }
    @Suppress("unused")
    fun getSortInfo(): Pair<SortCriteria, SortOrder> = musicService?.getSortInfo() ?: (SortCriteria.DATE to SortOrder.DESCENDING)
    
    fun toggleRandom() { musicService?.toggleRandom() }
    fun toggleSmart() { musicService?.toggleSmart() }
    fun toggleSingleRepeat() { musicService?.toggleSingleRepeat() }
    fun toggleTasteMode() { musicService?.toggleTasteMode() }
    fun toggleLikedFilter(): Boolean = musicService?.toggleLikedFilter() ?: false
    fun switchPlaylist(id: String) { musicService?.switchPlaylist(id) }
    fun addCustomPlaylist(name: String, ids: List<String>) { musicService?.addCustomPlaylist(name, ids) }
    fun updateCustomPlaylist(id: String, name: String, ids: List<String>) { musicService?.updateCustomPlaylist(id, name, ids) }
    @Suppress("unused")
    fun deletePlaylist(id: String) { musicService?.deletePlaylist(id) }
    @Suppress("unused")
    fun searchAndCreatePlaylist(query: String, type: String) { musicService?.searchAndCreatePlaylist(query, type) }
    fun searchWithFilters(query: String, category: String, isLikedOnly: Boolean, selectedVibes: Set<String>, selectedOccasions: Set<String>) {
        musicService?.searchWithFilters(query, category, isLikedOnly, selectedVibes, selectedOccasions)
    }
    fun toggleSelectionMode() { musicService?.toggleSelectionMode() }
    fun toggleSelection(id: String) { musicService?.toggleSelection(id) }
    fun toggleLike(fingerprintId: String) { musicService?.toggleLike(fingerprintId) }
    
    // --- Tags ---
    suspend fun getAllSongMetas(): List<com.example.loopmuse.data.db.SongMetaEntity> = musicService?.getAllSongMetas() ?: emptyList()
    suspend fun getSongMeta(fingerprintId: String): com.example.loopmuse.data.db.SongMetaEntity? {
        return musicService?.getSongMeta(fingerprintId)
    }
    fun updateSongTags(fingerprintId: String, vibeTags: String, occasionTags: String) {
        musicService?.updateSongTags(fingerprintId, vibeTags, occasionTags)
    }

    // --- Alarm ---
    suspend fun scheduleAlarm(alarm: com.example.loopmuse.data.db.AlarmEntity) {
        (musicService ?: error("음악 서비스 연결 중입니다.")).saveAlarm(alarm)
    }

    fun selectAll() { musicService?.selectAll() }
    fun clearSelection() { musicService?.clearSelection() }
    fun onPlaylistSongClick(id: String) { musicService?.onPlaylistSongClick(id) }
    fun playTrackById(id: String) { musicService?.playTrackById(id) }

    // --- Backup & Restore ---
    suspend fun restoreUserData(fileUri: android.net.Uri, mode: RestoreMode) {
        (musicService ?: error("음악 서비스 연결 중입니다.")).restoreUserData(fileUri, mode)
    }

    suspend fun startFreshUserData() {
        (musicService ?: error("음악 서비스 연결 중입니다.")).startFreshUserData()
    }
}
