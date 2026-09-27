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
    val likedCurrentId: String? = null,
    val likedResumePositionMs: Long = 0L,
    val temporaryHistory: List<String> = emptyList(),
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
    private var selectionCurrentId: String? = null

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
        if (current?.isLikedFilter == true && allSongs.isNotEmpty() && playbackQueue(current).isEmpty()) {
            playlists[currentPlaylistId] = current.copy(isLikedFilter = false, likedCurrentId = null,
                likedResumePositionMs = 0L)
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
        val baseQueueIds = if (state.originalQueue.isNotEmpty()) state.originalQueue else state.queue
        val songsInLibrary = if (id == "ALL") allSongs else {
            allSongs.filter { song -> baseQueueIds.contains(song.id) }
        }
        
        val newQueue = if (state.isRandom) {
            val existingIds = state.queue.filter { qId -> songsInLibrary.any { it.id == qId } }
            val newIds = songsInLibrary.map { it.id }.filter { nId -> !existingIds.contains(nId) }.shuffled()
            existingIds + newIds
        } else {
            songsInLibrary.asSequence()
                .sortedWith(getComparator(state.sortCriteria, state.sortOrder))
                .map { it.id }
                .toList()
        }

        var newIndex = if (state.currentIndex != -1) {
            val currentId = state.queue.getOrNull(state.currentIndex)
            newQueue.indexOf(currentId).coerceAtLeast(-1)
        } else -1

        if (newIndex == -1 && newQueue.isNotEmpty()) newIndex = 0

        val newHistory = state.history.filter { hId -> songsInLibrary.any { it.id == hId } }
        val finalHistory = if (newIndex != -1 && newHistory.isEmpty() && newQueue.isNotEmpty()) {
            listOf(newQueue[newIndex])
        } else newHistory

        val finalHistoryIndex = if (newIndex != -1 && state.historyIndex == -1 && newQueue.isNotEmpty()) {
            0
        } else if (state.historyIndex != -1) {
            val currentHistId = state.history.getOrNull(state.historyIndex)
            finalHistory.indexOf(currentHistId).coerceAtLeast(-1)
        } else -1

        playlists[id] = state.copy(
            queue = newQueue,
            currentIndex = newIndex,
            history = finalHistory,
            historyIndex = finalHistoryIndex,
            likedCurrentId = state.likedCurrentId?.takeIf { it in newQueue },
            temporaryHistory = state.temporaryHistory.filter { it in newQueue }
        )
        if (id == currentPlaylistId) {
            selectedIds.retainAll(newQueue.toSet())
            if (selectedIds.isEmpty()) {
                isSelectionMode = false
                isSelectionPlayback = false
                selectionCurrentId = null
            }
        }
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
                    currentIndex = -1,
                    history = emptyList(),
                    historyIndex = -1,
                    isRandom = false,
                    isSmart = true,
                    isSingleRepeat = false,
                    isLikedFilter = false,
                    likedCurrentId = null,
                    likedResumePositionMs = 0L,
                    temporaryHistory = emptyList(),
                    sortCriteria = SortCriteria.DATE,
                    sortOrder = SortOrder.DESCENDING
                )
                updatePlaylistQueue(id)
            }
        }
        selectedIds.clear()
        isSelectionMode = false
        isSelectionPlayback = false
        selectionCurrentId = null
        saveState()
    }

    fun localReset() {
        val id = currentPlaylistId
        val p = playlists[id] ?: return
        
        // 1. Reset standard options to default
        val resetState = p.copy(
            currentIndex = -1,
            history = emptyList(),
            historyIndex = -1,
            isRandom = false,
            isSmart = true,
            isSingleRepeat = false,
            isLikedFilter = false,
            likedCurrentId = null,
            likedResumePositionMs = 0L,
            temporaryHistory = emptyList(),
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
                currentIndex = 0,
                history = listOf(updatedP.queue[0]),
                historyIndex = 0
            )
        }

        selectedIds.clear()
        isSelectionMode = false
        isSelectionPlayback = false
        selectionCurrentId = null
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
        selectionCurrentId = null
        saveState()
    }

    fun getCurrentPlaylist(): PlaylistState? = playlists[currentPlaylistId]
    fun getAllPlaylists(): List<PlaylistState> = playlists.values.sortedBy { if (it.id == "ALL") 0 else 1 }.toList()
    
    fun switchPlaylist(id: String) {
        if (playlists.containsKey(id)) {
            if (currentPlaylistId != id) clearSelection()
            selectionCurrentId = null
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
            if (currentPlaylistId == id) {
                clearSelection()
                currentPlaylistId = "ALL"
            }
            saveState()
        }
    }

    fun setTemporaryPlaylist(name: String, ids: List<String>) {
        val id = "TEMP"
        clearSelection()
        playlists[id] = PlaylistState(id, name, PlaylistType.TEMPORARY, queue = ids, originalQueue = ids)
        currentPlaylistId = id
        updatePlaylistQueue(id)
        saveState()
    }

    fun toggleLikedFilter(resumePositionMs: Long = 0L): Boolean {
        val p = playlists[currentPlaylistId] ?: return false
        val newLikedFilter = !p.isLikedFilter
        if (newLikedFilter && playbackQueue(p.copy(isLikedFilter = true)).isEmpty()) return false
        val likedCurrentId = if (newLikedFilter) {
            getCurrentTrack()?.takeIf { it.fingerprintId in likedFingerprints }?.id
        } else null
        playlists[currentPlaylistId] = p.copy(isLikedFilter = newLikedFilter,
            likedCurrentId = likedCurrentId,
            likedResumePositionMs = if (newLikedFilter) resumePositionMs else 0L)
        saveState()
        return true
    }

    private fun playbackQueue(state: PlaylistState): List<String> {
        if (!state.isLikedFilter) return state.queue
        val likedIds = allSongs.asSequence()
            .filter { likedFingerprints.contains(it.fingerprintId) }
            .map { it.id }.toSet()
        return state.queue.filter { it in likedIds }
    }

    fun toggleRandom() {
        val p = playlists[currentPlaylistId] ?: return
        playlists[currentPlaylistId] = p.copy(isRandom = !p.isRandom)
        updatePlaylistQueue(currentPlaylistId)
        saveState()
    }

    fun toggleSmart() {
        val p = playlists[currentPlaylistId] ?: return
        playlists[currentPlaylistId] = p.copy(isSmart = !p.isSmart)
        saveState()
    }

    fun toggleSingleRepeat() {
        val p = playlists[currentPlaylistId] ?: return
        playlists[currentPlaylistId] = p.copy(isSingleRepeat = !p.isSingleRepeat)
        saveState()
    }

    fun toggleSort(criteria: SortCriteria) {
        val p = playlists[currentPlaylistId] ?: return
        val newOrder = if (p.sortCriteria == criteria) {
            if (p.sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        } else SortOrder.ASCENDING
        playlists[currentPlaylistId] = p.copy(sortCriteria = criteria, sortOrder = newOrder)
        updatePlaylistQueue(currentPlaylistId)
        saveState()
    }

    fun getNextTrack(isManual: Boolean = false): MusicFile? {
        val state = playlists[currentPlaylistId] ?: return null
        val queue = playbackQueue(state)
        val playableIds = queue.toSet()
        
        // Temporary modes repeat in playlist order without smart skipping or cycle resets.
        val repeatingTrack = if (state.isSingleRepeat && !isManual) getCurrentTrack() else null
        if (repeatingTrack != null) return repeatingTrack

        if (isSelectionPlayback) {
            val selectedList = state.queue.filter { selectedIds.contains(it) }
            if (selectedList.isEmpty()) {
                isSelectionPlayback = false
                selectionCurrentId = null
            }
            else {
                val selIdx = selectedList.indexOf(selectionCurrentId)
                val nextId = selectedList[(selIdx + 1) % selectedList.size]
                return updateTemporaryTrackById(nextId, selection = true)
            }
        }

        if (queue.isEmpty()) return null

        if (state.isLikedFilter) {
            val likedIndex = queue.indexOf(state.likedCurrentId)
            val anchorIndex = state.queue.indexOf(state.likedCurrentId
                ?: state.queue.getOrNull(state.currentIndex))
            val nextId = if (likedIndex >= 0) queue[(likedIndex + 1) % queue.size]
                else state.queue.drop((anchorIndex + 1).coerceAtLeast(0))
                    .firstOrNull { it in playableIds } ?: queue.first()
            return updateTemporaryTrackById(nextId, selection = false)
        }

        // The underlying playback history is untouched by temporary navigation.
        val nextHistoryIndex = ((state.historyIndex + 1) until state.history.size)
            .firstOrNull { state.history[it] in playableIds }
        if (nextHistoryIndex != null) {
            val nextIndex = nextHistoryIndex
            val nextId = state.history[nextIndex]
            return updateCurrentTrackById(nextId, isHistoryMove = true, targetHistoryIndex = nextIndex)
        }

        var nextId: String? = null

        if (state.isRandom) {
            if (state.isSmart) {
                val histSet = state.history.toSet() + state.temporaryHistory
                val candidates = queue.filter { !histSet.contains(it) }
                if (candidates.isNotEmpty()) {
                    nextId = candidates.random()
                } else {
                    // Smart Random: All songs played. Auto-reset history and loop.
                    playlists[currentPlaylistId] = state.copy(history = emptyList(), historyIndex = -1,
                        temporaryHistory = emptyList())
                    saveState()
                    nextId = queue.random()
                }
            } else {
                nextId = queue.random()
            }
        } else {
            if (state.isSmart) {
                val histSet = state.history.toSet() + state.temporaryHistory
                for (i in (state.currentIndex + 1) until state.queue.size) {
                    val id = state.queue[i]
                    if (id in playableIds && id !in histSet) {
                        nextId = id
                        break
                    }
                }
                if (nextId == null) {
                    nextId = queue.firstOrNull { !histSet.contains(it) }
                }
                
                if (nextId == null) {
                    // Smart Sequential: All songs played. Auto-reset history and loop.
                    playlists[currentPlaylistId] = state.copy(history = emptyList(), historyIndex = -1,
                        temporaryHistory = emptyList())
                    saveState()
                    nextId = queue.firstOrNull()
                }
            } else {
                val currentId = state.queue.getOrNull(state.currentIndex)
                val playbackIndex = queue.indexOf(currentId)
                nextId = if (playbackIndex >= 0) queue[(playbackIndex + 1) % queue.size]
                    else state.queue.drop((state.currentIndex + 1).coerceAtLeast(0))
                        .firstOrNull { it in playableIds } ?: queue.first()
            }
        }
        return nextId?.let { updateCurrentTrackById(it) }
    }

    fun getPreviousTrack(): MusicFile? {
        val state = playlists[currentPlaylistId] ?: return null
        if (isSelectionPlayback) {
            val selectedList = state.queue.filter { selectedIds.contains(it) }
            if (selectedList.isEmpty()) {
                isSelectionPlayback = false
                selectionCurrentId = null
            } else {
                val selIdx = selectedList.indexOf(selectionCurrentId)
                val prevId = selectedList[if (selIdx <= 0) selectedList.size - 1 else selIdx - 1]
                return updateTemporaryTrackById(prevId, selection = true)
            }
        }
        val playableIds = playbackQueue(state).toSet()
        if (state.isLikedFilter && playableIds.isNotEmpty()) {
            val likedQueue = playbackQueue(state)
            val likedIndex = likedQueue.indexOf(state.likedCurrentId)
            val anchorIndex = state.queue.indexOf(state.likedCurrentId
                ?: state.queue.getOrNull(state.currentIndex))
            val prevId = if (likedIndex >= 0) likedQueue[(likedIndex - 1 + likedQueue.size) % likedQueue.size]
                else state.queue.take(anchorIndex.coerceAtLeast(0))
                    .lastOrNull { it in playableIds } ?: likedQueue.last()
            return updateTemporaryTrackById(prevId, selection = false)
        }
        val prevHistoryIndex = (state.historyIndex - 1 downTo 0)
            .firstOrNull { state.history[it] in playableIds }
        if (prevHistoryIndex != null) {
            val prevIndex = prevHistoryIndex
            val prevId = state.history[prevIndex]
            return updateCurrentTrackById(prevId, isHistoryMove = true, targetHistoryIndex = prevIndex)
        }
        return getCurrentTrack()
    }

    private fun updateTemporaryTrackById(id: String, selection: Boolean): MusicFile? {
        val state = playlists[currentPlaylistId] ?: return null
        if (id !in state.queue) return null
        val track = allSongs.find { it.id == id } ?: return null
        if (selection) selectionCurrentId = id
        playlists[currentPlaylistId] = state.copy(
            likedCurrentId = if (selection) state.likedCurrentId else id
        )
        saveState()
        return track
    }

    fun recordStartedTrack(id: String) {
        val state = playlists[currentPlaylistId] ?: return
        if (id !in state.queue ||
            !(isSelectionPlayback || state.isLikedFilter || state.isSingleRepeat)) return
        playlists[currentPlaylistId] = state.copy(temporaryHistory = state.temporaryHistory + id)
        saveState()
    }

    private fun updateCurrentTrackById(id: String, isHistoryMove: Boolean = false, targetHistoryIndex: Int = -1): MusicFile? {
        val state = playlists[currentPlaylistId] ?: return null
        val qIdx = state.queue.indexOf(id)
        if (qIdx == -1) return null

        val currentHistory = state.history
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
            historyIndex = newHistoryIdx
        )
        saveState()
        return allSongs.find { it.id == id }
    }

    fun getCurrentTrack(): MusicFile? {
        val s = playlists[currentPlaylistId] ?: return null
        val id = when {
            isSelectionPlayback && selectionCurrentId != null -> selectionCurrentId
            s.isLikedFilter && s.likedCurrentId != null -> s.likedCurrentId
            else -> s.queue.getOrNull(s.currentIndex)
        }
        return allSongs.find { it.id == id }
    }

    fun getPlayableCurrentOrNextTrack(): MusicFile? {
        val state = playlists[currentPlaylistId] ?: return null
        val current = getCurrentTrack()
        if (current != null && (isSelectionPlayback || !state.isLikedFilter ||
                likedFingerprints.contains(current.fingerprintId))) {
            return current
        }
        return getNextTrack(isManual = true)
    }

    fun playTrackById(id: String): MusicFile? =
        if (isSelectionPlayback) updateTemporaryTrackById(id, selection = true)
        else if (playlists[currentPlaylistId]?.isLikedFilter == true) updateTemporaryTrackById(id, selection = false)
        else updateCurrentTrackById(id)

    fun startSelectionPlayback(id: String): MusicFile? {
        if (id !in selectedIds) return null
        val track = updateTemporaryTrackById(id, selection = true) ?: return null
        isSelectionPlayback = true
        return track
    }

    fun toggleSelectionMode() {
        if (isSelectionMode) {
            clearSelection()
        } else {
            selectedIds.clear()
            isSelectionPlayback = false
            isSelectionMode = true
        }
    }

    fun toggleSelection(id: String) {
        if (id !in (playlists[currentPlaylistId]?.queue ?: emptyList())) return
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) {
            isSelectionMode = false
            isSelectionPlayback = false
            selectionCurrentId = null
        }
    }

    fun selectAll() {
        val queue = playlists[currentPlaylistId]?.queue ?: emptyList()
        selectedIds.clear()
        selectedIds.addAll(queue)
        isSelectionMode = true
        isSelectionPlayback = false
        selectionCurrentId = null
    }

    fun clearSelection() {
        selectedIds.clear()
        isSelectionMode = false
        isSelectionPlayback = false
        selectionCurrentId = null
    }

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
            val rawPlaylists: Map<String, PlaylistStateDto>? = gson.fromJson(json, object : TypeToken<Map<String, PlaylistStateDto>>() {}.type)
            if (rawPlaylists != null) {
                playlists = rawPlaylists.mapValues { entry ->
                    entry.value.toDomain(entry.key)
                }.toMutableMap()
            } else {
                playlists = mutableMapOf()
            }
        } catch (_: Exception) {
            playlists = mutableMapOf()
        }
        currentPlaylistId = prefs.getString("current_id", "ALL") ?: "ALL"
    }

    fun getActiveQueueSongs(): List<MusicFile> {
        val s = playlists[currentPlaylistId] ?: return emptyList()
        return s.queue.mapNotNull { qId -> allSongs.find { it.id == qId } }
    }

    fun getPlayedSongIds(): Set<String> = playlists[currentPlaylistId]?.let {
        it.history.toSet() + it.temporaryHistory
    } ?: emptySet()
}

