package com.example.loopmuse.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loopmuse.data.db.AppDatabase
import com.example.loopmuse.data.db.DiscoveryReactionEntity
import com.example.loopmuse.service.DiscoveryFeed
import com.example.loopmuse.service.DiscoveryRepository
import com.example.loopmuse.service.DiscoverySong
import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The app's recommendations live beside user posts in the Lounge. */
@Composable
fun DiscoveryRecommendationsPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { DiscoveryRepository(context) }
    val reactionDao = remember(context) { AppDatabase.getDatabase(context).discoveryReactionDao() }
    val reactions by remember(reactionDao) { reactionDao.getAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var feed by remember { mutableStateOf<DiscoveryFeed?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val recommended = reactions.filter { it.batchId > 0L && !it.isHidden }
        .sortedByDescending { it.recommendedAt }
    val currentBatchId = reactions.maxOfOrNull { it.batchId } ?: 0L

    fun loadRecommendations(initial: Boolean = false) {
        if (!initial && isLoading) return
        isLoading = true
        scope.launch {
            errorMessage = null
            var sourceLoaded = false
            try {
                val existing = reactionDao.getAllOnce()
                if (initial && existing.any { it.batchId > 0L }) return@launch
                val loaded = repository.load()
                sourceLoaded = true
                val selected = pickDiscoverySongs(loaded.songs,
                    existing.mapTo(mutableSetOf()) { it.songKey })
                if (selected.isEmpty()) {
                    errorMessage = "이번 주에는 아직 소개하지 않은 곡이 없습니다. 새 통계가 올라오면 다시 확인해 주세요."
                    return@launch
                }
                val batchId = maxOf(System.currentTimeMillis(), (existing.maxOfOrNull { it.batchId } ?: 0L) + 1L)
                reactionDao.insertRecommendations(selected.mapIndexed { index, song ->
                    DiscoveryReactionEntity(
                        songKey = song.songKey,
                        artist = song.artist,
                        title = song.title,
                        isLiked = false,
                        updatedAt = batchId + index,
                        recommendedAt = batchId + index,
                        batchId = batchId,
                        listenCount = song.listenCount
                    )
                })
                feed = loaded
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                errorMessage = if (sourceLoaded) "추천곡을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."
                    else "추천곡을 불러오지 못했습니다. 인터넷 연결을 확인해 주세요."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadRecommendations(initial = true) }
    LaunchedEffect(currentBatchId) { listState.scrollToItem(0) }

    fun toggleLike(key: String) {
        scope.launch {
            try {
                reactionDao.toggleLike(key, System.currentTimeMillis())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(context, "좋아요를 저장하지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun markHeard(key: String) {
        scope.launch {
            try {
                reactionDao.setRead(key, true, System.currentTimeMillis())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(context, "들음 상태를 저장하지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun listen(song: DiscoveryReactionEntity) {
        if (openYouTubeSearch(context, song.artist, song.title)) markHeard(song.songKey)
    }

    fun hideSong(key: String) {
        scope.launch {
            try {
                reactionDao.hideRecommendation(key, System.currentTimeMillis())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(context, "추천곡을 목록에서 삭제하지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "앱이 고른 노래",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { loadRecommendations() }, enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (recommended.isEmpty()) "추천받기" else "다른 곡", fontSize = 12.sp)
            }
        }
        Text(
            if (feed?.isOfflineCopy == true) "이전에 불러온 곡 · ListenBrainz 주간 청취 통계"
            else "많이 들은 곡에서 골랐어요 · ListenBrainz 주간 청취 통계",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, bottom = 4.dp)
        )
        if (errorMessage != null && recommended.isNotEmpty()) {
            DiscoveryMessage(errorMessage.orEmpty())
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (recommended.isEmpty()) {
                item {
                    if (isLoading) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    } else DiscoveryMessage(errorMessage ?: "이번 주 추천곡이 아직 없습니다.")
                }
            } else {
                items(recommended, key = { it.songKey }) { song ->
                    DiscoverySongCard(
                        artist = song.artist,
                        title = song.title,
                        isLiked = song.isLiked,
                        isRead = song.isRead,
                        listenCount = song.listenCount.takeIf { it > 0L },
                        onLike = { toggleLike(song.songKey) },
                        onListen = { listen(song) },
                        onDelete = { hideSong(song.songKey) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryMessage(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DiscoverySongCard(
    artist: String,
    title: String,
    isLiked: Boolean,
    isRead: Boolean,
    onLike: () -> Unit,
    onListen: () -> Unit,
    onDelete: () -> Unit,
    listenCount: Long? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = AppCardStyle.horizontalPadding,
                vertical = AppCardStyle.compactVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        else Color(0xFF174A81),
                    textDecoration = TextDecoration.Underline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).heightIn(min = 32.dp)
                        .clickable(
                            onClickLabel = "${if (isRead) "들은 곡" else "듣지 않은 곡"}, YouTube에서 곡 검색",
                            role = Role.Button
                        ) { onListen() }
                        .padding(vertical = 6.dp)
                )
                Box(
                    modifier = Modifier.size(32.dp)
                        .clickable(onClickLabel = if (isLiked) "좋아요 취소" else "좋아요", role = Role.Button) { onLike() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLiked) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFD64559),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "추천곡 메뉴",
                            modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("목록에서 삭제") }, onClick = {
                            menuExpanded = false
                            confirmDelete = true
                        })
                    }
                }
            }
            Text(
                listenCount?.let { "$artist · 청취 ${NumberFormat.getNumberInstance(Locale.KOREA).format(it)}회" }
                    ?: artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(vertical = 4.dp)
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("추천곡 삭제") },
            text = { Text("이 곡을 추천 목록에서 숨길까요? 중복 추천 방지 기록은 남습니다.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } }
        )
    }
}

private fun pickDiscoverySongs(songs: List<DiscoverySong>, excludedKeys: Set<String>): List<DiscoverySong> {
    val day = System.currentTimeMillis() / 86_400_000L
    val shuffled = songs.filterNot { it.songKey in excludedKeys }.shuffled(Random(day.toInt()))
    val artists = mutableSetOf<String>()
    val diverseSongs = shuffled.filter { song ->
        val primaryArtist = song.artist
            .split(Regex("(?i),|\\s+feat\\.?|\\s+with\\s+|\\s+&\\s+"), limit = 2)
            .first().trim().lowercase(Locale.ROOT).replace('‐', '-')
        artists.add(primaryArtist)
    }
    val diverseKeys = diverseSongs.mapTo(mutableSetOf()) { it.songKey }
    return (diverseSongs + shuffled.filterNot { it.songKey in diverseKeys }).take(25)
}
