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
    private var recentLimit: Int = 30

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
            // Adjust current index if necessary
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
            
        // For RECENT scope, we filter the queue to only include these recent songs
        updateQueue(PlaybackScope.RECENT, recentSongs, oldSongsMap)
    }

    fun setRecentLimit(limit: Int) {
        if (recentLimit != limit) {
            recentLimit = limit
            // Reset recent queue
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

    fun getNextTrack(): MusicFile? {
        val queue = getActiveQueue()
        var index = getActiveIndex()

        if (queue.isEmpty()) return null

        when (repeatMode) {
            RepeatMode.SINGLE_REPEAT -> {
                // Stay on current index
                if (index == -1) index = 0
            }
            RepeatMode.SHUFFLE, RepeatMode.SEQUENTIAL -> {
                index++
                if (index >= queue.size) {
                    // End of queue reached - Signal UI (handled in Service)
                    return null
                }
            }
        }

        setActiveIndex(index)
        saveState()
        return allSongs.find { it.id == queue[index] }
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
        val queue = getActiveQueue()
        var index = getActiveIndex()
        
        if (queue.isEmpty()) return null
        
        index++
        if (index >= queue.size) {
            // End reached - return null to signal option dialog
            return null
        }
        
        setActiveIndex(index)
        saveState()
        return allSongs.find { it.id == queue[index] }
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
            apply()
        }
    }

    private fun loadState() {
        allQueue = gson.fromJson(prefs.getString("all_queue", "[]"), object : TypeToken<List<String>>() {}.type)
        allCurrentIndex = prefs.getInt("all_index", -1)
        recentQueue = gson.fromJson(prefs.getString("recent_queue", "[]"), object : TypeToken<List<String>>() {}.type)
        recentCurrentIndex = prefs.getInt("recent_index", -1)
        recentLimit = prefs.getInt("recent_limit", 30)
        currentScope = PlaybackScope.valueOf(prefs.getString("scope", PlaybackScope.ALL.name)!!)
        repeatMode = RepeatMode.valueOf(prefs.getString("repeat_mode", RepeatMode.SHUFFLE.name)!!)
    }
    
    fun getRecentSongs(): List<MusicFile> {
        return allSongs.sortedByDescending { it.dateAdded }.take(recentLimit)
    }
}
