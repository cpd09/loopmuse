package com.example.loopmuse.service

import android.content.Context
import android.content.SharedPreferences
import com.example.loopmuse.data.MusicFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class SortCriteria {
    FILENAME, DATE, ARTIST, ALBUM
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

enum class PlaylistType {
    PERMANENT, TEMPORARY
}

/**
 * Represents the state of a specific playlist.
 */
data class PlaylistState(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val queue: List<String> = emptyList(),
    val originalQueue: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val history: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val isRandom: Boolean = false,
    val isSmart: Boolean = true,
    val isSingleRepeat: Boolean = false,
    val isLikedFilter: Boolean = false,
    val sortCriteria: SortCriteria = SortCriteria.DATE,
    val sortOrder: SortOrder = SortOrder.DESCENDING
)

class PlaybackQueueManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playback_queue_v5", Context.MODE_PRIVATE)
    private val gson = Gson()

    private var allSongs: List<MusicFile> = emptyList()
    private var likedFingerprints: Set<String> = emptySet()
    
    // Playlists: id -> state
    private var playlists: MutableMap<String, PlaylistState> = mutableMapOf()
    private var currentPlaylistId: String = "ALL"

    // Selection State
    var selectedIds: MutableSet<String> = mutableSetOf()
    var isSelectionMode: Boolean = false
    var isSelectionPlayback: Boolean = false

    init {
        loadState()
        if (!playlists.containsKey("ALL")) {
            playlists["ALL"] = PlaylistState("ALL", "전체곡", PlaylistType.PERMANENT)
        }
    }

    fun setAllSongs(songs: List<MusicFile>) {
        allSongs = songs
        refreshAllQueues()
        saveState()
    }

    fun setLikedFingerprints(set: Set<String>) {
        likedFingerprints = set
        val current = playlists[currentPlaylistId]
        if (current?.isLikedFilter == true) {
            updatePlaylistQueue(currentPlaylistId)
            saveState()
        }
    }

    private fun refreshAllQueues() {
        playlists.keys.forEach { id ->
            updatePlaylistQueue(id)
        }
    }

    private fun updatePlaylistQueue(id: String) {
        val state = playlists[id] ?: return
        val baseQueueIds = (if (!state.originalQueue.isNullOrEmpty()) state.originalQueue else state.queue) ?: emptyList()
        var songsInLibrary = if (id == "ALL") allSongs else {
            allSongs.filter { song -> baseQueueIds.contains(song.id) }
        }
        
        if (state.isLikedFilter) {
            songsInLibrary = songsInLibrary.filter { song ->
                likedFingerprints.contains(song.fingerprintId)
            }
        }
        
        val newQueue = if (state.isRandom) {
            val existingIds = (state.queue ?: emptyList()).filter { qId -> songsInLibrary.any { it.id == qId } }
            val newIds = songsInLibrary.map { it.id }.filter { nId -> !existingIds.contains(nId) }.shuffled()
            existingIds + newIds
        } else {
            songsInLibrary.asSequence()
                .sortedWith(getComparator(state.sortCriteria, state.sortOrder))
                .map { it.id }
                .toList()
        }

        var newIndex = if (state.currentIndex != -1) {
            val currentId = state.queue?.getOrNull(state.currentIndex)
            newQueue.indexOf(currentId).coerceAtLeast(-1)
        } else -1

        if (newIndex == -1 && newQueue.isNotEmpty()) newIndex = 0

        val newHistory = (state.history ?: emptyList()).filter { hId -> songsInLibrary.any { it.id == hId } }
        val finalHistory = if (newIndex != -1 && newHistory.isEmpty() && newQueue.isNotEmpty()) {
            listOf(newQueue[newIndex])
        } else newHistory

        val finalHistoryIndex = if (newIndex != -1 && state.historyIndex == -1 && newQueue.isNotEmpty()) {
            0
        } else if (state.historyIndex != -1) {
            val currentHistId = state.history?.getOrNull(state.historyIndex)
            finalHistory.indexOf(currentHistId).coerceAtLeast(-1)
        } else -1

        playlists[id] = state.copy(
            queue = newQueue,
            originalQueue = state.originalQueue ?: emptyList(),
            currentIndex = newIndex,
            history = finalHistory,
            historyIndex = finalHistoryIndex
        )
    }

    private fun getComparator(criteria: SortCriteria, order: SortOrder): Comparator<MusicFile> {
        val base = when (criteria) {
            SortCriteria.FILENAME -> compareBy<MusicFile> { it.file.name }
            SortCriteria.DATE -> compareBy<MusicFile> { it.dateAdded }
            SortCriteria.ARTIST -> compareBy<MusicFile> { it.artist }
            SortCriteria.ALBUM -> compareBy<MusicFile> { it.album }
        }
        return if (order == SortOrder.ASCENDING) base else base.reversed()
    }

    fun globalReset() {
        playlists.keys.forEach { id ->
            val p = playlists[id]
            if (p != null) {
                playlists[id] = p.copy(
                    queue = p.queue ?: emptyList(),
                    originalQueue = p.originalQueue ?: emptyList(),
                    currentIndex = -1,
                    history = emptyList(),
                    historyIndex = -1,
                    isRandom = false,
                    isSmart = true,
                    isSingleRepeat = false,
                    isLikedFilter = false,
                    sortCriteria = SortCriteria.DATE,
                    sortOrder = SortOrder.DESCENDING
                )
                updatePlaylistQueue(id)
            }
        }
        selectedIds.clear()
        isSelectionMode = false
        isSelectionPlayback = false
        saveState()
    }

    fun localReset() {
        val id = currentPlaylistId
        val p = playlists[id] ?: return
        
        // 1. Reset standard options to default
        val resetState = p.copy(
            queue = p.queue ?: emptyList(),
            originalQueue = p.originalQueue ?: emptyList(),
            currentIndex = -1,
            history = emptyList(),
            historyIndex = -1,
            isRandom = false,
            isSmart = true,
            isSingleRepeat = false,
            isLikedFilter = false,
            sortCriteria = SortCriteria.DATE,
            sortOrder = SortOrder.DESCENDING
        )
        
        // 2. Apply reset
        playlists[id] = resetState
        
        // 3. Re-sort and find the new first track for Standby
        updatePlaylistQueue(id)
        
        // 4. Force standby on the first track of the newly sorted list
        val updatedP = playlists[id]
        if (updatedP != null && updatedP.queue.isNotEmpty()) {
            playlists[id] = updatedP.copy(
                queue = updatedP.queue,
                originalQueue = updatedP.originalQueue ?: emptyList(),
                currentIndex = 0,
                history = listOf(updatedP.queue[0]),
                historyIndex = 0
            )
        }

        selectedIds.clear()
        isSelectionMode = false
        isSelectionPlayback = false
        saveState()
    }

    fun resetToDefault() {
        currentPlaylistId = "ALL"
        playlists.clear()
        playlists["ALL"] = PlaylistState("ALL", "전체곡", PlaylistType.PERMANENT, isSmart = true, isRandom = false)
        refreshAllQueues()
        val state = playlists["ALL"]
        if (state != null && state.queue.isNotEmpty()) {
            playlists["ALL"] = state.copy(currentIndex = 0, history = listOf(state.queue[0]), historyIndex = 0)
        }
        selectedIds.clear()
        isSelectionMode = false
        isSelectionPlayback = false
        saveState()
    }

    fun getCurrentPlaylist(): PlaylistState? = playlists[currentPlaylistId]
    fun getAllPlaylists(): List<PlaylistState> = playlists.values.sortedBy { if (it.id == "ALL") 0 else 1 }.toList()
    
    fun switchPlaylist(id: String) {
        if (playlists.containsKey(id)) {
            currentPlaylistId = id
            saveState()
        }
    }

    fun addCustomPlaylist(name: String, ids: List<String>) {
        val id = "USER_${System.currentTimeMillis()}"
        playlists[id] = PlaylistState(id, name, PlaylistType.PERMANENT, queue = ids, originalQueue = ids)
        updatePlaylistQueue(id)
        saveState()
    }

    fun updateCustomPlaylist(id: String, name: String, ids: List<String>) {
        val existing = playlists[id] ?: return
        playlists[id] = existing.copy(name = name, queue = ids, originalQueue = ids)
        updatePlaylistQueue(id)
        saveState()
    }

    fun deletePlaylist(id: String) {
        if (id != "ALL") {
            playlists.remove(id)
            if (currentPlaylistId == id) currentPlaylistId = "ALL"
            saveState()
        }
    }

    fun setTemporaryPlaylist(name: String, ids: List<String>) {
        val id = "TEMP"
        playlists[id] = PlaylistState(id, name, PlaylistType.TEMPORARY, queue = ids, originalQueue = ids)
        currentPlaylistId = id
        updatePlaylistQueue(id)
        saveState()
    }

    fun toggleLikedFilter(): Boolean {
        val p = playlists[currentPlaylistId] ?: return false
        val newLikedFilter = !p.isLikedFilter
        
        if (newLikedFilter) {
            val baseQueueIds = (if (!p.originalQueue.isNullOrEmpty()) p.originalQueue else p.queue) ?: emptyList()
            val songsInPlaylist = if (currentPlaylistId == "ALL") allSongs else {
                allSongs.filter { song -> baseQueueIds.contains(song.id) }
            }
            val likedCount = songsInPlaylist.count { likedFingerprints.contains(it.fingerprintId) }
            if (likedCount == 0) {
                return false
            }
        }
        
        val original = if (p.originalQueue.isNullOrEmpty() && currentPlaylistId != "ALL") (p.queue ?: emptyList()) else (p.originalQueue ?: emptyList())
        playlists[currentPlaylistId] = p.copy(
            isLikedFilter = newLikedFilter,
            originalQueue = original,
            queue = p.queue ?: emptyList()
        )
        updatePlaylistQueue(currentPlaylistId)
        saveState()
        return true
    }

    fun toggleRandom() {
        val p = playlists[currentPlaylistId] ?: return
        playlists[currentPlaylistId] = p.copy(
            isRandom = !p.isRandom,
            queue = p.queue ?: emptyList(),
            originalQueue = p.originalQueue ?: emptyList()
        )
        updatePlaylistQueue(currentPlaylistId)
        saveState()
    }

    fun toggleSmart() {
        val p = playlists[currentPlaylistId] ?: return
        playlists[currentPlaylistId] = p.copy(
            isSmart = !p.isSmart,
            queue = p.queue ?: emptyList(),
            originalQueue = p.originalQueue ?: emptyList()
        )
        saveState()
    }

    fun toggleSingleRepeat() {
        val p = playlists[currentPlaylistId] ?: return
        playlists[currentPlaylistId] = p.copy(
            isSingleRepeat = !p.isSingleRepeat,
            queue = p.queue ?: emptyList(),
            originalQueue = p.originalQueue ?: emptyList()
        )
        saveState()
    }

    fun toggleSort(criteria: SortCriteria) {
        val p = playlists[currentPlaylistId] ?: return
        val newOrder = if (p.sortCriteria == criteria) {
            if (p.sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        } else SortOrder.ASCENDING
        playlists[currentPlaylistId] = p.copy(
            sortCriteria = criteria,
            sortOrder = newOrder,
            queue = p.queue ?: emptyList(),
            originalQueue = p.originalQueue ?: emptyList()
        )
        updatePlaylistQueue(currentPlaylistId)
        saveState()
    }

    fun getNextTrack(isManual: Boolean = false): MusicFile? {
        val state = playlists[currentPlaylistId] ?: return null
        
        // --- LEVEL 3: 1-Song Repeat (Highest Priority) ---
        // Even in Selection Playback, if 1-song repeat is on, stay on the current song (auto only)
        if (state.isSingleRepeat && !isManual) return getCurrentTrack()

        // --- LEVEL 2: Selection Playback ---
        if (isSelectionPlayback && selectedIds.isNotEmpty()) {
            val selectedList = state.queue.filter { selectedIds.contains(it) }
            if (selectedList.isEmpty()) { isSelectionPlayback = false }
            else {
                val currentId = state.queue.getOrNull(state.currentIndex)
                val selIdx = selectedList.indexOf(currentId)
                val nextId = selectedList[(selIdx + 1) % selectedList.size]
                return updateCurrentTrackById(nextId)
            }
        }

        // --- LEVEL 1 & Base Navigation (Option A) ---
        if (state.historyIndex < state.history.size - 1) {
            val nextIndex = state.historyIndex + 1
            val nextId = state.history[nextIndex]
            return updateCurrentTrackById(nextId, isHistoryMove = true, targetHistoryIndex = nextIndex)
        }

        var nextId: String? = null
        val queue = state.queue
        if (queue.isEmpty()) return null

        if (state.isRandom) {
            if (state.isSmart) {
                val histSet = state.history.toSet()
                val candidates = queue.filter { !histSet.contains(it) }
                if (candidates.isNotEmpty()) {
                    nextId = candidates.random()
                } else {
                    // Smart Random: All songs played. Auto-reset history and loop.
                    playlists[currentPlaylistId] = state.copy(
                        history = emptyList(),
                        historyIndex = -1,
                        queue = state.queue ?: emptyList(),
                        originalQueue = state.originalQueue ?: emptyList()
                    )
                    saveState()
                    nextId = queue.random()
                }
            } else {
                nextId = queue.random()
            }
        } else {
            if (state.isSmart) {
                val histSet = state.history.toSet()
                for (i in (state.currentIndex + 1) until queue.size) {
                    if (!histSet.contains(queue[i])) {
                        nextId = queue[i]
                        break
                    }
                }
                if (nextId == null) {
                    nextId = queue.firstOrNull { !histSet.contains(it) }
                }
                
                if (nextId == null) {
                    // Smart Sequential: All songs played. Auto-reset history and loop.
                    playlists[currentPlaylistId] = state.copy(
                        history = emptyList(),
                        historyIndex = -1,
                        queue = state.queue ?: emptyList(),
                        originalQueue = state.originalQueue ?: emptyList()
                    )
                    saveState()
                    nextId = queue.firstOrNull()
                }
            } else {
                nextId = queue[(state.currentIndex + 1) % queue.size]
            }
        }
        return nextId?.let { updateCurrentTrackById(it) }
    }

    fun getPreviousTrack(): MusicFile? {
        val state = playlists[currentPlaylistId] ?: return null
        if (isSelectionPlayback && selectedIds.isNotEmpty()) {
            val selectedList = state.queue.filter { selectedIds.contains(it) }
            val currentId = state.queue.getOrNull(state.currentIndex)
            val selIdx = selectedList.indexOf(currentId)
            val prevId = selectedList[if (selIdx <= 0) selectedList.size - 1 else selIdx - 1]
            return updateCurrentTrackById(prevId)
        }
        if (state.historyIndex > 0) {
            val prevIndex = state.historyIndex - 1
            val prevId = state.history[prevIndex]
            return updateCurrentTrackById(prevId, isHistoryMove = true, targetHistoryIndex = prevIndex)
        }
        return getCurrentTrack()
    }

    private fun updateCurrentTrackById(id: String, isHistoryMove: Boolean = false, targetHistoryIndex: Int = -1): MusicFile? {
        val state = playlists[currentPlaylistId] ?: return null
        val qIdx = (state.queue ?: emptyList()).indexOf(id)
        if (qIdx == -1) return null

        val currentHistory = state.history ?: emptyList()
        val newHistory: List<String>
        val newHistoryIdx: Int
        if (isHistoryMove && targetHistoryIndex != -1) {
            newHistory = currentHistory
            newHistoryIdx = targetHistoryIndex
        } else {
            val lastId = currentHistory.lastOrNull()
            newHistory = if (id != lastId) currentHistory + id else currentHistory
            newHistoryIdx = newHistory.size - 1
        }
        playlists[currentPlaylistId] = state.copy(
            currentIndex = qIdx,
            history = newHistory,
            historyIndex = newHistoryIdx,
            queue = state.queue ?: emptyList(),
            originalQueue = state.originalQueue ?: emptyList()
        )
        saveState()
        return allSongs.find { it.id == id }
    }

    fun getCurrentTrack(): MusicFile? {
        val s = playlists[currentPlaylistId] ?: return null
        return if (s.currentIndex in s.queue.indices) allSongs.find { it.id == s.queue[s.currentIndex] } else null
    }

    fun playTrackById(id: String): MusicFile? = updateCurrentTrackById(id)

    fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
        if (!isSelectionMode) {
            clearSelection()
        }
    }

    fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) { isSelectionMode = false; isSelectionPlayback = false }
    }

    fun selectAll() {
        val queue = playlists[currentPlaylistId]?.queue ?: emptyList()
        selectedIds.clear()
        selectedIds.addAll(queue)
        isSelectionMode = true
    }

    fun clearSelection() { selectedIds.clear(); isSelectionMode = false; isSelectionPlayback = false }

    private fun saveState() {
        prefs.edit().apply {
            putString("playlists", gson.toJson(playlists.filter { it.value.type == PlaylistType.PERMANENT }))
            putString("current_id", currentPlaylistId)
            apply()
        }
    }

    private fun loadState() {
        val json = prefs.getString("playlists", "{}")
        try {
            val rawPlaylists: Map<String, PlaylistState>? = gson.fromJson(json, object : TypeToken<Map<String, PlaylistState>>() {}.type)
            if (rawPlaylists != null) {
                playlists = rawPlaylists.mapValues { entry ->
                    val p = entry.value
                    val safeQueue = p.queue ?: emptyList()
                    val safeOrig = if (p.originalQueue.isNullOrEmpty() && entry.key != "ALL") safeQueue else (p.originalQueue ?: emptyList())
                    PlaylistState(
                        id = p.id ?: entry.key,
                        name = p.name ?: "Unknown",
                        type = p.type ?: PlaylistType.PERMANENT,
                        queue = safeQueue,
                        originalQueue = safeOrig,
                        currentIndex = p.currentIndex,
                        history = p.history ?: emptyList(),
                        historyIndex = p.historyIndex,
                        isRandom = p.isRandom,
                        isSmart = p.isSmart,
                        isSingleRepeat = p.isSingleRepeat,
                        isLikedFilter = p.isLikedFilter,
                        sortCriteria = p.sortCriteria ?: SortCriteria.DATE,
                        sortOrder = p.sortOrder ?: SortOrder.DESCENDING
                    )
                }.toMutableMap()
            } else {
                playlists = mutableMapOf()
            }
        } catch (e: Exception) {
            playlists = mutableMapOf()
        }
        currentPlaylistId = prefs.getString("current_id", "ALL") ?: "ALL"
    }

    fun getActiveQueueSongs(): List<MusicFile> {
        val s = playlists[currentPlaylistId] ?: return emptyList()
        return s.queue.mapNotNull { qId -> allSongs.find { it.id == qId } }
    }

    fun getPlayedSongIds(): Set<String> = playlists[currentPlaylistId]?.history?.toSet() ?: emptySet()
}
