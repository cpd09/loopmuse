package com.example.loopmuse.ui

import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.viewinterop.AndroidView
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
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var browserSong by remember { mutableStateOf<DiscoveryReactionEntity?>(null) }
    val recommended = reactions.filter { it.batchId > 0L && !it.isHidden }
        .sortedByDescending { it.recommendedAt }
    val liked = reactions.filter { it.isLiked && !it.isHidden }
        .sortedByDescending { it.recommendedAt }
    val currentBatchId = reactions.maxOfOrNull { it.batchId } ?: 0L
    val unheardSongs = recommended.filter { !it.isRead }
    val heardSongs = recommended.filter { it.isRead }
    val visibleSongs = when (filter) {
        "UNREAD" -> unheardSongs
        "READ" -> heardSongs
        "LIKED" -> liked
        else -> recommended
    }

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
                filter = "ALL"
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
    LaunchedEffect(filter, currentBatchId) { listState.scrollToItem(0) }

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
        markHeard(song.songKey)
        browserSong = song
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

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "ALL" to "전체 ${recommended.size}",
                    "UNREAD" to "안 들음 ${unheardSongs.size}",
                    "READ" to "들음 ${heardSongs.size}",
                    "LIKED" to "좋아요 ${liked.size}"
                ).forEach { (mode, label) ->
                    AppOutlinedButton(
                        onClick = { filter = mode },
                        modifier = Modifier.weight(1f).heightIn(min = 32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (filter == mode) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                        ),
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp)
                    ) {
                        Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (errorMessage != null && visibleSongs.isNotEmpty()) {
                DiscoveryMessage(errorMessage.orEmpty())
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (visibleSongs.isEmpty()) {
                    item {
                        if (isLoading) {
                            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        } else DiscoveryMessage(when (filter) {
                            "LIKED" -> "추천곡 옆의 하트를 누르면 이곳에 모입니다."
                            "UNREAD" -> "아직 듣지 않은 추천곡이 없습니다."
                            "READ" -> "들은 추천곡이 없습니다."
                            else -> errorMessage ?: "이번 주 추천곡이 아직 없습니다."
                        })
                    }
                } else {
                    items(visibleSongs, key = { it.songKey }) { song ->
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
        browserSong?.let { song ->
            DiscoveryBrowser(
                song = song,
                songs = recommended,
                onSelectSong = ::listen,
                onClose = { browserSong = null },
                modifier = Modifier.fillMaxSize()
            )
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
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isRead) 0.65f else 1f),
                    textDecoration = TextDecoration.Underline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).heightIn(min = 32.dp)
                        .clickable(onClickLabel = "YouTube에서 곡 검색", role = Role.Button) { onListen() }
                        .padding(vertical = 6.dp)
                )
                Text(
                    if (isRead) "들음" else "● 안 들음",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.heightIn(min = 32.dp)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.heightIn(min = 32.dp)
                        .clickable(onClickLabel = if (isLiked) "좋아요 취소" else "좋아요", role = Role.Button) { onLike() }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "좋아요 취소" else "좋아요",
                        tint = if (isLiked) Color(0xFFD64559) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (isLiked) " 내 좋아요" else " 좋아요",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLiked) Color(0xFFD64559) else MaterialTheme.colorScheme.onSurfaceVariant
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryBrowser(
    song: DiscoveryReactionEntity,
    songs: List<DiscoveryReactionEntity>,
    onSelectSong: (DiscoveryReactionEntity) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchUrl = remember(song.songKey) {
        Uri.parse("https://www.youtube.com/results").buildUpon()
            .appendQueryParameter("search_query", "${song.artist} ${song.title}")
            .build().toString()
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showSongPicker by remember { mutableStateOf(false) }
    var requestedUrl by remember { mutableStateOf(searchUrl) }
    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else onClose() }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.onPause()
            webView?.destroy()
            webView = null
        }
    }
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("YouTube · ${song.artist} ${song.title}", modifier = Modifier.weight(1f).padding(start = 12.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { showSongPicker = true }) { Text("곡 선택", fontSize = 12.sp) }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "검색 닫기") }
        }
        if (loading) CircularProgressIndicator(Modifier.size(18.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportMultipleWindows(false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val scheme = request?.url?.scheme
                            return scheme != "https" && scheme != "http"
                        }
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            loading = true
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                        }
                    }
                    webChromeClient = WebChromeClient()
                    loadUrl(searchUrl)
                    webView = this
                }
            },
            update = { view ->
                if (requestedUrl != searchUrl) {
                    requestedUrl = searchUrl
                    view.loadUrl(searchUrl)
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
    if (showSongPicker) {
        ModalBottomSheet(onDismissRequest = { showSongPicker = false }) {
            Text("추천곡 선택", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                items(songs, key = { it.songKey }) { candidate ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable {
                                showSongPicker = false
                                onSelectSong(candidate)
                            }
                            .padding(horizontal = 20.dp, vertical = 9.dp)
                    ) {
                        Text(candidate.title, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (candidate.songKey == song.songKey) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(candidate.artist, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
