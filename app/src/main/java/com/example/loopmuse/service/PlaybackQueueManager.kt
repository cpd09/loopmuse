package com.example.loopmuse.service

import android.content.Context
import android.content.SharedPreferences
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.PlaybackScope
import com.example.loopmuse.data.RepeatMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class SortCriteria {
    FILENAME, DATE
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

class PlaybackQueueManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playback_queue", Context.MODE_PRIVATE)
    private val gson = Gson()

    private var allSongs: List<MusicFile> = emptyList()
    
    // State for ALL scope
    private var allQueue: List<String> = emptyList() // IDs
    private var allCurrentIndex: Int = -1
    
    // State for RECENT scope
    private var recentQueue: List<String> = emptySet<String>().toList() // Use set then list for recent IDs
    private var recentCurrentIndex: Int = -1
    private var recentLimit: Int = 100

    // Global Played History (across scopes)
    private var playedSongIds: MutableSet<String> = mutableSetOf()
    
    // Per-track positions (Resume)
    private var trackPositions: MutableMap<String, Long> = mutableMapOf()

    // Current State
    var currentScope: PlaybackScope = PlaybackScope.ALL
    var repeatMode: RepeatMode = RepeatMode.SHUFFLE
    var sortCriteria: SortCriteria = SortCriteria.DATE
    var sortOrder: SortOrder = SortOrder.DESCENDING
    var isSingleRepeat: Boolean = false

    // Pending State (to be applied when current queue ends)
    var pendingScope: PlaybackScope? = null
    var pendingRepeatMode: RepeatMode? = null

    init {
        loadState()
    }

    fun setAllSongs(songs: List<MusicFile>) {
        val oldSongsMap = allSongs.associateBy { it.id }
        allSongs = songs
        val newSongsMap = allSongs.associateBy { it.id }

        // Update ALL queue
        updateQueue(PlaybackScope.ALL, newSongsMap, oldSongsMap)
        
        // Update RECENT queue
        updateRecentQueue(newSongsMap, oldSongsMap)
        
        saveState()
    }

    fun resetToDefault() {
        // Hard reset after selection
        currentScope = PlaybackScope.ALL
        repeatMode = RepeatMode.SEQUENTIAL
        sortCriteria = SortCriteria.DATE
        sortOrder = SortOrder.DESCENDING
        isSingleRepeat = false
        pendingScope = null
        pendingRepeatMode = null
        
        allQueue = allSongs
            .asSequence()
            .sortedByDescending { it.dateAdded }
            .map { it.id }
            .toList()
        allCurrentIndex = if (allQueue.isNotEmpty()) 0 else -1
        
        saveState()
    }

    private fun updateQueue(scope: PlaybackScope, newSongsMap: Map<String, MusicFile>, oldSongsMap: Map<String, MusicFile>) {
        val currentQueue = if (scope == PlaybackScope.ALL) allQueue else recentQueue
        val currentIndex = if (scope == PlaybackScope.ALL) allCurrentIndex else recentCurrentIndex
        
        val newIds = newSongsMap.keys
        val existingIdsInQueue = currentQueue.filter { newIds.contains(it) }
        val addedIds = newIds.filter { !oldSongsMap.containsKey(it) }
        
        val updatedQueue = if (repeatMode == RepeatMode.SHUFFLE) {
            val playedPart = if ((currentIndex >= 0) && (currentIndex < existingIdsInQueue.size)) {
                existingIdsInQueue.subList(0, currentIndex + 1)
            } else emptyList()
            
            val unplayedPart = if ((currentIndex + 1) < existingIdsInQueue.size) {
                existingIdsInQueue.subList(currentIndex + 1, existingIdsInQueue.size)
            } else emptyList()
            
            val newUnplayedPart = (unplayedPart + addedIds).shuffled()
            playedPart + newUnplayedPart
        } else {
            newSongsMap.values
                .asSequence()
                .sortedWith(getComparator())
                .map { it.id }
                .toList()
        }

        if (scope == PlaybackScope.ALL) {
            allQueue = updatedQueue
            if (allCurrentIndex >= allQueue.size) allCurrentIndex = allQueue.size - 1
        } else {
            recentQueue = updatedQueue
            if (recentCurrentIndex >= recentQueue.size) recentCurrentIndex = recentQueue.size - 1
        }
    }

    private fun getComparator(): Comparator<MusicFile> {
        val baseComparator = when (sortCriteria) {
            SortCriteria.FILENAME -> compareBy<MusicFile> { it.title }
            SortCriteria.DATE -> compareBy<MusicFile> { it.dateAdded }
        }
        return if (sortOrder == SortOrder.ASCENDING) baseComparator else baseComparator.reversed()
    }

    private fun updateRecentQueue(newSongsMap: Map<String, MusicFile>, oldSongsMap: Map<String, MusicFile>) {
        val recentSongs = newSongsMap.values
            .asSequence()
            .sortedByDescending { it.dateAdded }
            .take(recentLimit)
            .toList()
            .associateBy { it.id }
            
        updateQueue(PlaybackScope.RECENT, recentSongs, oldSongsMap)
    }

    fun setRecentLimit(limit: Int) {
        if (recentLimit != limit) {
            recentLimit = limit
            val recentSongs = allSongs
                .sortedByDescending { it.dateAdded }
                .take(recentLimit)
            
            recentQueue = if (repeatMode == RepeatMode.SHUFFLE) {
                recentSongs.map { it.id }.shuffled()
            } else {
                recentSongs.sortedWith(getComparator()).map { it.id }
            }
            recentCurrentIndex = -1
            saveState()
        }
    }

    fun toggleSort(criteria: SortCriteria) {
        if (sortCriteria == criteria) {
            sortOrder = if (sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        } else {
            sortCriteria = criteria
            sortOrder = SortOrder.ASCENDING
        }
        
        if (repeatMode == RepeatMode.SEQUENTIAL) {
            val songsToQueue = getActiveSongs()
            val newQueue = songsToQueue.sortedWith(getComparator()).map { it.id }
            
            val currentTrackId = getCurrentTrack()?.id
            if (currentScope == PlaybackScope.ALL) {
                allQueue = newQueue
                allCurrentIndex = currentTrackId?.let { newQueue.indexOf(it) } ?: -1
            } else {
                recentQueue = newQueue
                recentCurrentIndex = currentTrackId?.let { newQueue.indexOf(it) } ?: -1
            }
        }
        saveState()
    }

    private fun getActiveSongs(): List<MusicFile> {
        return if (currentScope == PlaybackScope.ALL) allSongs else {
            allSongs.sortedByDescending { it.dateAdded }.take(recentLimit)
        }
    }

    fun requestPendingScope(scope: PlaybackScope) {
        pendingScope = scope
        isSingleRepeat = false // 1곡 재생 해제
        saveState()
    }

    fun requestPendingRepeatMode(mode: RepeatMode) {
        pendingRepeatMode = mode
        isSingleRepeat = false // 1곡 재생 해제
        saveState()
    }

    fun toggleSingleRepeat() {
        isSingleRepeat = !isSingleRepeat
        saveState()
    }

    fun saveTrackPosition(id: String, position: Long) {
        trackPositions[id] = position
        saveState()
    }

    fun getTrackPosition(id: String): Long {
        return trackPositions[id] ?: 0L
    }

    fun addToHistory(id: String) {
        playedSongIds.add(id)
        saveState()
    }

    fun getPlayedSongIds(): Set<String> = playedSongIds.toSet()

    fun clearAllHistory() {
        playedSongIds.clear()
        allCurrentIndex = -1
        recentCurrentIndex = -1
        saveState()
    }

    private fun promotePendingState() {
        var changed = false
        pendingScope?.let {
            currentScope = it
            pendingScope = null
            changed = true
        }
        pendingRepeatMode?.let {
            repeatMode = it
            pendingRepeatMode = null
            changed = true
        }
        
        if (changed) {
            resetActiveQueue(repeatMode)
        }
    }

    private fun checkAndAutoResetHistory() {
        val currentQueue = getActiveQueue()
        if (currentQueue.isEmpty()) return
        
        val unplayedInQueue = currentQueue.filter { !playedSongIds.contains(it) }
        if (unplayedInQueue.isEmpty()) {
            playedSongIds.clear()
            allCurrentIndex = -1
            recentCurrentIndex = -1
            
            // Apply pending updates when cycle ends
            promotePendingState()
            saveState()
        }
    }

    fun getNextTrack(): MusicFile? {
        if (isSingleRepeat) {
            return getCurrentTrack() ?: findFirstUnplayed()
        }

        val queue = getActiveQueue()
        val index = getActiveIndex()

        if (queue.isEmpty()) return null

        if (index >= (queue.size - 1)) {
            checkAndAutoResetHistory()
            return findFirstUnplayed()
        }

        val nextIndex = index + 1
        val nextTrackId = queue[nextIndex]

        return if (!playedSongIds.contains(nextTrackId)) {
            setActiveIndex(nextIndex)
            saveState()
            allSongs.find { it.id == nextTrackId }
        } else {
            val hasUnplayedAhead = (nextIndex until queue.size).any { !playedSongIds.contains(queue[it]) }
            
            if (hasUnplayedAhead) {
                setActiveIndex(nextIndex)
                saveState()
                allSongs.find { it.id == nextTrackId }
            } else {
                checkAndAutoResetHistory()
                findFirstUnplayed()
            }
        }
    }

    fun getPreviousTrack(): MusicFile? {
        val queue = getActiveQueue()
        var index = getActiveIndex()

        if (queue.isEmpty()) return null

        index--
        if (index < 0) {
            index = 0
        }

        setActiveIndex(index)
        saveState()
        return allSongs.find { it.id == queue[index] }
    }

    private fun findFirstUnplayed(): MusicFile? {
        val queue = getActiveQueue()
        if (queue.isEmpty()) return null
        
        for (i in queue.indices) {
            if (!playedSongIds.contains(queue[i])) {
                setActiveIndex(i)
                saveState()
                return allSongs.find { it.id == queue[i] }
            }
        }
        return null
    }

    fun getCurrentTrack(): MusicFile? {
        val queue = getActiveQueue()
        val index = getActiveIndex()
        if (index in queue.indices) {
            return allSongs.find { it.id == queue[index] }
        }
        return null
    }

    fun skipToNext(): MusicFile? {
        return getNextTrack()
    }

    fun playTrackById(id: String): MusicFile? {
        val queue = getActiveQueue()
        val index = queue.indexOf(id)
        if (index != -1) {
            setActiveIndex(index)
            saveState()
            return allSongs.find { it.id == id }
        }
        return null
    }

    fun resetActiveQueue(mode: RepeatMode) {
        repeatMode = mode
        val songsToQueue = getActiveSongs()

        val newQueue = if (mode == RepeatMode.SHUFFLE) {
            songsToQueue.map { it.id }.shuffled()
        } else {
            songsToQueue.sortedWith(getComparator()).map { it.id }
        }

        if (currentScope == PlaybackScope.ALL) {
            allQueue = newQueue
            allCurrentIndex = -1
        } else {
            recentQueue = newQueue
            recentCurrentIndex = -1
        }
        saveState()
    }

    private fun getActiveQueue() = if (currentScope == PlaybackScope.ALL) allQueue else recentQueue
    private fun getActiveIndex() = if (currentScope == PlaybackScope.ALL) allCurrentIndex else recentCurrentIndex
    private fun setActiveIndex(index: Int) {
        if (currentScope == PlaybackScope.ALL) allCurrentIndex = index else recentCurrentIndex = index
    }

    private fun saveState() {
        prefs.edit().apply {
            putString("all_queue", gson.toJson(allQueue))
            putInt("all_index", allCurrentIndex)
            putString("recent_queue", gson.toJson(recentQueue))
            putInt("recent_index", recentCurrentIndex)
            putInt("recent_limit", recentLimit)
            putString("scope", currentScope.name)
            putString("repeat_mode", repeatMode.name)
            putStringSet("played_history", playedSongIds)
            putString("sort_criteria", sortCriteria.name)
            putString("sort_order", sortOrder.name)
            putBoolean("is_single_repeat", isSingleRepeat)
            putString("pending_scope", pendingScope?.name)
            putString("pending_repeat_mode", pendingRepeatMode?.name)
            putString("track_positions", gson.toJson(trackPositions))
            apply()
        }
    }

    private fun loadState() {
        allQueue = gson.fromJson(prefs.getString("all_queue", "[]"), object : TypeToken<List<String>>() {}.type)
        allCurrentIndex = prefs.getInt("all_index", -1)
        recentQueue = gson.fromJson(prefs.getString("recent_queue", "[]"), object : TypeToken<List<String>>() {}.type)
        recentCurrentIndex = prefs.getInt("recent_index", -1)
        recentLimit = prefs.getInt("recent_limit", 100)
        currentScope = PlaybackScope.valueOf(prefs.getString("scope", PlaybackScope.ALL.name)!!)
        repeatMode = RepeatMode.valueOf(prefs.getString("repeat_mode", RepeatMode.SHUFFLE.name)!!)
        playedSongIds = prefs.getStringSet("played_history", emptySet())?.toMutableSet() ?: mutableSetOf()
        sortCriteria = SortCriteria.valueOf(prefs.getString("sort_criteria", SortCriteria.DATE.name)!!)
        sortOrder = SortOrder.valueOf(prefs.getString("sort_order", SortOrder.DESCENDING.name)!!)
        isSingleRepeat = prefs.getBoolean("is_single_repeat", false)
        
        val pScope = prefs.getString("pending_scope", null)
        pendingScope = pScope?.let { PlaybackScope.valueOf(it) }
        
        val pMode = prefs.getString("pending_repeat_mode", null)
        pendingRepeatMode = pMode?.let { RepeatMode.valueOf(it) }
        
        val positionsJson = prefs.getString("track_positions", "{}")
        trackPositions = gson.fromJson(positionsJson, object : TypeToken<MutableMap<String, Long>>() {}.type) ?: mutableMapOf()
    }
    
    fun getRecentSongs(): List<MusicFile> {
        val recentIds = recentQueue
        return recentIds.mapNotNull { id -> allSongs.find { it.id == id } }
    }

    fun getActiveQueueSongs(): List<MusicFile> {
        val queue = getActiveQueue()
        return queue.mapNotNull { id -> allSongs.find { it.id == id } }
    }
}
