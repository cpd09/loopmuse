package com.example.loopmuse.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.PlaybackScope
import com.example.loopmuse.data.RepeatMode
import com.example.loopmuse.service.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val musicScanner = remember { MusicScanner(context) }
    val musicServiceConnection = remember { MusicServiceConnection(context) }
    
    val storagePermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    var showSelectionScreen by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    
    val isServiceConnected by musicServiceConnection.isConnected.collectAsStateWithLifecycle()
    val isPlaying by musicServiceConnection.isPlaying.collectAsStateWithLifecycle()
    val currentTrack by musicServiceConnection.currentTrack.collectAsStateWithLifecycle()
    val allSongs by musicServiceConnection.allSongs.collectAsStateWithLifecycle()
    val playedSongIds by musicServiceConnection.playedSongIds.collectAsStateWithLifecycle()
    
    val repeatMode by musicServiceConnection.repeatMode.collectAsStateWithLifecycle()
    val playbackScope by musicServiceConnection.playbackScope.collectAsStateWithLifecycle()
    val isSingleRepeat by musicServiceConnection.isSingleRepeat.collectAsStateWithLifecycle()
    val pendingScope by musicServiceConnection.pendingScope.collectAsStateWithLifecycle()
    val pendingRepeatMode by musicServiceConnection.pendingRepeatMode.collectAsStateWithLifecycle()
    
    val songCounts by musicServiceConnection.songCounts.collectAsStateWithLifecycle()

    var showQueueEndedDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var recentLimit by remember { mutableIntStateOf(100) }

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
            musicScanner = musicScanner,
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
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
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
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Single Row Menu with Pending visualization
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { musicServiceConnection.clearPlaybackHistory() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Box(modifier = Modifier.weight(1.3f)) {
                            PlaybackListCombo(playbackScope, pendingScope, recentLimit, musicServiceConnection) { recentLimit = it }
                        }
                        Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            RepeatModeButtonSmall(RepeatMode.SHUFFLE, "랜덤", repeatMode, pendingRepeatMode, isSingleRepeat, musicServiceConnection)
                            RepeatModeButtonSmall(RepeatMode.SEQUENTIAL, "순차", repeatMode, pendingRepeatMode, isSingleRepeat, musicServiceConnection)
                            SingleRepeatButtonSmall(isSingleRepeat, musicServiceConnection)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    NowPlayingCardExpanded(currentTrack, isPlaying, coroutineScope, musicServiceConnection) {
                        showExitDialog = true
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(songCounts, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("재생 목록", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        
                        if (repeatMode == RepeatMode.SEQUENTIAL && !isSingleRepeat) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val sortInfo = musicServiceConnection.getSortInfo()
                                SortToggleButton("파일명", sortInfo.first == SortCriteria.FILENAME, sortInfo.second) {
                                    musicServiceConnection.toggleSort(SortCriteria.FILENAME)
                                }
                                SortToggleButton("수정일자", sortInfo.first == SortCriteria.DATE, sortInfo.second) {
                                    musicServiceConnection.toggleSort(SortCriteria.DATE)
                                }
                            }
                        }
                    }
                    
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistView(allSongs, currentTrack, playedSongIds) { song ->
                            musicServiceConnection.playTrackById(song.id)
                        }
                    }
                }
            }
        }
    }

    // Dialogs ...
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("종료") },
            text = { Text("앱을 종료하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) { Text("종료") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("취소") }
            }
        )
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
fun SortToggleButton(label: String, isSelected: Boolean, order: SortOrder, onClick: () -> Unit) {
    val icon = if (!isSelected) null else if (order == SortOrder.ASCENDING) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown
    
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label, 
                fontSize = 11.sp, 
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun PlaybackListCombo(
    scope: PlaybackScope,
    pendingScope: PlaybackScope?,
    limit: Int,
    connection: MusicServiceConnection,
    onLimitChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "전체곡" to (PlaybackScope.ALL to 0),
        "최근 15곡" to (PlaybackScope.RECENT to 15),
        "최근 50곡" to (PlaybackScope.RECENT to 50),
        "최근 100곡" to (PlaybackScope.RECENT to 100)
    )
    
    val effectiveScope = pendingScope ?: scope
    val currentLabel = when {
        effectiveScope == PlaybackScope.ALL -> "전체곡"
        limit == 15 -> "최근 15곡"
        limit == 50 -> "최근 50곡"
        limit == 100 -> "최근 100곡"
        else -> "전체곡"
    }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = if (pendingScope != null) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else ButtonDefaults.outlinedButtonColors()
        ) {
            Text(currentLabel + (if (pendingScope != null) " (예약)" else ""), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
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
fun RepeatModeButtonSmall(
    mode: RepeatMode, 
    label: String, 
    currentMode: RepeatMode, 
    pendingMode: RepeatMode?,
    isSingleRepeat: Boolean,
    connection: MusicServiceConnection
) {
    val effectiveMode = pendingMode ?: currentMode
    val isSelected = !isSingleRepeat && effectiveMode == mode
    
    FilterChip(
        selected = isSelected,
        onClick = { connection.setRepeatMode(mode) },
        label = { Text(label + (if (pendingMode == mode) " (예약)" else ""), fontSize = 11.sp) },
        modifier = Modifier.height(32.dp),
        colors = if (pendingMode == mode) FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer) else FilterChipDefaults.filterChipColors()
    )
}

@Composable
fun SingleRepeatButtonSmall(isSingleRepeat: Boolean, connection: MusicServiceConnection) {
    FilterChip(
        selected = isSingleRepeat,
        onClick = { connection.toggleSingleRepeat() },
        label = { Text("1곡", fontSize = 11.sp) },
        modifier = Modifier.height(32.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingCardExpanded(
    track: MusicFile?,
    isPlaying: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    connection: MusicServiceConnection,
    onExit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val infoText = if (track != null) "${track.title} - ${track.artist} (${track.file.name})" else "재생 중인 곡 없음"
            Text(
                text = infoText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "가사 작업예정",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "종료", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { connection.playPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "이전곡", modifier = Modifier.size(30.dp))
                }
                FilledTonalIconButton(
                    onClick = {
                        scope.launch {
                            if (isPlaying) connection.togglePlayPause()
                            else connection.playRandomUnplayedSong()
                        }
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "재생/일시정지",
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(onClick = { connection.playNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "다음곡", modifier = Modifier.size(30.dp))
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
                isPlayed -> Color.Gray.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.onSurface
            }

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSongClick(song) },
                color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.MusicNote, 
                        contentDescription = null, 
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = song.title, 
                            color = contentColor,
                            fontSize = 14.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist, 
                            fontSize = 11.sp, 
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
