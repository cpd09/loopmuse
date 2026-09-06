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

data class EnvState(
    val queue: List<String> = emptyList(),
    val index: Int = -1
)

class PlaybackQueueManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playback_queue", Context.MODE_PRIVATE)
    private val gson = Gson()

    private var allSongs: List<MusicFile> = emptyList()
    
    // Multi-verse State: 4 scopes x 2 modes = 8 states
    private var envStates: MutableMap<String, EnvState> = mutableMapOf()

    // Global Shared History & Positions
    private var playedSongIds: MutableSet<String> = mutableSetOf()
    private var trackPositions: MutableMap<String, Long> = mutableMapOf()

    // Current Active Environment
    var currentScope: PlaybackScope = PlaybackScope.ALL
    var repeatMode: RepeatMode = RepeatMode.SHUFFLE
    
    var sortCriteria: SortCriteria = SortCriteria.DATE
    var sortOrder: SortOrder = SortOrder.DESCENDING
    var isSingleRepeat: Boolean = false
    var recentLimit: Int = 100

    init {
        loadState()
    }

    private fun getEnvKey(scope: PlaybackScope, mode: RepeatMode): String {
        return "${scope.name}_${mode.name}"
    }

    private fun getCurrentEnvKey() = getEnvKey(currentScope, repeatMode)

    fun setAllSongs(songs: List<MusicFile>) {
        allSongs = songs
        // Re-generate all 8 queues to ensure they are up to date with the new library
        refreshAllQueues()
        saveState()
    }

    private fun refreshAllQueues() {
        val scopes = listOf(PlaybackScope.ALL, PlaybackScope.RECENT)
        val modes = listOf(RepeatMode.SHUFFLE, RepeatMode.SEQUENTIAL)
        
        scopes.forEach { s ->
            modes.forEach { m ->
                val key = getEnvKey(s, m)
                val existing = envStates[key]
                val songsForScope = getSongsForScope(s)
                
                val newQueue = if (m == RepeatMode.SHUFFLE) {
                    // Try to maintain existing shuffle order if possible, or just shuffle
                    songsForScope.map { it.id }.shuffled()
                } else {
                    songsForScope.sortedWith(getComparator()).map { it.id }
                }
                
                val newIndex = if (existing != null && existing.index != -1) {
                    val currentId = existing.queue.getOrNull(existing.index)
                    newQueue.indexOf(currentId).coerceAtLeast(-1)
                } else -1
                
                envStates[key] = EnvState(newQueue, newIndex)
            }
        }
    }

    fun resetToDefault() {
        currentScope = PlaybackScope.ALL
        repeatMode = RepeatMode.SEQUENTIAL
        sortCriteria = SortCriteria.DATE
        sortOrder = SortOrder.DESCENDING
        isSingleRepeat = false
        
        // Regenerate everything clean
        envStates.clear()
        refreshAllQueues()
        
        // Force first song selection
        val key = getCurrentEnvKey()
        val state = envStates[key]
        if (state != null && state.queue.isNotEmpty()) {
            envStates[key] = state.copy(index = 0)
        }
        
        saveState()
    }

    private fun getSongsForScope(scope: PlaybackScope): List<MusicFile> {
        return if (scope == PlaybackScope.ALL) allSongs else {
            allSongs.asSequence().sortedByDescending { it.dateAdded }.take(recentLimit).toList()
        }
    }

    private fun getComparator(): Comparator<MusicFile> {
        val baseComparator = when (sortCriteria) {
            SortCriteria.FILENAME -> compareBy<MusicFile> { it.title }
            SortCriteria.DATE -> compareBy<MusicFile> { it.dateAdded }
        }
        return if (sortOrder == SortOrder.ASCENDING) baseComparator else baseComparator.reversed()
    }

    fun switchContext(scope: PlaybackScope, mode: RepeatMode) {
        currentScope = scope
        repeatMode = mode
        isSingleRepeat = false // Clear on context switch
        
        val key = getCurrentEnvKey()
        if (!envStates.containsKey(key)) {
            refreshAllQueues() // Initialize if missing
        }
        saveState()
    }

    fun toggleSort(criteria: SortCriteria) {
        if (sortCriteria == criteria) {
            sortOrder = if (sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        } else {
            sortCriteria = criteria
            sortOrder = SortOrder.ASCENDING
        }
        
        // Refresh sequential queues with new sort
        refreshAllQueues()
        saveState()
    }

    fun updateRecentLimit(limit: Int) {
        if (recentLimit != limit) {
            recentLimit = limit
            refreshAllQueues()
            saveState()
        }
    }

    fun toggleSingleRepeat() {
        isSingleRepeat = !isSingleRepeat
        saveState()
    }

    fun addToHistory(id: String) {
        playedSongIds.add(id)
        saveState()
    }

    fun getPlayedSongIds(): Set<String> = playedSongIds.toSet()

    fun clearAllHistory() {
        playedSongIds.clear()
        // Reset all indices in all environments to start fresh
        envStates.keys.forEach { key ->
            envStates[key] = envStates[key]?.copy(index = -1) ?: EnvState()
        }
        saveState()
    }

    private fun checkAndAutoResetHistory() {
        val state = envStates[getCurrentEnvKey()] ?: return
        val unplayedInQueue = state.queue.filter { !playedSongIds.contains(it) }
        
        if (unplayedInQueue.isEmpty() && state.queue.isNotEmpty()) {
            playedSongIds.clear()
            envStates.keys.forEach { key ->
                envStates[key] = envStates[key]?.copy(index = -1) ?: EnvState()
            }
            saveState()
        }
    }

    fun getNextTrack(): MusicFile? {
        if (isSingleRepeat) return getCurrentTrack() ?: findFirstUnplayed()

        val key = getCurrentEnvKey()
        val state = envStates[key] ?: return null
        val queue = state.queue
        val index = state.index

        if (queue.isEmpty()) return null

        // Try to find next unplayed in the CURRENT queue
        var foundIndex = -1
        for (i in (index + 1) until queue.size) {
            if (!playedSongIds.contains(queue[i])) {
                foundIndex = i
                break
            }
        }

        return if (foundIndex != -1) {
            envStates[key] = state.copy(index = foundIndex)
            saveState()
            allSongs.find { it.id == queue[foundIndex] }
        } else {
            checkAndAutoResetHistory()
            findFirstUnplayed()
        }
    }

    fun getPreviousTrack(): MusicFile? {
        val key = getCurrentEnvKey()
        val state = envStates[key] ?: return null
        val queue = state.queue
        var index = state.index

        if (queue.isEmpty()) return null

        index = (index - 1).coerceAtLeast(0)
        envStates[key] = state.copy(index = index)
        saveState()
        return allSongs.find { it.id == queue[index] }
    }

    private fun findFirstUnplayed(): MusicFile? {
        val key = getCurrentEnvKey()
        val state = envStates[key] ?: return null
        val queue = state.queue
        
        for (i in queue.indices) {
            if (!playedSongIds.contains(queue[i])) {
                envStates[key] = state.copy(index = i)
                saveState()
                return allSongs.find { it.id == queue[i] }
            }
        }
        return null
    }

    fun getCurrentTrack(): MusicFile? {
        val state = envStates[getCurrentEnvKey()] ?: return null
        return if (state.index in state.queue.indices) {
            allSongs.find { it.id == state.queue[state.index] }
        } else null
    }

    fun skipToNext(): MusicFile? = getNextTrack()

    fun playTrackById(id: String): MusicFile? {
        val key = getCurrentEnvKey()
        val state = envStates[key] ?: return null
        val idx = state.queue.indexOf(id)
        if (idx != -1) {
            envStates[key] = state.copy(index = idx)
            saveState()
            return allSongs.find { it.id == id }
        }
        return null
    }

    fun saveTrackPosition(id: String, position: Long) {
        trackPositions[id] = position
        saveState()
    }

    fun getTrackPosition(id: String): Long = trackPositions[id] ?: 0L

    private fun saveState() {
        prefs.edit().apply {
            putString("env_states", gson.toJson(envStates))
            putString("scope", currentScope.name)
            putString("repeat_mode", repeatMode.name)
            putStringSet("played_history", playedSongIds)
            putString("sort_criteria", sortCriteria.name)
            putString("sort_order", sortOrder.name)
            putBoolean("is_single_repeat", isSingleRepeat)
            putInt("recent_limit", recentLimit)
            putString("track_positions", gson.toJson(trackPositions))
            apply()
        }
    }

    private fun loadState() {
        val statesJson = prefs.getString("env_states", "{}")
        envStates = gson.fromJson(statesJson, object : TypeToken<MutableMap<String, EnvState>>() {}.type) ?: mutableMapOf()
        
        currentScope = PlaybackScope.valueOf(prefs.getString("scope", PlaybackScope.ALL.name)!!)
        repeatMode = RepeatMode.valueOf(prefs.getString("repeat_mode", RepeatMode.SHUFFLE.name)!!)
        playedSongIds = prefs.getStringSet("played_history", emptySet())?.toMutableSet() ?: mutableSetOf()
        sortCriteria = SortCriteria.valueOf(prefs.getString("sort_criteria", SortCriteria.DATE.name)!!)
        sortOrder = SortOrder.valueOf(prefs.getString("sort_order", SortOrder.DESCENDING.name)!!)
        isSingleRepeat = prefs.getBoolean("is_single_repeat", false)
        recentLimit = prefs.getInt("recent_limit", 100)
        
        val positionsJson = prefs.getString("track_positions", "{}")
        trackPositions = gson.fromJson(positionsJson, object : TypeToken<MutableMap<String, Long>>() {}.type) ?: mutableMapOf()
    }
    
    fun getActiveQueueSongs(): List<MusicFile> {
        val state = envStates[getCurrentEnvKey()] ?: return emptyList()
        return state.queue.mapNotNull { id -> allSongs.find { it.id == id } }
    }

    fun getAllSongs(): List<MusicFile> = allSongs
}
