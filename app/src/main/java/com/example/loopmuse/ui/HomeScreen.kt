package com.example.loopmuse.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loopmuse.community.ui.CommunityLoungeScreen
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.service.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Engine to block annoying system popup menus
    val emptyTextToolbar = remember {
        object : TextToolbar {
            override val status: TextToolbarStatus = TextToolbarStatus.Hidden
            override fun hide() {}
            override fun showMenu(rect: androidx.compose.ui.geometry.Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {}
        }
    }
    
    val musicScanner = remember { MusicScanner(context) }
    val musicServiceConnection = remember { MusicServiceConnection(context) }
    
    val storagePermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    var showSelectionScreen by remember { mutableStateOf(value = false) }
    var showExitDialog by remember { mutableStateOf(value = false) }
    var showGlobalRefreshDialog by remember { mutableStateOf(value = false) }
    var showLocalRefreshDialog by remember { mutableStateOf(value = false) }
    var showSearchDialog by remember { mutableStateOf(value = false) }
    var showEditPlaylistDialog by remember { mutableStateOf(value = false) }
    var showLoungeScreen by remember { mutableStateOf(value = false) }
    
    var showBackupResult by remember { mutableStateOf<String?>(null) }
    
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            coroutineScope.launch {
                val success = musicServiceConnection.createBackup(it)
                showBackupResult = if (success) "백업 성공: loopmuse_backup.json" else "백업 실패"
            }
        }
    }
    
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            coroutineScope.launch {
                val success = musicServiceConnection.restoreDatabase(it)
                showBackupResult = if (success) "데이터 복원(병합) 성공!" else "복원 실패"
            }
        }
    }
    
    var searchType by remember { mutableStateOf("파일명") } 
    var editPlaylistName by remember { mutableStateOf("") }
    var editSelectedIds by remember { mutableStateOf(setOf<String>()) }
    var selectedPlaylistIdForEdit by remember { mutableStateOf<String?>(null) }
    
    val isServiceConnected by musicServiceConnection.isConnected.collectAsStateWithLifecycle()
    val isPlaying by musicServiceConnection.isPlaying.collectAsStateWithLifecycle()
    val currentTrack by musicServiceConnection.currentTrack.collectAsStateWithLifecycle()
    val allSongsInQueue by musicServiceConnection.allSongsInQueue.collectAsStateWithLifecycle()
    val playlistState by musicServiceConnection.playlistState.collectAsStateWithLifecycle()
    val allPlaylists by musicServiceConnection.allPlaylists.collectAsStateWithLifecycle()
    val playedSongIds by musicServiceConnection.playedSongIds.collectAsStateWithLifecycle()
    
    val isSelectionMode by musicServiceConnection.isSelectionMode.collectAsStateWithLifecycle()
    val isSelectionPlayback by musicServiceConnection.isSelectionPlayback.collectAsStateWithLifecycle()
    val isTasteMode by musicServiceConnection.isTasteMode.collectAsStateWithLifecycle()
    val selectedIds by musicServiceConnection.selectedIds.collectAsStateWithLifecycle()
    val likedFingerprints by musicServiceConnection.likedFingerprints.collectAsStateWithLifecycle()
    
    val currentPosition by musicServiceConnection.currentPosition.collectAsStateWithLifecycle()
    val duration by musicServiceConnection.duration.collectAsStateWithLifecycle()
    val songCounts by musicServiceConnection.songCounts.collectAsStateWithLifecycle()

    var showQueueEndedDialog by remember { mutableStateOf(value = false) }
    var songForTagEdit by remember { mutableStateOf<MusicFile?>(null) }

    LaunchedEffect(isServiceConnected) {
        if (isServiceConnected) {
            musicServiceConnection.queueEnded.collectLatest { showQueueEndedDialog = true }
        }
    }
    
    val hasPermissions by remember { derivedStateOf { storagePermissionState.status.isGranted } }
    
    LaunchedEffect(Unit) { musicServiceConnection.bindService() }
    DisposableEffect(Unit) { onDispose { musicServiceConnection.unbindService() } }
    
    if (showLoungeScreen) {
        CommunityLoungeScreen(
            onBackPressed = { showLoungeScreen = false }
        )
    } else if (showSelectionScreen) {
        MusicSelectionScreen(
            musicScanner = musicScanner,
            initialSelectedItems = musicServiceConnection.getSelectedItems(),
            onSelectionApplied = { items ->
                musicServiceConnection.setSelectedItems(items)
                showSelectionScreen = false
            },
            onBackPressed = { showSelectionScreen = false },
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            topBar = {
                Surface(tonalElevation = 4.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎵 LoopMuse", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showLoungeScreen = true },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Lounge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("v1.9.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("2026.09.19", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { showGlobalRefreshDialog = true }, modifier = Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color.Red, modifier = Modifier.size(40.dp))
                                    Text("ALL", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            Button(onClick = { if (hasPermissions) showSelectionScreen = true else storagePermissionState.launchPermissionRequest() }, modifier = Modifier.weight(1f)) {
                                Text("곡,폴더 선택 (${songCounts})")
                            }
                            IconButton(onClick = { showExitDialog = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "종료", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isServiceConnected) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("서비스 연결 중...") }
                } else if (!hasPermissions) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Button(onClick = { storagePermissionState.launchPermissionRequest() }) { Text("저장소 권한 허용") }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { showLocalRefreshDialog = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "리스트 초기화", tint = Color.Red, modifier = Modifier.size(24.dp))
                        }
                        Box(modifier = Modifier.weight(1f)) { PlaylistCombo(playlistState, allPlaylists, musicServiceConnection) }
                        Row(modifier = Modifier.wrapContentWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            val togglesEnabled = !isSelectionPlayback && !(playlistState?.isSingleRepeat ?: false)
                            OptionIconButton(Icons.Default.Shuffle, playlistState?.isRandom ?: false, togglesEnabled) { musicServiceConnection.toggleRandom() }
                            OptionIconButton(Icons.Default.Headset, playlistState?.isSmart ?: false, togglesEnabled) { musicServiceConnection.toggleSmart() }
                            OptionIconButton(Icons.Default.RepeatOne, playlistState?.isSingleRepeat ?: false) { musicServiceConnection.toggleSingleRepeat() }
                        }
                        
                        var showSettingsMenu by remember { mutableStateOf(false) }
                        
                        Box {
                            IconButton(onClick = { showSettingsMenu = true }, modifier = Modifier.size(32.dp)) { 
                                Icon(Icons.Default.Settings, contentDescription = "설정", modifier = Modifier.size(20.dp)) 
                            }
                            DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("데이터 백업 (폴더 선택)") }, 
                                    onClick = { 
                                        showSettingsMenu = false 
                                        backupLauncher.launch(null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("데이터 복원 (파일 선택)") }, 
                                    onClick = { 
                                        showSettingsMenu = false 
                                        restoreLauncher.launch(arrayOf("application/json", "*/*"))
                                    }
                                )
                            }
                        }
                        
                        // Simple Snackbar alternative for results
                        showBackupResult?.let { msg ->
                            LaunchedEffect(msg) {
                                delay(3000)
                                showBackupResult = null
                            }
                            AlertDialog(
                                onDismissRequest = { showBackupResult = null },
                                title = { Text("알림") },
                                text = { Text(msg) },
                                confirmButton = { TextButton(onClick = { showBackupResult = null }) { Text("확인") } }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (playlistState?.type == PlaylistType.TEMPORARY) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "${playlistState?.name ?: "전체곡"} (${allSongsInQueue.size}곡)", 
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.ExtraBold, 
                            color = Color(0xFF1A237E), // Deep Indigo
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp), 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                        SortCombo(playlistState, musicServiceConnection)
                        IconButton(onClick = { musicServiceConnection.toggleSelectionMode() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.TouchApp, contentDescription = "선택 모드", tint = if (isSelectionMode) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(26.dp))
                        }
                        IconButton(onClick = { 
                            if (selectedIds.size == allSongsInQueue.size && allSongsInQueue.isNotEmpty()) musicServiceConnection.clearSelection() 
                            else musicServiceConnection.selectAll() 
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.SelectAll, contentDescription = "전체 선택/해제", tint = if (selectedIds.size == allSongsInQueue.size && allSongsInQueue.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(26.dp))
                        }
                        IconButton(enabled = selectedIds.size >= 2, onClick = { 
                            editPlaylistName = ""
                            editSelectedIds = selectedIds
                            selectedPlaylistIdForEdit = null
                            showEditPlaylistDialog = true 
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.EditNote, contentDescription = "플레이리스트 편집", tint = if (selectedIds.size >= 2) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = { showSearchDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = "통합 검색", modifier = Modifier.size(28.dp))
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistView(
                            songs = allSongsInQueue, currentTrack = currentTrack, playlistState = playlistState,
                            playedSongIds = playedSongIds, selectedIds = selectedIds, likedFingerprints = likedFingerprints,
                            onSongClick = { song ->
                                if (isSelectionMode) musicServiceConnection.toggleSelection(song.id)
                                else musicServiceConnection.playTrackById(song.id)
                            },
                            onLikeClick = { fingerprintId ->
                                musicServiceConnection.toggleLike(fingerprintId)
                            },
                            onEditTagsClick = { song ->
                                songForTagEdit = song
                            },
                            isSelectionMode = isSelectionMode
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    NowPlayingCardExpanded(track = currentTrack, isPlaying = isPlaying, currentPosition = currentPosition, duration = duration, isTasteMode = isTasteMode, scope = coroutineScope, connection = musicServiceConnection)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // --- Dialogs ---
    songForTagEdit?.let { song ->
        TagEditDialog(
            song = song,
            connection = musicServiceConnection,
            onDismiss = { songForTagEdit = null }
        )
    }

    // --- Phone-style '곡 상세 검색' Center ---
    if (showSearchDialog) {
        var localSearchQuery by remember { mutableStateOf("") }
        Dialog(
            onDismissRequest = { showSearchDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CompositionLocalProvider(LocalTextToolbar provides emptyTextToolbar) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .wrapContentHeight() // Search is simpler, so wrap height
                        .border(1.2.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 12.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Phone Notch Decor
                        Box(modifier = Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.TopCenter) {
                            Surface(modifier = Modifier.width(40.dp).height(3.dp), color = Color.Gray.copy(alpha = 0.2f), shape = RoundedCornerShape(2.dp)) {}
                        }

                        // Centered Title
                        Text(
                            text = "곡 상세 검색", 
                            fontSize = 17.sp, 
                            fontWeight = FontWeight.ExtraBold, 
                            color = MaterialTheme.colorScheme.primary, 
                            modifier = Modifier.fillMaxWidth(), 
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Search Category (Combo) + Text Input Row
                        Row(
                            modifier = Modifier.fillMaxWidth(), 
                            verticalAlignment = Alignment.CenterVertically, 
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var categoryExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(0.35f)) { // Restored compact width weight
                                OutlinedButton(
                                    onClick = { categoryExpanded = true },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(text = searchType, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                                    listOf("파일명", "가수", "앨범", "느낌", "상황", "좋아요").forEach { type ->
                                        DropdownMenuItem(text = { Text(type) }, onClick = { 
                                            searchType = type
                                            categoryExpanded = false 
                                        })
                                    }
                                }
                            }

                            // Custom Text Field to prevent clipping
                            BasicTextField(
                                value = localSearchQuery,
                                onValueChange = { localSearchQuery = it },
                                modifier = Modifier.weight(0.65f).height(42.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (localSearchQuery.isEmpty()) {
                                            Text("검색어 입력", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        innerTextField()
                                        // Bottom Line (Underline)
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(alpha = 0.5f)).align(Alignment.BottomCenter))
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Bottom Actions (40:60 ratio)
                        Row(
                            modifier = Modifier.fillMaxWidth(), 
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSearchDialog = false },
                                modifier = Modifier.weight(0.4f).height(38.dp), 
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("취소", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = { 
                                    musicServiceConnection.searchAndCreatePlaylist(localSearchQuery, searchType)
                                    showSearchDialog = false 
                                },
                                enabled = localSearchQuery.isNotBlank(),
                                modifier = Modifier.weight(0.6f).height(38.dp), 
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("검색", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Phone-style Mini Playlist Edit Center (Refined) ---
    if (showEditPlaylistDialog) {
        Dialog(
            onDismissRequest = { showEditPlaylistDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(handleColor = Color.Transparent, backgroundColor = Color.Transparent),
                LocalTextToolbar provides emptyTextToolbar
            ) {
                // Reduced Size Container (Width 0.8f, Height 0.6f)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .fillMaxHeight(0.6f)
                        .border(1.2.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 12.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        // Phone Notch Decor
                        Box(modifier = Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.TopCenter) {
                            Surface(modifier = Modifier.width(50.dp).height(4.dp), color = Color.Gray.copy(alpha = 0.2f), shape = RoundedCornerShape(2.dp)) {}
                        }
                        
                        // New Title: "플레이리스트 편집"
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "플레이리스트 편집", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            IconButton(onClick = { showEditPlaylistDialog = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "닫기", modifier = Modifier.size(20.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Labeled Playlist Selector Combo
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("플레이리스트", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            var expanded by remember { mutableStateOf(false) }
                            val userPlaylists = allPlaylists.filter { it.id != "ALL" }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                    Text(text = userPlaylists.find { it.id == selectedPlaylistIdForEdit }?.name ?: "리스트 선택", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    userPlaylists.forEach { p ->
                                        DropdownMenuItem(text = { Text(p.name) }, onClick = { 
                                            selectedPlaylistIdForEdit = p.id
                                            editPlaylistName = p.name // Auto-fill name when selected
                                            editSelectedIds = p.queue.toSet()
                                            expanded = false 
                                        })
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Name Input & Save/Update Button Row (70:30 ratio)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Custom Text Field to prevent clipping
                            BasicTextField(
                                value = editPlaylistName,
                                onValueChange = { editPlaylistName = it },
                                modifier = Modifier.weight(0.7f).height(42.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (editPlaylistName.isEmpty()) {
                                            Text("리스트 이름", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        innerTextField()
                                        // Bottom Line (Underline)
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(alpha = 0.5f)).align(Alignment.BottomCenter))
                                    }
                                }
                            )
                            Button(
                                onClick = { 
                                    val pid = selectedPlaylistIdForEdit
                                    if (pid != null) musicServiceConnection.updateCustomPlaylist(pid, editPlaylistName, editSelectedIds.toList())
                                    else musicServiceConnection.addCustomPlaylist(editPlaylistName, editSelectedIds.toList())
                                    showEditPlaylistDialog = false; musicServiceConnection.clearSelection() 
                                },
                                enabled = editPlaylistName.isNotBlank() && editSelectedIds.size >= 2,
                                modifier = Modifier.weight(0.3f).height(42.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (selectedPlaylistIdForEdit != null) "업데이트" else "저장", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Sub-header with Selection Icons moved here
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("선택된 곡 (${editSelectedIds.size}곡)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            
                            // Selection Icons moved next to count text
                            IconButton(onClick = { /* Display status only here */ }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (editSelectedIds.size == allSongsInQueue.size) editSelectedIds = emptySet()
                                    else editSelectedIds = allSongsInQueue.map { it.id }.toSet()
                                }, 
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.SelectAll, 
                                    contentDescription = "전체 선택/해제", 
                                    tint = if (editSelectedIds.size == allSongsInQueue.size) MaterialTheme.colorScheme.primary else Color.Gray, 
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // List Area with Custom Scrollbar
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            val editListState = rememberLazyListState()
                            val selectedSongsForEdit = allSongsInQueue.filter { editSelectedIds.contains(it.id) }
                            
                            LazyColumn(state = editListState, modifier = Modifier.fillMaxSize().padding(end = 24.dp)) {
                                items(selectedSongsForEdit, key = { it.id }) { song ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(song.title, fontSize = 12.sp, color = Color.Blue, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist, fontSize = 9.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = { editSelectedIds = editSelectedIds - song.id }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "제외", tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                            
                            if (selectedSongsForEdit.isNotEmpty()) {
                                var miniScrollTarget by remember { mutableIntStateOf(-1) }
                                LaunchedEffect(miniScrollTarget) {
                                    if (miniScrollTarget != -1) {
                                        while (true) {
                                            val current = editListState.firstVisibleItemIndex
                                            if (current == miniScrollTarget) break
                                            val step = if (miniScrollTarget > current) 1 else -1
                                            if (kotlin.math.abs(miniScrollTarget - current) < 2) { editListState.scrollToItem(miniScrollTarget); break }
                                            else { editListState.scrollToItem(current + step); delay(200.milliseconds) }
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(18.dp).background(Color.Gray.copy(alpha = 0.05f))
                                        .pointerInput(selectedSongsForEdit) {
                                            detectDragGestures(onDragEnd = { miniScrollTarget = -1 }, onDragCancel = { miniScrollTarget = -1 }) { change, _ ->
                                                val ratio = (change.position.y / size.height).coerceIn(0f, 1f)
                                                miniScrollTarget = (ratio * selectedSongsForEdit.size).toInt().coerceIn(0, selectedSongsForEdit.size - 1)
                                            }
                                        }
                                ) {
                                    val thumbInfo by remember(selectedSongsForEdit) { derivedStateOf {
                                        val first = editListState.firstVisibleItemIndex
                                        val vis = editListState.layoutInfo.visibleItemsInfo.size
                                        val tot = selectedSongsForEdit.size
                                        if (tot > 0) (vis.toFloat() / tot.toFloat()).coerceIn(0.1f, 1f) to (first.toFloat() / tot.toFloat()) else 0f to 0f
                                    } }
                                    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(thumbInfo.first).offset { IntOffset(0, (260 * thumbInfo.second).roundToInt()) }.padding(horizontal = 4.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
    
    // Reset Dialogs...
    if (showGlobalRefreshDialog) {
        AlertDialog(
            onDismissRequest = { showGlobalRefreshDialog = false },
            title = { Text("전체 초기화") },
            text = { Text("모든 플레이리스트의 기록과 순서를 지우고 처음으로 되돌리시겠습니까?") },
            confirmButton = { TextButton(onClick = { musicServiceConnection.globalReset(); showGlobalRefreshDialog = false }) { Text("전체 초기화", color = Color.Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showGlobalRefreshDialog = false }) { Text("취소") } }
        )
    }

    if (showLocalRefreshDialog) {
        AlertDialog(
            onDismissRequest = { showLocalRefreshDialog = false },
            title = { Text("리스트 초기화") },
            text = { Text("현재 플레이리스트의 기록과 순서만 초기화하시겠습니까?") },
            confirmButton = { TextButton(onClick = { musicServiceConnection.clearPlaybackHistory(); showLocalRefreshDialog = false }) { Text("초기화", color = Color.Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showLocalRefreshDialog = false }) { Text("취소") } }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("종료") },
            text = { Text("앱을 종료하시겠습니까?") },
            confirmButton = { TextButton(onClick = { (context as? Activity)?.finish() }) { Text("종료") } },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("취소") } }
        )
    }
}

@Composable
fun OptionIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp) 
                .then(
                    if (isSelected) Modifier.border(1.2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary 
                       else if (enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                       else Color.Gray.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PlaylistCombo(current: PlaylistState?, all: List<PlaylistState>, connection: MusicServiceConnection) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true }, 
            modifier = Modifier.fillMaxWidth().height(32.dp), 
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                current?.name ?: "플레이리스트", 
                fontSize = 14.sp, 
                fontWeight = FontWeight.Bold,
                color = Color.Blue,
                textAlign = TextAlign.Center,
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ArrowDropDown, 
                contentDescription = null, 
                tint = Color.Blue,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            all.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { connection.switchPlaylist(p.id); expanded = false }) }
        }
    }
}

@Composable
fun SortCombo(state: PlaylistState?, connection: MusicServiceConnection) {
    var expanded by remember { mutableStateOf(false) }
    val label = when(state?.sortCriteria) {
        SortCriteria.FILENAME -> "파일명"
        SortCriteria.DATE -> "수정일"
        SortCriteria.ARTIST -> "가수"
        SortCriteria.ALBUM -> "앨범"
        else -> "정렬"
    }
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.height(32.dp)) {
            Text(label, fontSize = 11.sp)
            Icon(if (state?.sortOrder == SortOrder.ASCENDING) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortCriteria.entries.forEach { criteria ->
                DropdownMenuItem(text = { Text(when(criteria) {
                    SortCriteria.FILENAME -> "파일명"
                    SortCriteria.DATE -> "수정일"
                    SortCriteria.ARTIST -> "가수"
                    SortCriteria.ALBUM -> "앨범"
                }) }, onClick = { connection.toggleSort(criteria); expanded = false })
            }
        }
    }
}

@Composable
fun TagEditDialog(
    song: MusicFile,
    connection: MusicServiceConnection,
    onDismiss: () -> Unit
) {
    var vibeTags by remember { mutableStateOf(setOf<String>()) }
    var occasionTags by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val defaultVibes = listOf("신남", "차분", "분위기", "발랄", "우울", "몽환", "강렬")
    val defaultOccasions = listOf("비오는날", "여행", "드라이브", "일할때", "운동", "휴식", "출퇴근")
    
    var newVibeInput by remember { mutableStateOf("") }
    var newOccasionInput by remember { mutableStateOf("") }
    var customVibes by remember { mutableStateOf(listOf<String>()) }
    var customOccasions by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(song.fingerprintId) {
        val meta = connection.getSongMeta(song.fingerprintId)
        if (meta != null) {
            vibeTags = meta.vibeTags.split(",").filter { it.isNotBlank() }.toSet()
            occasionTags = meta.occasionTags.split(",").filter { it.isNotBlank() }.toSet()
            // Add custom tags not in default to custom lists
            customVibes = vibeTags.filter { !defaultVibes.contains(it) }
            customOccasions = occasionTags.filter { !defaultOccasions.contains(it) }
        }
        isLoading = false
    }

    if (!isLoading) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("태그 편집", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(song.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("어떤 느낌인가요? (Vibe)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (defaultVibes + customVibes).forEach { tag ->
                            val isSelected = vibeTags.contains(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = { vibeTags = if (isSelected) vibeTags - tag else vibeTags + tag },
                                label = { Text(tag, fontSize = 12.sp) }
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = newVibeInput, onValueChange = { newVibeInput = it },
                            modifier = Modifier.weight(1f).height(32.dp).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                            singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            decorationBox = { inner -> if (newVibeInput.isEmpty()) Text("+ 직접 입력", fontSize = 12.sp, color = Color.Gray) else inner() }
                        )
                        IconButton(onClick = { if (newVibeInput.isNotBlank()) { customVibes = customVibes + newVibeInput; vibeTags = vibeTags + newVibeInput; newVibeInput = "" } }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "추가", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("어떨 때 좋나요? (Occasion)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (defaultOccasions + customOccasions).forEach { tag ->
                            val isSelected = occasionTags.contains(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = { occasionTags = if (isSelected) occasionTags - tag else occasionTags + tag },
                                label = { Text(tag, fontSize = 12.sp) }
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = newOccasionInput, onValueChange = { newOccasionInput = it },
                            modifier = Modifier.weight(1f).height(32.dp).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                            singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            decorationBox = { inner -> if (newOccasionInput.isEmpty()) Text("+ 직접 입력", fontSize = 12.sp, color = Color.Gray) else inner() }
                        )
                        IconButton(onClick = { if (newOccasionInput.isNotBlank()) { customOccasions = customOccasions + newOccasionInput; occasionTags = occasionTags + newOccasionInput; newOccasionInput = "" } }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "추가", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("취소") }
                        Button(onClick = {
                            connection.updateSongTags(song.fingerprintId, vibeTags.joinToString(","), occasionTags.joinToString(","))
                            onDismiss()
                        }) { Text("저장") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingCardExpanded(
    track: MusicFile?, isPlaying: Boolean, currentPosition: Long, duration: Long, 
    isTasteMode: Boolean, scope: kotlinx.coroutines.CoroutineScope, connection: MusicServiceConnection
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    var currentVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val title = if (track != null) "${track.title} - ${track.artist}" else "재생 중인 곡 없음"
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Slider(value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f, onValueChange = { connection.seekTo((it * duration).toLong()) }, modifier = Modifier.fillMaxWidth().height(24.dp), colors = if (isTasteMode) SliderDefaults.colors(activeTrackColor = Color.Magenta) else SliderDefaults.colors())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(currentPosition), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTime(duration), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.VolumeDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Slider(value = currentVolume, onValueChange = { currentVolume = it; audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, it.roundToInt(), 0) }, valueRange = 0f..maxVolume, modifier = Modifier.weight(0.7f).height(32.dp), colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)))
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { connection.playPrevious() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "이전곡", modifier = Modifier.size(30.dp)) }
                FilledTonalIconButton(onClick = { scope.launch { connection.togglePlayPause() } }, modifier = Modifier.size(52.dp)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "재생", modifier = Modifier.size(32.dp)) }
                IconButton(onClick = { connection.playNext() }) { Icon(Icons.Default.SkipNext, contentDescription = "다음곡", modifier = Modifier.size(30.dp)) }
                IconButton(onClick = { connection.toggleTasteMode() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.AvTimer, contentDescription = "맛보기 재생", tint = if (isTasteMode) Color.Magenta else MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp)) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Surface(modifier = Modifier.fillMaxWidth().height(60.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                    Text(text = "가사 작업예정", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistView(
    songs: List<MusicFile>, currentTrack: MusicFile?, playlistState: PlaylistState?,
    playedSongIds: Set<String>, selectedIds: Set<String>, likedFingerprints: Set<String>,
    onSongClick: (MusicFile) -> Unit, onLikeClick: (String) -> Unit, onEditTagsClick: (MusicFile) -> Unit,
    isSelectionMode: Boolean
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var scrollTargetIndex by remember { mutableIntStateOf(-1) }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    val viewConf = LocalViewConfiguration.current
    
    LaunchedEffect(currentTrack?.id) {
        val index = songs.indexOfFirst { it.id == currentTrack?.id }
        if (index != -1) {
            listState.animateScrollToItem((index - 2).coerceAtLeast(0))
        }
    }

    LaunchedEffect(scrollTargetIndex) {
        if (scrollTargetIndex != -1) {
            while (true) {
                val currentVisible = listState.firstVisibleItemIndex
                if (currentVisible == scrollTargetIndex) break
                val step = if (scrollTargetIndex > currentVisible) 1 else -1
                if (kotlin.math.abs(scrollTargetIndex - currentVisible) < 2) {
                    listState.scrollToItem(scrollTargetIndex); break
                } else {
                    listState.scrollToItem(currentVisible + step)
                    delay(200.milliseconds)
                }
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { trackHeightPx = it.size.height }.pointerInput(Unit) { 
        awaitPointerEventScope { 
            while (true) { 
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                if (event.type == PointerEventType.Scroll) { 
                    val delta = event.changes.first().scrollDelta.y
                    coroutineScope.launch { listState.scrollBy(delta * 250f) } 
                    event.changes.forEach { it.consume() }
                } 
            } 
        } 
    }) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().padding(end = 24.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(songs, key = { it.id }) { song ->
                val isCurrent = song.id == currentTrack?.id
                val isPlayed = playedSongIds.contains(song.id)
                val isSelected = selectedIds.contains(song.id)
                val isLiked = likedFingerprints.contains(song.fingerprintId)
                val isSingleRepeat = playlistState?.isSingleRepeat ?: false
                
                val alpha = if (isPlayed && !isCurrent) 0.4f else 1f
                val color = when {
                    isCurrent && isSingleRepeat -> Color.Red
                    isCurrent -> MaterialTheme.colorScheme.primary
                    isSelected -> Color.Blue
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val weight = if (isSelected || (isCurrent && isSingleRepeat)) FontWeight.Black else FontWeight.Normal

                var isBeingPressed by remember { mutableStateOf(false) }
                val animatedBgColor by animateColorAsState(
                    targetValue = if (isBeingPressed) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 200)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(animatedBgColor)
                        .pointerInput(song.id) {
                            awaitPointerEventScope {
                                val down = awaitFirstDown()
                                isBeingPressed = true
                                val startPos = down.position
                                var isCancelled = false
                                
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.first()
                                    val currentPos = change.position
                                    
                                    // Distance check for cancellation (Scroll priority)
                                    if (kotlin.math.abs(currentPos.y - startPos.y) > viewConf.touchSlop ||
                                        kotlin.math.abs(currentPos.x - startPos.x) > viewConf.touchSlop) {
                                        isCancelled = true
                                        isBeingPressed = false
                                    }
                                    
                                    if (event.type == PointerEventType.Release) {
                                        if (!isCancelled) {
                                            onSongClick(song)
                                        }
                                        isBeingPressed = false
                                        break
                                    }
                                    
                                    if (isCancelled) {
                                        break
                                    }
                                }
                            }
                        },
                    color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onLikeClick(song.fingerprintId) }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.MusicNote, 
                                contentDescription = "좋아요", 
                                tint = if (isLiked) Color.Red else Color(0xFF8BC34A).copy(alpha = alpha), 
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = song.title, color = color.copy(alpha = alpha), fontSize = 14.sp, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = song.artist, fontSize = 11.sp, color = color.copy(alpha = alpha * 0.7f), fontWeight = weight)
                        }
                        if (!isSelectionMode) {
                            IconButton(onClick = { onEditTagsClick(song) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "태그 편집", tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        
        if (songs.isNotEmpty()) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(24.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .pointerInput(songs) {
                        detectTapGestures { offset ->
                            val first = listState.firstVisibleItemIndex
                            val visibleCount = listState.layoutInfo.visibleItemsInfo.size
                            val totalCount = songs.size
                            if (totalCount > 0) {
                                val thumbTopRatio = first.toFloat() / totalCount.toFloat()
                                val thumbHeightRatio = (visibleCount.toFloat() / totalCount.toFloat()).coerceIn(0.1f, 1f)
                                val thumbTop = thumbTopRatio * size.height
                                val thumbBottom = (thumbTopRatio + thumbHeightRatio) * size.height
                                if (offset.y < thumbTop) { coroutineScope.launch { listState.animateScrollToItem((first - visibleCount).coerceAtLeast(0)) } }
                                else if (offset.y > thumbBottom) { coroutineScope.launch { listState.animateScrollToItem((first + visibleCount).coerceAtMost(totalCount - 1)) } }
                            }
                        }
                    }
                    .pointerInput(songs) { 
                        detectDragGestures(onDragEnd = { scrollTargetIndex = -1 }, onDragCancel = { scrollTargetIndex = -1 }) { change, _ -> 
                            val ratio = (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                            scrollTargetIndex = (ratio * songs.size).toInt().coerceIn(0, songs.size - 1)
                        } 
                    }
            ) {
                val thumbInfo by remember(songs) { derivedStateOf { 
                    val first = listState.firstVisibleItemIndex
                    val vis = listState.layoutInfo.visibleItemsInfo.size
                    val tot = songs.size
                    if (tot > 0) (vis.toFloat() / tot.toFloat()).coerceIn(0.1f, 1f) to (first.toFloat() / tot.toFloat()) else 0f to 0f
                } }
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(thumbInfo.first).offset { IntOffset(0, (trackHeightPx * thumbInfo.second).roundToInt()) }.padding(horizontal = 4.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)))
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
