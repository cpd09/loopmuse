package com.example.loopmuse.service

import android.app.*
import android.content.Context
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
import com.example.loopmuse.MainActivity
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.PlaybackScope
import com.example.loopmuse.data.RepeatMode
import com.example.loopmuse.data.SelectionItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class MusicPlaybackService : Service() {
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "music_playback_channel"
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_STOP = "action_stop"
    }
    
    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: MusicFile? = null
    private var selectedItems: List<SelectionItem> = emptyList()
    
    // Cache for performance optimization
    private var cachedAllSongs: List<MusicFile> = emptyList()
    private var lastScanTime: Long = 0
    private val scanCacheTimeout = 30000L // 30 seconds
    
    private lateinit var queueManager: PlaybackQueueManager
    private lateinit var musicScanner: MusicScanner
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentTrack = MutableStateFlow<MusicFile?>(null)
    val currentTrack: StateFlow<MusicFile?> = _currentTrack

    private val _repeatMode = MutableStateFlow(RepeatMode.SHUFFLE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode

    private val _playbackScope = MutableStateFlow(PlaybackScope.ALL)
    val playbackScope: StateFlow<PlaybackScope> = _playbackScope

    private val _queueEnded = MutableSharedFlow<Unit>()
    val queueEnded: SharedFlow<Unit> = _queueEnded

    private val _playedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val playedSongIds: StateFlow<Set<String>> = _playedSongIds
    
    private val _songCounts = MutableStateFlow("Loading...")
    val songCounts: StateFlow<String> = _songCounts
    
    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }
    
    override fun onCreate() {
        super.onCreate()
        
        queueManager = PlaybackQueueManager(this)
        musicScanner = MusicScanner(this)
        notificationManager = NotificationManagerCompat.from(this)
        
        _repeatMode.value = queueManager.repeatMode
        _playbackScope.value = queueManager.currentScope
        _playedSongIds.value = queueManager.getPlayedSongIds()
        
        loadSelectedItems()
        
        createNotificationChannel()
        initializeMediaSession()
    }

    private fun loadSelectedItems() {
        val prefs = getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("selected_items", null)
        if (json != null) {
            selectedItems = Gson().fromJson(json, object : TypeToken<List<SelectionItem>>() {}.type)
        }
    }

    private fun saveSelectedItems() {
        val prefs = getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_items", Gson().toJson(selectedItems)).apply()
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlaybackService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resumePlayback()
                }
                
                override fun onPause() {
                    pausePlayback()
                }
                
                override fun onSkipToNext() {
                    playNext()
                }
                
                override fun onStop() {
                    stopService()
                }
            })
            
            isActive = true
        }
    }
    
    fun setSelectedItems(items: List<SelectionItem>) {
        val itemsChanged = selectedItems != items
        val isFirstTimeSet = _songCounts.value == "Loading..."
        selectedItems = items
        saveSelectedItems()
        
        if (itemsChanged || isFirstTimeSet) {
            // 폴더가 변경되거나 최초 설정 시 캐시 무효화 및 곡 수 업데이트
            invalidateCache()
            serviceScope.launch {
                updateSongCounts()
            }
        }
    }

    fun getSelectedItems(): List<SelectionItem> = selectedItems

    fun forceUpdateSongCounts() {
        serviceScope.launch {
            updateSongCounts()
        }
    }
    
    private fun invalidateCache() {
        cachedAllSongs = emptyList()
        lastScanTime = 0
    }
    
    private suspend fun getCachedOrScanSongs(): List<MusicFile> {
        val currentTime = System.currentTimeMillis()
        
        return if (cachedAllSongs.isNotEmpty() && (currentTime - lastScanTime) < scanCacheTimeout) {
            // Use cached results if available and not expired
            cachedAllSongs
        } else {
            // Scan and cache new results
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
                val total = songs.size
                _songCounts.value = if (queueManager.currentScope == PlaybackScope.ALL) {
                    "$total total songs"
                } else {
                    "Recent ${queueManager.getRecentSongs().size} songs"
                }
            } catch (e: Exception) {
                _songCounts.value = "Error loading song counts"
            }
        }
    }
    
    fun playRandomUnplayedSong(): Boolean {
        // This is now "Start Playback"
        val track = queueManager.getCurrentTrack() ?: queueManager.getNextTrack()
        return if (track != null) {
            playSong(track)
        } else {
            false
        }
    }
    
    fun setRepeatMode(mode: RepeatMode) {
        queueManager.resetActiveQueue(mode)
        _repeatMode.value = mode
    }

    fun setPlaybackScope(scope: PlaybackScope) {
        queueManager.currentScope = scope
        _playbackScope.value = scope
        serviceScope.launch {
            updateSongCounts()
        }
    }

    fun setRecentLimit(limit: Int) {
        queueManager.setRecentLimit(limit)
        serviceScope.launch {
            updateSongCounts()
        }
    }

    fun playTrackById(id: String) {
        queueManager.playTrackById(id)?.let {
            playSong(it)
        }
    }

    fun getRecentSongs(): List<MusicFile> = queueManager.getRecentSongs()
    
    private fun playSong(musicFile: MusicFile): Boolean {
        return try {
            // Clean up previous media player without clearing track info during transitions
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            mediaPlayer = null
            
            // Update track info immediately to maintain UI consistency
            currentSong = musicFile
            _currentTrack.value = musicFile
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(musicFile.path)
                prepareAsync()
                setOnPreparedListener { player ->
                    player.start()
                    _isPlaying.value = true
                    currentSong?.let { 
                        queueManager.addToHistory(it.id)
                        updateHistory()
                    }
                    updateMediaSession()
                    startForeground(NOTIFICATION_ID, createNotification())
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    currentSong?.let { 
                        queueManager.addToHistory(it.id) 
                        updateHistory()
                    }
                    serviceScope.launch {
                        val nextTrack = queueManager.getNextTrack()
                        if (nextTrack != null) {
                            playSong(nextTrack)
                        } else {
                            // End of queue!
                            _queueEnded.emit(Unit)
                            @Suppress("DEPRECATION")
                            stopForeground(false)
                            updateNotification()
                        }
                        launch {
                            updateSongCounts()
                        }
                    }
                }
                setOnErrorListener { _, _, _ ->
                    _isPlaying.value = false
                    false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                pausePlayback()
            } else {
                resumePlayback()
            }
        }
    }
    
    private fun pausePlayback() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        updateMediaSession()
        updateNotification()
    }
    
    private fun resumePlayback() {
        mediaPlayer?.start()
        _isPlaying.value = true
        updateMediaSession()
        updateNotification()
    }
    
    private fun playNext() {
        serviceScope.launch {
            val nextTrack = queueManager.skipToNext()
            if (nextTrack != null) {
                playSong(nextTrack)
            } else {
                _queueEnded.emit(Unit)
            }
            launch {
                updateSongCounts()
            }
        }
    }
    
    private fun stopCurrentSong() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        currentSong = null
        _currentTrack.value = null
    }
    
    private fun stopService() {
        stopCurrentSong()
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }
    
    private fun updateMediaSession() {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (_isPlaying.value) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                0L,
                1f
            )
            .build()
        
        mediaSession.setPlaybackState(playbackState)
        
        currentSong?.let { song ->
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .build()
            
            mediaSession.setMetadata(metadata)
        }
    }
    
    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val playPauseIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 2, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val currentTrack = currentSong ?: return createEmptyNotification()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTrack.title)
            .setContentText(currentTrack.artist)
            .setSubText("LoopMuse")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                null // Previous functionality can be added later
            )
            .addAction(
                if (_isPlaying.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (_isPlaying.value) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                nextPendingIntent
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }
    
    private fun createEmptyNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LoopMuse")
            .setContentText("Music Player")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
    
    private fun updateNotification() {
        if (currentSong != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(NOTIFICATION_ID, createNotification())
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, createNotification())
            }
        }
    }
    
    fun clearPlaybackHistory() {
        queueManager.clearAllHistory()
        _playedSongIds.value = emptySet()
        serviceScope.launch {
            updateSongCounts()
        }
    }

    private fun updateHistory() {
        _playedSongIds.value = queueManager.getPlayedSongIds()
    }

    fun isPlayed(id: String): Boolean = queueManager.isPlayed(id)
    
    fun getUnplayedCount(): Int {
        // Using "unplayed" concept as "songs remaining in current queue"
        return 0 // Simplified for now, or could be (queue.size - index)
    }
    
    fun getTotalSongsCount(): Int {
        return cachedAllSongs.size
    }

    fun getAllSongs(): List<MusicFile> {
        return if (queueManager.currentScope == PlaybackScope.ALL) {
            cachedAllSongs
        } else {
            queueManager.getRecentSongs()
        }
    }
}
