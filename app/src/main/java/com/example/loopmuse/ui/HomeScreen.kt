package com.example.loopmuse.ui

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.PlaybackScope
import com.example.loopmuse.data.RepeatMode
import com.example.loopmuse.data.SelectionItem
import com.example.loopmuse.service.MusicServiceConnection
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val musicServiceConnection = remember { MusicServiceConnection(context) }
    
    val storagePermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    var showSelectionScreen by remember { mutableStateOf(false) }
    
    val isServiceConnected by musicServiceConnection.isConnected.collectAsStateWithLifecycle()
    val isPlaying by musicServiceConnection.isPlaying.collectAsStateWithLifecycle()
    val currentTrack by musicServiceConnection.currentTrack.collectAsStateWithLifecycle()
    val allSongs by musicServiceConnection.allSongs.collectAsStateWithLifecycle()
    val playedSongIds by musicServiceConnection.playedSongIds.collectAsStateWithLifecycle()
    val repeatMode by musicServiceConnection.repeatMode.collectAsStateWithLifecycle()
    val playbackScope by musicServiceConnection.playbackScope.collectAsStateWithLifecycle()
    val songCounts by musicServiceConnection.songCounts.collectAsStateWithLifecycle()

    var showQueueEndedDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var recentLimit by remember { mutableStateOf(30) }

    val filteredSongs by remember {
        derivedStateOf {
            if (searchQuery.isEmpty()) allSongs
            else allSongs.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
        }
    }

    LaunchedEffect(isServiceConnected) {
        if (isServiceConnected) {
            musicServiceConnection.queueEnded.collectLatest {
                showQueueEndedDialog = true
            }
        }
    }
    
    val hasPermissions by remember {
        derivedStateOf { storagePermissionState.status.isGranted }
    }
    
    var lastKnownPlayingState by remember { mutableStateOf(false) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            lastKnownPlayingState = true
        } else if (lastKnownPlayingState && currentTrack != null) {
            kotlinx.coroutines.delay(500.milliseconds)
            lastKnownPlayingState = false
        } else {
            lastKnownPlayingState = false
        }
    }
    
    val shouldShowNextButton by remember {
        derivedStateOf { lastKnownPlayingState || currentTrack != null }
    }
    
    LaunchedEffect(Unit) {
        musicServiceConnection.bindService()
    }
    
    DisposableEffect(Unit) {
        onDispose { musicServiceConnection.unbindService() }
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isServiceConnected) {
                    val currentItems = musicServiceConnection.getSelectedItems()
                    if (currentItems.isNotEmpty()) {
                        musicServiceConnection.setSelectedItems(currentItems)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    if (showSelectionScreen) {
        MusicSelectionScreen(
            initialSelectedItems = musicServiceConnection.getSelectedItems(),
            onSelectionApplied = { items ->
                musicServiceConnection.setSelectedItems(items)
                showSelectionScreen = false
            },
            onBackPressed = { showSelectionScreen = false }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            topBar = {
                Surface(tonalElevation = 4.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎵 LoopMuse", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showSearchDialog = true }) {
                                Icon(Icons.Default.Search, contentDescription = "검색")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (hasPermissions) showSelectionScreen = true
                                else storagePermissionState.launchPermissionRequest()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("곡,폴더 선택")
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isServiceConnected) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("서비스 연결 중...")
                    }
                } else if (!hasPermissions) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Button(onClick = { storagePermissionState.launchPermissionRequest() }) {
                            Text("저장소 권한 허용")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            PlaybackListCombo(playbackScope, recentLimit, musicServiceConnection) { recentLimit = it }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { musicServiceConnection.clearPlaybackHistory() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "재생곡 새로고침", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    RepeatModeSelector(repeatMode, musicServiceConnection)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    NowPlayingCard(currentTrack, isPlaying, coroutineScope, musicServiceConnection, shouldShowNextButton)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(songCounts, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("재생 목록", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Start))
                    
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistView(allSongs, currentTrack, playedSongIds) { song ->
                            musicServiceConnection.playTrackById(song.id)
                        }
                    }
                }
            }
        }
    }

    if (showQueueEndedDialog) {
        AlertDialog(
            onDismissRequest = { showQueueEndedDialog = false },
            title = { Text("재생 완료") },
            text = { Text("모든 곡을 다 들었습니다. 어떻게 하시겠습니까?") },
            confirmButton = {},
            dismissButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        musicServiceConnection.setRepeatMode(RepeatMode.SHUFFLE)
                        coroutineScope.launch { musicServiceConnection.playRandomUnplayedSong() }
                        showQueueEndedDialog = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("새로운 랜덤 재생") }
                    Button(onClick = {
                        musicServiceConnection.setRepeatMode(RepeatMode.SEQUENTIAL)
                        coroutineScope.launch { musicServiceConnection.playRandomUnplayedSong() }
                        showQueueEndedDialog = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("전체 순차 재생") }
                }
            }
        )
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("곡 검색") },
            text = {
                Column {
                    TextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("제목 또는 아티스트 검색") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(filteredSongs) { song ->
                            TextButton(onClick = {
                                musicServiceConnection.playTrackById(song.id)
                                showSearchDialog = false
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("${song.title} - ${song.artist}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSearchDialog = false }) { Text("닫기") } }
        )
    }
}

@Composable
fun PlaybackListCombo(
    scope: PlaybackScope,
    limit: Int,
    connection: MusicServiceConnection,
    onLimitChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "전체곡" to (PlaybackScope.ALL to 0),
        "최근 10곡" to (PlaybackScope.RECENT to 10),
        "최근 30곡" to (PlaybackScope.RECENT to 30),
        "최근 50곡" to (PlaybackScope.RECENT to 50)
    )
    val currentLabel = when {
        scope == PlaybackScope.ALL -> "전체곡"
        limit == 10 -> "최근 10곡"
        limit == 30 -> "최근 30곡"
        limit == 50 -> "최근 50곡"
        else -> "전체곡"
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(currentLabel, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("▼", fontSize = 10.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, config) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        val (newScope, newLimit) = config
                        if (newScope == PlaybackScope.ALL) connection.setPlaybackScope(PlaybackScope.ALL)
                        else {
                            connection.setPlaybackScope(PlaybackScope.RECENT)
                            connection.setRecentLimit(newLimit)
                            onLimitChange(newLimit)
                        }
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun RepeatModeSelector(mode: RepeatMode, connection: MusicServiceConnection) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RepeatModeButton(RepeatMode.SHUFFLE, "랜덤", mode, connection)
        RepeatModeButton(RepeatMode.SEQUENTIAL, "순차", mode, connection)
        RepeatModeButton(RepeatMode.SINGLE_REPEAT, "1곡", mode, connection)
    }
}

@Composable
fun RepeatModeButton(mode: RepeatMode, label: String, currentMode: RepeatMode, connection: MusicServiceConnection) {
    FilterChip(
        selected = currentMode == mode,
        onClick = { connection.setRepeatMode(mode) },
        label = { Text(label) }
    )
}

@Composable
fun NowPlayingCard(
    track: MusicFile?,
    isPlaying: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    connection: MusicServiceConnection,
    showNext: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(track?.title ?: "재생 중인 곡 없음", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track?.artist ?: "Unknown Artist", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = {
                    scope.launch {
                        if (isPlaying) connection.togglePlayPause()
                        else connection.playRandomUnplayedSong()
                    }
                }) {
                    Text(if (isPlaying) "⏸ 일시정지" else "▶ 재생")
                }
                if (showNext) {
                    IconButton(onClick = { connection.playNext() }) {
                        Text("⏭", fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistView(
    songs: List<MusicFile>,
    currentTrack: MusicFile?,
    playedSongIds: Set<String>,
    onSongClick: (MusicFile) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentTrack) {
        val index = songs.indexOfFirst { it.id == currentTrack?.id }
        if (index != -1) listState.animateScrollToItem(index)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(songs) { song ->
            val isCurrent = song.id == currentTrack?.id
            val isPlayed = playedSongIds.contains(song.id)
            
            val contentColor = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isPlayed -> Color.Gray.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.onSurface
            }

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSongClick(song) },
                color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.List, 
                        contentDescription = null, 
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = song.title, 
                            color = contentColor,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist, 
                            fontSize = 12.sp, 
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