private data class PlaylistStateDto(
    val id: String? = null,
    val name: String? = null,
    val type: PlaylistType? = null,
    val queue: List<String>? = null,
    val originalQueue: List<String>? = null,
    val currentIndex: Int? = null,
    val history: List<String>? = null,
    val historyIndex: Int? = null,
    val isRandom: Boolean? = null,
    val isSmart: Boolean? = null,
    val isSingleRepeat: Boolean? = null,
    val isLikedFilter: Boolean? = null,
    val likedCurrentId: String? = null,
    val likedResumePositionMs: Long? = null,
    val temporaryHistory: List<String>? = null,
    val sortCriteria: SortCriteria? = null,
    val sortOrder: SortOrder? = null
) {
    fun toDomain(fallbackId: String): PlaylistState {
        val safeQueue = queue ?: emptyList()
        val safeOrig = if (!originalQueue.isNullOrEmpty()) originalQueue else if (fallbackId != "ALL") safeQueue else emptyList()
        return PlaylistState(
            id = id ?: fallbackId,
            name = name ?: "Unknown",
            type = type ?: PlaylistType.PERMANENT,
            queue = safeQueue,
            originalQueue = safeOrig,
            currentIndex = currentIndex ?: -1,
            history = history ?: emptyList(),
            historyIndex = historyIndex ?: -1,
            isRandom = isRandom ?: false,
            isSmart = isSmart ?: true,
            isSingleRepeat = isSingleRepeat ?: false,
            isLikedFilter = isLikedFilter ?: false,
            likedCurrentId = likedCurrentId,
            likedResumePositionMs = likedResumePositionMs ?: 0L,
            temporaryHistory = temporaryHistory ?: emptyList(),
            sortCriteria = sortCriteria ?: SortCriteria.DATE,
            sortOrder = sortOrder ?: SortOrder.DESCENDING
        )
    }
}
