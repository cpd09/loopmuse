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
import kotlinx.coroutines.flow.first
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
    private var playbackStarting = false
    private var playerGeneration = 0L
    private data class SuspendedPlayback(
        val trackId: String?, val positionMs: Long, val temporaryHistorySize: Int = -1
    )
    private val likedSuspensions = mutableMapOf<String, SuspendedPlayback>()
    private var selectionSuspension: SuspendedPlayback? = null
    private var pendingResumeTrackId: String? = null
    private var pendingResumePositionMs: Long = 0L
    private var selectedItems: List<SelectionItem> = emptyList()
    
    private var cachedAllSongs: List<MusicFile> = emptyList()
    private var lastScanTime: Long = 0
    private var scanGeneration: Long = 0
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

    private val _likedFingerprints = MutableStateFlow<Set<String>>(emptySet())
    val likedFingerprints: StateFlow<Set<String>> = _likedFingerprints
    
    private val _allSongsInQueue = MutableStateFlow<List<MusicFile>>(emptyList())
    val allSongsInQueue: StateFlow<List<MusicFile>> = _allSongsInQueue

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _songCounts = MutableStateFlow("0곡")
    val songCounts: StateFlow<String> = _songCounts
    
    
    // Room DB & Backup
    private lateinit var appDatabase: com.example.loopmuse.data.db.AppDatabase
    lateinit var backupManager: BackupManager
    private var isImportingData = false
    
    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }
    
    override fun onCreate() {
        super.onCreate()
        queueManager = PlaybackQueueManager(this)
        musicScanner = MusicScanner(this)
        notificationManager = NotificationManagerCompat.from(this)
        appDatabase = com.example.loopmuse.data.db.AppDatabase.getDatabase(this)
        backupManager = BackupManager(this, appDatabase)
        
        loadSelectedItems()
        syncWithQueueManager()
        backupManager.startAutoBackup(serviceScope)
        createNotificationChannel()
        initializeMediaSession()
        startPositionUpdates()

        // Sync liked songs from DB
        serviceScope.launch {
            appDatabase.songMetaDao().getLikedSongs().collect { likedEntities ->
                val set = likedEntities.map { it.fingerprintId }.toSet()
                _likedFingerprints.value = set
                if (!isImportingData) {
                    val likedState = queueManager.getCurrentPlaylist()?.takeIf { it.isLikedFilter }
                    val resume = likedState?.let {
                        likedSuspensions[it.id] ?: SuspendedPlayback(it.queue.getOrNull(it.currentIndex),
                            it.likedResumePositionMs)
                    }
                    queueManager.setLikedFingerprints(set)
                    if (likedState != null && queueManager.getCurrentPlaylist()?.isLikedFilter == false) {
                        likedSuspensions.remove(likedState.id)
                        if (resume != null && currentSong?.id == resume.trackId &&
                            likedState.likedCurrentId == resume.trackId &&
                            likedState.temporaryHistory.size == resume.temporaryHistorySize) {
                            syncWithQueueManager()
                        } else resumeUnderlyingPlayback(resume)
                    } else {
                        alignLikedPlayback()
                        syncWithQueueManager()
                    }
                }
            }
        }

        if (selectedItems.isNotEmpty()) {
            serviceScope.launch { updateSongCounts() }
        }
    }

    private fun syncWithQueueManager() {
        val activeSongs = queueManager.getActiveQueueSongs()
        currentSong?.let { song ->
            if (activeSongs.none { it.id == song.id }) {
                stopCurrentSong()
                queueManager.isSelectionPlayback = false
            }
        }
        _playlistState.value = queueManager.getCurrentPlaylist()
        _allPlaylists.value = queueManager.getAllPlaylists()
        _isSelectionMode.value = queueManager.isSelectionMode
        _isSelectionPlayback.value = queueManager.isSelectionPlayback
        // We don't sync isTasteMode from manager as it's a runtime toggle for now, 
        // but we could if we wanted it persisted.
        _selectedIds.value = queueManager.selectedIds.toSet()
        _playedSongIds.value = queueManager.getPlayedSongIds()
        _allSongsInQueue.value = activeSongs
        _currentTrack.value = if (queueManager.isSelectionMode && !queueManager.isSelectionPlayback && currentSong == null) {
            val selected = queueManager.selectedIds
            activeSongs.firstOrNull { it.id in selected }
                ?: queueManager.getCurrentTrack()
        } else {
            queueManager.getCurrentTrack()
        }
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
        backupManager.stopAutoBackup()
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
        likedSuspensions.clear()
        selectionSuspension = null
        pendingResumeTrackId = null
        // A restored playlist must survive reselecting its music folder.
        queueManager.switchPlaylist("ALL")
        queueManager.clearSelection() 
        syncWithQueueManager()
        invalidateCache()
        serviceScope.launch { updateSongCounts() }
    }

    fun getSelectedItems(): List<SelectionItem> = selectedItems

    suspend fun saveAlarm(alarm: com.example.loopmuse.data.db.AlarmEntity): Int {
        val dao = appDatabase.alarmDao()
        val previous = if (alarm.id == 0) null else withContext(Dispatchers.IO) { dao.getAlarmById(alarm.id) }
        val id = withContext(Dispatchers.IO) {
            if (alarm.id == 0) dao.insertAlarm(alarm).toInt()
            else { dao.updateAlarm(alarm); alarm.id }
        }
        val scheduler = com.example.loopmuse.service.alarm.AlarmScheduler(this)
        try {
            if (alarm.isEnabled) scheduler.scheduleAlarm(alarm.copy(id = id))
            else scheduler.cancelAlarm(id)
        } catch (e: Exception) {
            withContext(Dispatchers.IO) {
                if (previous == null) dao.deleteAlarm(alarm.copy(id = id))
                else dao.updateAlarm(previous)
            }
            if (previous?.isEnabled == true) runCatching { scheduler.scheduleAlarm(previous) }
            throw e
        }
        return id
    }

    suspend fun deleteAlarm(alarm: com.example.loopmuse.data.db.AlarmEntity) {
        com.example.loopmuse.service.alarm.AlarmScheduler(this).cancelAlarm(alarm.id)
        withContext(Dispatchers.IO) { appDatabase.alarmDao().deleteAlarm(alarm) }
    }

    suspend fun restoreUserData(uri: android.net.Uri, mode: RestoreMode) {
        isImportingData = true
        backupManager.stopAutoBackup()
        invalidateCache()
        stopCurrentSong()
        try {
            backupManager.restore(uri, mode)
            reloadUserData()
        } finally {
            isImportingData = false
            backupManager.startAutoBackup(serviceScope)
        }
    }

    suspend fun startFreshUserData() {
        isImportingData = true
        backupManager.stopAutoBackup()
        invalidateCache()
        stopCurrentSong()
        try {
            backupManager.startFresh()
            reloadUserData()
        } finally {
            isImportingData = false
            backupManager.startAutoBackup(serviceScope)
        }
    }

    private fun reloadUserData() {
        stopCurrentSong()
        likedSuspensions.clear()
        selectionSuspension = null
        pendingResumeTrackId = null
        selectedItems = emptyList()
        loadSelectedItems()
        queueManager = PlaybackQueueManager(this)
        queueManager.setLikedFingerprints(_likedFingerprints.value)
        invalidateCache()
        syncWithQueueManager()
        updateSongCounts()
    }

    private fun invalidateCache() {
        scanGeneration++
        cachedAllSongs = emptyList()
        lastScanTime = 0
    }
    
    private suspend fun getCachedOrScanSongs(): List<MusicFile>? {
        val currentTime = System.currentTimeMillis()
        return if ((cachedAllSongs.isNotEmpty()) && ((currentTime - lastScanTime) < scanCacheTimeout)) {
            cachedAllSongs
        } else {
            val generation = scanGeneration
            val itemsToScan = selectedItems.toList()
            val songs = musicScanner.scanMusicFiles(itemsToScan)
            if (generation != scanGeneration) return null
            cachedAllSongs = songs
            queueManager.setAllSongs(songs)
            lastScanTime = System.currentTimeMillis()
            songs
        }
    }
    
    private fun updateSongCounts() {
        serviceScope.launch {
            try {
                val songs = getCachedOrScanSongs() ?: return@launch
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

    fun switchPlaylist(id: String) {
        if (queueManager.getCurrentPlaylist()?.id == id || queueManager.getAllPlaylists().none { it.id == id }) return
        stopCurrentSong()
        pendingResumeTrackId = null
        selectionSuspension = null
        queueManager.switchPlaylist(id)
        queueManager.setLikedFingerprints(_likedFingerprints.value)
        if (queueManager.getCurrentPlaylist()?.isLikedFilter == false) likedSuspensions.remove(id)
        alignLikedPlayback()
        syncWithQueueManager()
    }
    fun addCustomPlaylist(name: String, ids: List<String>) { queueManager.addCustomPlaylist(name, ids); syncWithQueueManager() }
    fun updateCustomPlaylist(id: String, name: String, ids: List<String>) { queueManager.updateCustomPlaylist(id, name, ids); syncWithQueueManager() }
    fun deletePlaylist(id: String) { queueManager.deletePlaylist(id); syncWithQueueManager() }

    fun toggleLikedFilter(): Boolean {
        val previous = queueManager.getCurrentPlaylist() ?: return false
        val suspended = if (previous.isLikedFilter) {
            likedSuspensions[previous.id] ?: SuspendedPlayback(
                previous.queue.getOrNull(previous.currentIndex), previous.likedResumePositionMs)
        } else capturePlayback().also { likedSuspensions[previous.id] = it }
        val sameTrackUnchanged = previous.isLikedFilter && currentSong?.id == suspended.trackId &&
            previous.likedCurrentId == suspended.trackId &&
            previous.temporaryHistory.size == suspended.temporaryHistorySize
        val result = queueManager.toggleLikedFilter(suspended.positionMs)
        if (!result) {
            if (!previous.isLikedFilter) likedSuspensions.remove(previous.id)
            return false
        }
        if (previous.isLikedFilter) {
            likedSuspensions.remove(previous.id)
            if (sameTrackUnchanged) syncWithQueueManager()
            else resumeUnderlyingPlayback(suspended)
        } else {
            alignLikedPlayback()
            syncWithQueueManager()
        }
        return result
    }

    private fun capturePlayback(): SuspendedPlayback = SuspendedPlayback(
        queueManager.getCurrentTrack()?.id,
        runCatching { mediaPlayer?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L),
        queueManager.getCurrentPlaylist()?.temporaryHistory?.size ?: -1
    )

    private fun resumeUnderlyingPlayback(suspended: SuspendedPlayback?) {
        val continuePlaying = _isPlaying.value || playbackStarting
        stopCurrentSong()
        pendingResumeTrackId = null
        pendingResumePositionMs = 0L
        val track = queueManager.getCurrentTrack()
        if (track != null) {
            val position = suspended?.positionMs?.takeIf { suspended.trackId == track.id } ?: 0L
            if (continuePlaying) playSong(track, position)
            else {
                pendingResumeTrackId = track.id
                pendingResumePositionMs = position
            }
        }
        syncWithQueueManager()
    }

    private fun alignLikedPlayback() {
        val state = queueManager.getCurrentPlaylist() ?: return
        if (!state.isLikedFilter || queueManager.isSelectionPlayback) return
        val selected = queueManager.getCurrentTrack() ?: return
        if (selected.fingerprintId in _likedFingerprints.value) return
        val wasPlaying = _isPlaying.value
        val next = queueManager.getNextTrack(isManual = true)
        if (currentSong != null) stopCurrentSong()
        if (wasPlaying && next != null) playSong(next)
    }

    fun searchWithFilters(
        query: String,
        category: String = "전체",
        isLikedOnly: Boolean = false,
        selectedVibes: Set<String> = emptySet(),
        selectedOccasions: Set<String> = emptySet()
    ) {
        serviceScope.launch {
            val dbMetas = appDatabase.songMetaDao().getAllMetadata().first()
            val metaMap = dbMetas.associateBy { it.fingerprintId }

            val results = cachedAllSongs.filter { song ->
                val meta = metaMap[song.fingerprintId]

                // 1. Text filter
                val matchesText = if (query.isBlank()) {
                    true
                } else {
                    when (category) {
                        "가수" -> song.artist.contains(query, ignoreCase = true)
                        "앨범" -> song.album.contains(query, ignoreCase = true)
                        "파일명" -> song.file.name.contains(query, ignoreCase = true) || song.title.contains(query, ignoreCase = true)
                        else -> song.title.contains(query, ignoreCase = true) ||
                                song.artist.contains(query, ignoreCase = true) ||
                                song.album.contains(query, ignoreCase = true) ||
                                song.file.name.contains(query, ignoreCase = true)
                    }
                }

                // 2. Liked filter
                val matchesLiked = if (isLikedOnly) {
                    meta?.isLiked == true
                } else {
                    true
                }

                // 3. Vibe tags filter (OR logic among selected vibes)
                val matchesVibe = if (selectedVibes.isNotEmpty()) {
                    val songVibes = meta?.vibeTags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
                    selectedVibes.any { songVibes.contains(it) }
                } else {
                    true
                }

                // 4. Occasion tags filter (OR logic among selected occasions)
                val matchesOccasion = if (selectedOccasions.isNotEmpty()) {
                    val songOccasions = meta?.occasionTags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
                    selectedOccasions.any { songOccasions.contains(it) }
                } else {
                    true
                }

                matchesText && matchesLiked && matchesVibe && matchesOccasion
            }.map { it.id }

            val titleParts = mutableListOf<String>()
            if (query.isNotBlank()) titleParts.add("'$query'")
            if (isLikedOnly) titleParts.add("좋아요")
            if (selectedVibes.isNotEmpty()) titleParts.add(selectedVibes.joinToString(","))
            if (selectedOccasions.isNotEmpty()) titleParts.add(selectedOccasions.joinToString(","))
            val playlistTitle = if (titleParts.isEmpty()) "전체 검색" else "검색: ${titleParts.joinToString(" / ")}"

            stopCurrentSong()
            queueManager.setTemporaryPlaylist(playlistTitle, results)
            syncWithQueueManager()
        }
    }

    fun searchAndCreatePlaylist(query: String, type: String) {
        when (type) {
            "느낌" -> searchWithFilters("", "전체", false, setOf(query), emptySet())
            "상황" -> searchWithFilters("", "전체", false, emptySet(), setOf(query))
            "좋아요" -> searchWithFilters("", "전체", true, emptySet(), emptySet())
            else -> searchWithFilters(query, type, false, emptySet(), emptySet())
        }
    }

    suspend fun getAllSongMetas(): List<com.example.loopmuse.data.db.SongMetaEntity> {
        return appDatabase.songMetaDao().getAllMetadata().first()
    }

    fun toggleSelectionMode() {
        val enteringSelectionMode = !queueManager.isSelectionMode
        if (enteringSelectionMode) {
            selectionSuspension = capturePlayback()
            val songToSelect = queueManager.getCurrentTrack()
            stopCurrentSong()
            queueManager.toggleSelectionMode()
            songToSelect?.let { queueManager.toggleSelection(it.id) }
        } else {
            queueManager.toggleSelectionMode()
            resumeUnderlyingPlayback(selectionSuspension)
            selectionSuspension = null
            return
        }
        syncWithQueueManager()
    }

    fun toggleSelection(id: String) {
        if (!queueManager.isSelectionMode) {
            selectionSuspension = capturePlayback()
            stopCurrentSong()
            queueManager.toggleSelectionMode()
        }
        val wasSelectionPlayback = queueManager.isSelectionPlayback
        queueManager.toggleSelection(id)
        if (!queueManager.isSelectionMode) {
            resumeUnderlyingPlayback(selectionSuspension)
            selectionSuspension = null
            return
        }
        if (wasSelectionPlayback && !queueManager.isSelectionPlayback) stopCurrentSong()
        syncWithQueueManager()
    }

    fun onPlaylistSongClick(id: String) {
        if (queueManager.isSelectionMode) toggleSelection(id) else playTrackById(id)
    }

    fun toggleLike(fingerprintId: String) {
        serviceScope.launch {
            val isCurrentlyLiked = _likedFingerprints.value.contains(fingerprintId)
            val currentMeta = appDatabase.songMetaDao().getMetadataById(fingerprintId)
            
            if (currentMeta != null) {
                appDatabase.songMetaDao().updateLikeStatus(fingerprintId, !isCurrentlyLiked, System.currentTimeMillis())
            } else {
                val file = cachedAllSongs.find { it.fingerprintId == fingerprintId }
                appDatabase.songMetaDao().insertOrUpdate(
                    com.example.loopmuse.data.db.SongMetaEntity(
                        fingerprintId = fingerprintId,
                        title = file?.title ?: "Unknown",
                        artist = file?.artist ?: "Unknown",
                        isLiked = !isCurrentlyLiked
                    )
                )
            }
        }
    }

    suspend fun getSongMeta(fingerprintId: String): com.example.loopmuse.data.db.SongMetaEntity? {
        return appDatabase.songMetaDao().getMetadataById(fingerprintId)
    }

    fun updateSongTags(fingerprintId: String, vibeTags: String, occasionTags: String) {
        serviceScope.launch {
            val currentMeta = appDatabase.songMetaDao().getMetadataById(fingerprintId)
            if (currentMeta != null) {
                appDatabase.songMetaDao().insertOrUpdate(
                    currentMeta.copy(vibeTags = vibeTags, occasionTags = occasionTags, lastUpdated = System.currentTimeMillis())
                )
            } else {
                val file = cachedAllSongs.find { it.fingerprintId == fingerprintId }
                appDatabase.songMetaDao().insertOrUpdate(
                    com.example.loopmuse.data.db.SongMetaEntity(
                        fingerprintId = fingerprintId,
                        title = file?.title ?: "Unknown",
                        artist = file?.artist ?: "Unknown",
                        vibeTags = vibeTags,
                        occasionTags = occasionTags
                    )
                )
            }
        }
    }

    fun startSelectionPlayback() {
        val selected = queueManager.selectedIds
        val firstSelected = queueManager.getActiveQueueSongs().firstOrNull { it.id in selected } ?: return
        val track = queueManager.startSelectionPlayback(firstSelected.id) ?: return
        if (!playSong(track)) queueManager.isSelectionPlayback = false
        syncWithQueueManager()
    }

    fun selectAll() { 
        if (!queueManager.isSelectionMode) selectionSuspension = capturePlayback()
        stopCurrentSong()
        queueManager.selectAll()
        // Automatically enter selection mode when 'Select All' is clicked
        _isSelectionMode.value = true
        queueManager.isSelectionMode = true
        syncWithQueueManager() 
    }
    fun clearSelection() {
        if (!queueManager.isSelectionMode && selectionSuspension == null) {
            queueManager.clearSelection()
            syncWithQueueManager()
            return
        }
        queueManager.clearSelection()
        resumeUnderlyingPlayback(selectionSuspension)
        selectionSuspension = null
    }

    fun toggleSort(criteria: SortCriteria) { queueManager.toggleSort(criteria); syncWithQueueManager() }
    fun playTrackById(id: String) { 
        val state = queueManager.getCurrentPlaylist()
        if (state?.isLikedFilter == true && !queueManager.isSelectionPlayback &&
            queueManager.getActiveQueueSongs().firstOrNull { it.id == id }?.fingerprintId !in _likedFingerprints.value) return
        queueManager.playTrackById(id)?.let { 
            playSong(it)
            syncWithQueueManager()
        } 
    }
    fun seekTo(position: Long) { mediaPlayer?.seekTo(position.toInt()); _currentPosition.value = position }
    fun getSortInfo(): Pair<SortCriteria, SortOrder> { val s = queueManager.getCurrentPlaylist(); return (s?.sortCriteria ?: SortCriteria.DATE) to (s?.sortOrder ?: SortOrder.DESCENDING) }

    private fun playSong(musicFile: MusicFile, startPositionMs: Long = 0L): Boolean {
        return try {
            val generation = ++playerGeneration
            pendingResumeTrackId = null
            pendingResumePositionMs = 0L
            playbackStarting = true
            mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
            mediaPlayer = null
            currentSong = musicFile
            _currentTrack.value = musicFile
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(musicFile.path)
                prepareAsync()
                setOnPreparedListener {
                    if (generation != playerGeneration) return@setOnPreparedListener
                    playbackStarting = false
                    if (startPositionMs > 0L) {
                        it.seekTo(startPositionMs.coerceAtMost((it.duration - 1).coerceAtLeast(0).toLong()).toInt())
                    } else if (_isTasteMode.value && it.duration > 30000) {
                        it.seekTo(30000)
                    }
                    it.start()
                    _isPlaying.value = true
                    queueManager.recordStartedTrack(musicFile.id)
                    syncWithQueueManager()
                    _duration.value = it.duration.toLong()
                    updateMediaSession()
                    startForeground(NOTIFICATION_ID, createNotification())
                }
                setOnCompletionListener {
                    if (generation != playerGeneration) return@setOnCompletionListener
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
                setOnErrorListener { _, _, _ ->
                    if (generation == playerGeneration) {
                        playbackStarting = false
                        _isPlaying.value = false
                    }
                    false
                }
            }
            true
        } catch (_: Exception) { playbackStarting = false; false }
    }
    
    private fun togglePlayPause() {
        if (_isSelectionMode.value && !_isSelectionPlayback.value) {
            startSelectionPlayback()
        } else {
            mediaPlayer?.let { if (it.isPlaying) pausePlayback() else resumePlayback() } ?: run {
                val track = queueManager.getPlayableCurrentOrNextTrack()
                track?.let {
                    val position = if (it.id == pendingResumeTrackId) pendingResumePositionMs else 0L
                    playSong(it, position)
                }
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
        playerGeneration++
        mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
        mediaPlayer = null
        playbackStarting = false
        _isPlaying.value = false
        currentSong = null
        _currentTrack.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
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
        likedSuspensions.clear()
        selectionSuspension = null
        pendingResumeTrackId = null
        queueManager.localReset()
        syncWithQueueManager() 
    }
    fun globalReset() {
        stopCurrentSong()
        likedSuspensions.clear()
        selectionSuspension = null
        pendingResumeTrackId = null
        queueManager.globalReset()
        syncWithQueueManager()
    }
}
