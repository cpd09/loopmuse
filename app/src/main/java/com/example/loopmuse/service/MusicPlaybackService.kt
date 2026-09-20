package com.example.loopmuse.service

import android.app.*
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.example.loopmuse.MainActivity
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.SelectionItem
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration.Companion.seconds

class MusicPlaybackService : Service() {
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "music_playback_channel"
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_NEXT = "action_next"
        const val ACTION_STOP = "action_stop"
    }
    
    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: MusicFile? = null
    private var selectedItems: List<SelectionItem> = emptyList()
    
    private var cachedAllSongs: List<MusicFile> = emptyList()
    private var lastScanTime: Long = 0
    private val scanCacheTimeout = 30000L
    
    private lateinit var queueManager: PlaybackQueueManager
    private lateinit var musicScanner: MusicScanner
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _isPlaying = MutableStateFlow(value = false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentTrack = MutableStateFlow<MusicFile?>(null)
    val currentTrack: StateFlow<MusicFile?> = _currentTrack

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

    private val _queueEnded = MutableSharedFlow<Unit>()
    val queueEnded: SharedFlow<Unit> = _queueEnded

    private val _playedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val playedSongIds: StateFlow<Set<String>> = _playedSongIds
    
    private val _allSongsInQueue = MutableStateFlow<List<MusicFile>>(emptyList())
    val allSongsInQueue: StateFlow<List<MusicFile>> = _allSongsInQueue

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _songCounts = MutableStateFlow("0곡")
    val songCounts: StateFlow<String> = _songCounts
    
    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }
    
    override fun onCreate() {
        super.onCreate()
        queueManager = PlaybackQueueManager(this)
        musicScanner = MusicScanner(this)
        notificationManager = NotificationManagerCompat.from(this)
        
        loadSelectedItems()
        syncWithQueueManager()
        createNotificationChannel()
        initializeMediaSession()
        startPositionUpdates()

        if (selectedItems.isNotEmpty()) {
            serviceScope.launch { updateSongCounts() }
        }
    }

    private fun syncWithQueueManager() {
        _playlistState.value = queueManager.getCurrentPlaylist()
        _allPlaylists.value = queueManager.getAllPlaylists()
        _isSelectionMode.value = queueManager.isSelectionMode
        _isSelectionPlayback.value = queueManager.isSelectionPlayback
        // We don't sync isTasteMode from manager as it's a runtime toggle for now, 
        // but we could if we wanted it persisted.
        _selectedIds.value = queueManager.selectedIds.toSet()
        _playedSongIds.value = queueManager.getPlayedSongIds()
        _allSongsInQueue.value = queueManager.getActiveQueueSongs()
        _currentTrack.value = queueManager.getCurrentTrack()
    }

    private fun startPositionUpdates() {
        serviceScope.launch {
            while (isActive) {
                if (_isPlaying.value) {
                    mediaPlayer?.let {
                        val pos = it.currentPosition.toLong()
                        _currentPosition.value = pos
                        _duration.value = it.duration.toLong()
                        
                        // Taste Mode: Skip to next if > 50 seconds
                        if (_isTasteMode.value && pos >= 50000L) {
                            playNext()
                        }
                    }
                }
                delay(1.seconds)
            }
        }
    }

    private fun loadSelectedItems() {
        val prefs = getSharedPreferences("music_prefs", MODE_PRIVATE)
        prefs.getString("selected_items", null)?.let { json ->
            selectedItems = Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<List<SelectionItem>>() {}.type)
        }
    }

    private fun saveSelectedItems() {
        val prefs = getSharedPreferences("music_prefs", MODE_PRIVATE)
        prefs.edit { putString("selected_items", Gson().toJson(selectedItems)) }
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_NEXT -> playNext()
            ACTION_STOP -> stopService()
        }
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopCurrentSong()
        mediaSession.release()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for music playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
    
    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlaybackService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resumePlayback() }
                override fun onPause() { pausePlayback() }
                override fun onSkipToNext() { playNext() }
                override fun onStop() { stopService() }
            })
            isActive = true
        }
    }
    
    fun setSelectedItems(items: List<SelectionItem>) {
        selectedItems = items
        saveSelectedItems()
        stopCurrentSong()
        queueManager.resetToDefault()
        // Ensure selection mode is cleared when selecting new folders
        queueManager.clearSelection() 
        syncWithQueueManager()
        invalidateCache()
        serviceScope.launch { updateSongCounts() }
    }

    fun getSelectedItems(): List<SelectionItem> = selectedItems

    private fun invalidateCache() {
        cachedAllSongs = emptyList()
        lastScanTime = 0
    }
    
    private suspend fun getCachedOrScanSongs(): List<MusicFile> {
        val currentTime = System.currentTimeMillis()
        return if ((cachedAllSongs.isNotEmpty()) && ((currentTime - lastScanTime) < scanCacheTimeout)) {
            cachedAllSongs
        } else {
            withContext(Dispatchers.IO) {
                val songs = musicScanner.scanMusicFiles(selectedItems)
                cachedAllSongs = songs
                queueManager.setAllSongs(songs)
                lastScanTime = currentTime
                songs
            }
        }
    }
    
    private fun updateSongCounts() {
        serviceScope.launch {
            try {
                val songs = getCachedOrScanSongs()
                _songCounts.value = "${songs.size}곡"
                syncWithQueueManager()
            } catch (_: Exception) {
                _songCounts.value = "오류"
            }
        }
    }
    
    // --- Controller Actions ---
    fun toggleRandom() { queueManager.toggleRandom(); syncWithQueueManager() }
    fun toggleSmart() { queueManager.toggleSmart(); syncWithQueueManager() }
    fun toggleSingleRepeat() { queueManager.toggleSingleRepeat(); syncWithQueueManager() }
    
    fun toggleTasteMode() {
        _isTasteMode.value = !_isTasteMode.value
        // If turned ON while playing, and position is before 30s, jump to 30s
        if (_isTasteMode.value && _isPlaying.value) {
            mediaPlayer?.let {
                if (it.currentPosition < 30000) {
                    seekTo(30000L)
                }
            }
        }
    }

    fun switchPlaylist(id: String) { stopCurrentSong(); queueManager.switchPlaylist(id); syncWithQueueManager() }
    fun addCustomPlaylist(name: String, ids: List<String>) { queueManager.addCustomPlaylist(name, ids); syncWithQueueManager() }
    fun updateCustomPlaylist(id: String, name: String, ids: List<String>) { queueManager.updateCustomPlaylist(id, name, ids); syncWithQueueManager() }
    fun deletePlaylist(id: String) { queueManager.deletePlaylist(id); syncWithQueueManager() }
    fun searchAndCreatePlaylist(query: String, type: String) {
        val results = cachedAllSongs.filter { 
            when (type) {
                "가수" -> it.artist.contains(query, ignoreCase = true)
                "앨범" -> it.album.contains(query, ignoreCase = true)
                else -> it.file.name.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true)
            }
        }.map { it.id }
        queueManager.setTemporaryPlaylist(query, results)
        syncWithQueueManager()
    }

    fun toggleSelectionMode() {
        val enteringSelectionMode = !queueManager.isSelectionMode
        if (enteringSelectionMode) {
            // Get the song that was either playing or in standby before stopping
            val songToSelect = currentSong ?: queueManager.getCurrentTrack()
            
            stopCurrentSong()
            
            // Auto-select the current song as the first item in the multi-selection
            songToSelect?.let {
                queueManager.toggleSelection(it.id)
                // Set as standby track for the selection mode
                _currentTrack.value = it
            }
        }
        
        queueManager.toggleSelectionMode()
        syncWithQueueManager()
    }

    fun toggleSelection(id: String) {
        if (!_isSelectionMode.value) {
            stopCurrentSong()
            _isSelectionMode.value = true
            queueManager.isSelectionMode = true
        }
        queueManager.toggleSelection(id)
        
        // --- Enhanced Standby Logic ---
        val currentQueue = queueManager.getActiveQueueSongs()
        val firstSelected = currentQueue.firstOrNull { queueManager.selectedIds.contains(it.id) }
        
        if (firstSelected != null) {
            _currentTrack.value = firstSelected
        } else {
            // Nothing selected -> Exit mode and reset standby track
            _isSelectionMode.value = false
            queueManager.isSelectionMode = false
            _isSelectionPlayback.value = false
            queueManager.isSelectionPlayback = false
            _currentTrack.value = queueManager.getCurrentTrack()
        }
        
        syncWithQueueManager()
    }

    fun startSelectionPlayback() {
        if (_selectedIds.value.isNotEmpty()) {
            queueManager.isSelectionPlayback = true
            _isSelectionPlayback.value = true
            val firstSelected = _allSongsInQueue.value.firstOrNull { _selectedIds.value.contains(it.id) }
            firstSelected?.let { playSong(it) }
        }
    }

    fun selectAll() { 
        stopCurrentSong()
        queueManager.selectAll()
        // Automatically enter selection mode when 'Select All' is clicked
        _isSelectionMode.value = true
        queueManager.isSelectionMode = true
        syncWithQueueManager() 
    }
    fun clearSelection() { queueManager.clearSelection(); syncWithQueueManager() }

    fun toggleSort(criteria: SortCriteria) { queueManager.toggleSort(criteria); syncWithQueueManager() }
    fun playTrackById(id: String) { 
        queueManager.playTrackById(id)?.let { 
            playSong(it)
            syncWithQueueManager()
        } 
    }
    fun seekTo(position: Long) { mediaPlayer?.seekTo(position.toInt()); _currentPosition.value = position }
    fun getSortInfo(): Pair<SortCriteria, SortOrder> { val s = queueManager.getCurrentPlaylist(); return (s?.sortCriteria ?: SortCriteria.DATE) to (s?.sortOrder ?: SortOrder.DESCENDING) }

    private fun playSong(musicFile: MusicFile): Boolean {
        return try {
            mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
            mediaPlayer = null
            currentSong = musicFile
            _currentTrack.value = musicFile
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(musicFile.path)
                prepareAsync()
                setOnPreparedListener {
                    // Taste Mode: Start at 30 seconds
                    if (_isTasteMode.value && it.duration > 30000) {
                        it.seekTo(30000)
                    }
                    it.start()
                    _isPlaying.value = true
                    _duration.value = it.duration.toLong()
                    updateMediaSession()
                    startForeground(NOTIFICATION_ID, createNotification())
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    serviceScope.launch {
                        val nextTrack = queueManager.getNextTrack(isManual = false)
                        syncWithQueueManager()
                        nextTrack?.let { playSong(it) } ?: run {
                            _queueEnded.emit(Unit)
                            @Suppress("DEPRECATION") stopForeground(false)
                            updateNotification()
                        }
                    }
                }
                setOnErrorListener { _, _, _ -> _isPlaying.value = false; false }
            }
            true
        } catch (_: Exception) { false }
    }
    
    private fun togglePlayPause() {
        if (_isSelectionMode.value && !_isSelectionPlayback.value) {
            startSelectionPlayback()
        } else {
            mediaPlayer?.let { if (it.isPlaying) pausePlayback() else resumePlayback() } ?: run {
                val track = queueManager.getCurrentTrack() ?: queueManager.getNextTrack()
                track?.let { playSong(it) }
            }
        }
    }
    
    private fun pausePlayback() { mediaPlayer?.pause(); _isPlaying.value = false; updateMediaSession(); updateNotification() }
    private fun resumePlayback() { mediaPlayer?.start(); _isPlaying.value = true; updateMediaSession(); updateNotification() }
    
    private fun playNext() {
        serviceScope.launch {
            val nextTrack = queueManager.getNextTrack(isManual = true)
            syncWithQueueManager()
            if (nextTrack != null) {
                playSong(nextTrack)
            } else {
                _queueEnded.emit(Unit)
            }
        }
    }

    private fun playPrevious() {
        serviceScope.launch {
            queueManager.getPreviousTrack()?.let { playSong(it) }
            syncWithQueueManager()
        }
    }
    
    private fun stopCurrentSong() {
        mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
        mediaPlayer = null
        _isPlaying.value = false
        currentSong = null
        _currentTrack.value = null
        _currentPosition.value = 0L
    }
    
    private fun stopService() { stopCurrentSong(); @Suppress("DEPRECATION") stopForeground(true); stopSelf() }
    
    private fun updateMediaSession() {
        val pbState = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_STOP)
            .setState(if (_isPlaying.value) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, 0L, 1f)
            .build()
        mediaSession.setPlaybackState(pbState)
        currentSong?.let { s ->
            val md = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, s.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, s.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, s.album)
                .build()
            mediaSession.setMetadata(md)
        }
    }
    
    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playPauseIntent = PendingIntent.getService(this, 1, Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nextIntent = PendingIntent.getService(this, 2, Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 3, Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        val s = currentSong ?: return createEmptyNotification()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(s.title).setContentText(s.artist).setSubText("LoopMuse").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent).setDeleteIntent(stopIntent).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", null)
            .addAction(if (_isPlaying.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (_isPlaying.value) "Pause" else "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }
    
    private fun createEmptyNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("LoopMuse").setContentText("Music Player").setSmallIcon(android.R.drawable.ic_media_play).build()
    
    private fun updateNotification() {
        if (currentSong != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(NOTIFICATION_ID, createNotification())
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, createNotification())
            }
        }
    }
    
    fun clearPlaybackHistory() { 
        stopCurrentSong()
        queueManager.localReset()
        syncWithQueueManager() 
    }
    fun globalReset() { stopCurrentSong(); queueManager.globalReset(); syncWithQueueManager() }
}
