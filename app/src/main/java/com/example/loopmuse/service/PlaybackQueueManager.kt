package com.example.loopmuse.service

import android.content.Context
import android.content.SharedPreferences
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.PlaybackScope
import com.example.loopmuse.data.RepeatMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlaybackQueueManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playback_queue", Context.MODE_PRIVATE)
    private val gson = Gson()

    private var allSongs: List<MusicFile> = emptyList()
    
    // State for ALL scope
    private var allQueue: List<String> = emptyList() // IDs
    private var allCurrentIndex: Int = -1
    
    // State for RECENT scope
    private var recentQueue: List<String> = emptyList() // IDs
    private var recentCurrentIndex: Int = -1
    private var recentLimit: Int = 50 // Default changed to 50

    // Global Played History (across scopes)
    private var playedSongIds: MutableSet<String> = mutableSetOf()

    var currentScope: PlaybackScope = PlaybackScope.ALL
    var repeatMode: RepeatMode = RepeatMode.SHUFFLE

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

    private fun updateQueue(scope: PlaybackScope, newSongsMap: Map<String, MusicFile>, oldSongsMap: Map<String, MusicFile>) {
        val currentQueue = if (scope == PlaybackScope.ALL) allQueue else recentQueue
        val currentIndex = if (scope == PlaybackScope.ALL) allCurrentIndex else recentCurrentIndex
        
        val newIds = newSongsMap.keys
        val existingIdsInQueue = currentQueue.filter { newIds.contains(it) }
        val addedIds = newIds.filter { !oldSongsMap.containsKey(it) }
        
        // Smart Insertion (4-2): Add new IDs to the unplayed part of the queue
        val updatedQueue = if (repeatMode == RepeatMode.SHUFFLE) {
            val playedPart = if (currentIndex >= 0 && currentIndex < existingIdsInQueue.size) {
                existingIdsInQueue.subList(0, currentIndex + 1)
            } else emptyList()
            
            val unplayedPart = if (currentIndex + 1 < existingIdsInQueue.size) {
                existingIdsInQueue.subList(currentIndex + 1, existingIdsInQueue.size)
            } else emptyList()
            
            val newUnplayedPart = (unplayedPart + addedIds).shuffled()
            playedPart + newUnplayedPart
        } else {
            // Sequential: Keep the order of newSongsMap (sorted by path/name)
            newSongsMap.values
                .sortedWith(compareBy({ it.folder }, { it.title }))
                .map { it.id }
        }

        if (scope == PlaybackScope.ALL) {
            allQueue = updatedQueue
            if (allCurrentIndex >= allQueue.size) allCurrentIndex = allQueue.size - 1
        } else {
            recentQueue = updatedQueue
            if (recentCurrentIndex >= recentQueue.size) recentCurrentIndex = recentQueue.size - 1
        }
    }

    private fun updateRecentQueue(newSongsMap: Map<String, MusicFile>, oldSongsMap: Map<String, MusicFile>) {
        val recentSongs = newSongsMap.values
            .sortedByDescending { it.dateAdded }
            .take(recentLimit)
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
                recentSongs.sortedWith(compareBy({ it.folder }, { it.title })).map { it.id }
            }
            recentCurrentIndex = -1
            saveState()
        }
    }

    fun addToHistory(id: String) {
        playedSongIds.add(id)
        saveState()
    }

    fun isPlayed(id: String): Boolean = playedSongIds.contains(id)

    fun getPlayedSongIds(): Set<String> = playedSongIds.toSet()

    fun clearAllHistory() {
        playedSongIds.clear()
        allCurrentIndex = -1
        recentCurrentIndex = -1
        saveState()
    }

    private fun checkAndAutoResetHistory() {
        val currentQueue = getActiveQueue()
        if (currentQueue.isEmpty()) return
        
        // If all songs in current queue are in history, clear history for this cycle
        val unplayedInQueue = currentQueue.filter { !playedSongIds.contains(it) }
        if (unplayedInQueue.isEmpty()) {
            // Cycle finished. Clear history.
            playedSongIds.clear()
            // Reset current indices to start over
            allCurrentIndex = -1
            recentCurrentIndex = -1
            saveState()
        }
    }

    fun getNextTrack(): MusicFile? {
        if (repeatMode == RepeatMode.SINGLE_REPEAT) {
            return getCurrentTrack() ?: findFirstUnplayed()
        }

        val queue = getActiveQueue()
        val index = getActiveIndex()

        if (queue.isEmpty()) return null

        // If we are at the end of the queue, try to reset or stop
        if (index >= queue.size - 1) {
            checkAndAutoResetHistory()
            return findFirstUnplayed()
        }

        // --- NEW LOGIC FOR MANEUVERING AFTER "PREVIOUS" ---
        val nextIndex = index + 1
        val nextTrackId = queue[nextIndex]

        return if (!playedSongIds.contains(nextTrackId)) {
            // Option A: The very next song is UNPLAYED.
            // This is the "Frontier". We play it and original jump logic resumes from here naturally.
            setActiveIndex(nextIndex)
            saveState()
            allSongs.find { it.id == nextTrackId }
        } else {
            // Option B: The next song was ALREADY PLAYED.
            // We only play it sequentially IF the user moved back manually.
            // Otherwise (normal flow), we jump to the next unplayed song.
            
            // To satisfy: "If we went back, play sequentially until we hit an unplayed song"
            // We check if there are ANY unplayed songs FURTHER ahead in the queue.
            val hasUnplayedAhead = (nextIndex until queue.size).any { !playedSongIds.contains(queue[it]) }
            
            if (hasUnplayedAhead) {
                // We are behind the frontier. Just go to the NEXT one (even if played).
                setActiveIndex(nextIndex)
                saveState()
                allSongs.find { it.id == nextTrackId }
            } else {
                // No unplayed songs ahead! This means we finished the cycle.
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
            index = 0 // Stay at beginning
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
        val songsToQueue = if (currentScope == PlaybackScope.ALL) allSongs else {
            allSongs.sortedByDescending { it.dateAdded }.take(recentLimit)
        }

        val newQueue = if (mode == RepeatMode.SHUFFLE) {
            songsToQueue.map { it.id }.shuffled()
        } else {
            songsToQueue.sortedWith(compareBy({ it.folder }, { it.title })).map { it.id }
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
            apply()
        }
    }

    private fun loadState() {
        allQueue = gson.fromJson(prefs.getString("all_queue", "[]"), object : TypeToken<List<String>>() {}.type)
        allCurrentIndex = prefs.getInt("all_index", -1)
        recentQueue = gson.fromJson(prefs.getString("recent_queue", "[]"), object : TypeToken<List<String>>() {}.type)
        recentCurrentIndex = prefs.getInt("recent_index", -1)
        recentLimit = prefs.getInt("recent_limit", 50)
        currentScope = PlaybackScope.valueOf(prefs.getString("scope", PlaybackScope.ALL.name)!!)
        repeatMode = RepeatMode.valueOf(prefs.getString("repeat_mode", RepeatMode.SHUFFLE.name)!!)
        playedSongIds = prefs.getStringSet("played_history", emptySet())?.toMutableSet() ?: mutableSetOf()
    }
    
    fun getRecentSongs(): List<MusicFile> {
        val recentIds = recentQueue
        return recentIds.mapNotNull { id -> allSongs.find { it.id == id } }
    }

    fun getActiveQueueSongs(): List<MusicFile> {
        val queue = getActiveQueue()
        return queue.mapNotNull { id -> allSongs.find { it.id == id } }
    }

    fun getAllSongs(): List<MusicFile> = allSongs
}
