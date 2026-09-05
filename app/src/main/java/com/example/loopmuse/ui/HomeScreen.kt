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
    
    val isReadyToPlay by remember {
        derivedStateOf { hasPermissions && isServiceConnected }
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
                                Icon(Icons.Default.Search, contentDescription = "Search")
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
                            Text("Select Music (Files & Folders)")
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
                        Text("Connecting to service...")
                    }
                } else if (!hasPermissions) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Button(onClick = { storagePermissionState.launchPermissionRequest() }) {
                            Text("Grant Storage Permission")
                        }
                    }
                } else {
                    // Playback Controls (Mini)
                    Spacer(modifier = Modifier.height(16.dp))
                    PlaybackModeSelector(playbackScope, recentLimit, repeatMode, musicServiceConnection) { recentLimit = it }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Now Playing Card
                    NowPlayingCard(currentTrack, isPlaying, coroutineScope, musicServiceConnection, shouldShowNextButton)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(songCounts, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Playlist", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Start))
                    
                    // Scrollable Playlist View
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistView(allSongs, currentTrack) { song ->
                            musicServiceConnection.playTrackById(song.id)
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showQueueEndedDialog) {
        AlertDialog(
            onDismissRequest = { showQueueEndedDialog = false },
            title = { Text("Playback Finished") },
            text = { Text("What would you like to do next?") },
            confirmButton = {},
            dismissButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        musicServiceConnection.setRepeatMode(RepeatMode.SHUFFLE)
                        coroutineScope.launch { musicServiceConnection.playRandomUnplayedSong() }
                        showQueueEndedDialog = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("New Shuffle") }
                    Button(onClick = {
                        musicServiceConnection.setRepeatMode(RepeatMode.SEQUENTIAL)
                        coroutineScope.launch { musicServiceConnection.playRandomUnplayedSong() }
                        showQueueEndedDialog = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("Sequential Play") }
                }
            }
        )
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search Songs") },
            text = {
                Column {
                    TextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search title or artist") }, modifier = Modifier.fillMaxWidth())
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
            confirmButton = { TextButton(onClick = { showSearchDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
fun PlaybackModeSelector(
    scope: PlaybackScope,
    limit: Int,
    mode: RepeatMode,
    connection: MusicServiceConnection,
    onLimitChange: (Int) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilterChip(selected = scope == PlaybackScope.ALL, onClick = { connection.setPlaybackScope(PlaybackScope.ALL) }, label = { Text("All") })
            FilterChip(selected = scope == PlaybackScope.RECENT, onClick = { connection.setPlaybackScope(PlaybackScope.RECENT) }, label = { Text("Recent ($limit)") })
        }
        if (scope == PlaybackScope.RECENT) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                listOf(10, 30, 50).forEach { l ->
                    OutlinedIconToggleButton(checked = limit == l, onCheckedChange = { onLimitChange(l); connection.setRecentLimit(l) }, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text(l.toString())
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RepeatModeButton(RepeatMode.SHUFFLE, "Shuffle", mode, connection)
            RepeatModeButton(RepeatMode.SEQUENTIAL, "Sequential", mode, connection)
            RepeatModeButton(RepeatMode.SINGLE_REPEAT, "1-Repeat", mode, connection)
        }
    }
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
            Text(track?.title ?: "No Track", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track?.artist ?: "Unknown Artist", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = {
                    scope.launch {
                        if (isPlaying) connection.togglePlayPause()
                        else connection.playRandomUnplayedSong()
                    }
                }) {
                    Text(if (isPlaying) "⏸ Pause" else "▶ Play")
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
fun PlaylistView(songs: List<MusicFile>, currentTrack: MusicFile?, onSongClick: (MusicFile) -> Unit) {
    val listState = rememberLazyListState()
    
    // Auto-scroll to current track
    LaunchedEffect(currentTrack) {
        val index = songs.indexOfFirst { it.id == currentTrack?.id }
        if (index != -1) {
            listState.animateScrollToItem(index)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(songs) { song ->
            val isSelected = song.id == currentTrack?.id
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSongClick(song) },
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.List, 
                        contentDescription = null, 
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(song.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
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
