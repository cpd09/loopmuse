package com.example.loopmuse.ui

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loopmuse.data.SelectionItem
import com.example.loopmuse.service.MusicScanner
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSelectionScreen(
    musicScanner: MusicScanner,
    initialSelectedItems: List<SelectionItem>,
    onSelectionApplied: (List<SelectionItem>) -> Unit,
    onBackPressed: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("추천 폴더", "전체 폴더")
    
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var selectedItems by remember { mutableStateOf(initialSelectedItems.associateBy { it.path }.toMutableMap()) }
    
    val recommendedFolders = remember { musicScanner.getRecommendedFolders() }
    val filesInPath = remember(currentPath) {
        currentPath.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }

    BackHandler {
        if (selectedTabIndex == 1 && currentPath != Environment.getExternalStorageDirectory()) {
            currentPath = currentPath.parentFile ?: Environment.getExternalStorageDirectory()
        } else {
            onBackPressed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("곡,폴더 선택") },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedItems.size}개 선택됨", fontSize = 14.sp)
                    Button(onClick = { onSelectionApplied(selectedItems.values.toList()) }) {
                        Text("선택완료")
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (selectedTabIndex == 1) {
                // Breadcrumbs for "All Folders"
                Breadcrumbs(currentPath) { path -> currentPath = path }
            }
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (selectedTabIndex == 0) {
                    items(recommendedFolders) { folder ->
                        FileItemRow(
                            file = folder,
                            selection = selectedItems[folder.absolutePath],
                            onToggleSelect = { isSelected ->
                                val newMap = selectedItems.toMutableMap()
                                if (isSelected) newMap[folder.absolutePath] = SelectionItem(folder.absolutePath, true)
                                else newMap.remove(folder.absolutePath)
                                selectedItems = newMap
                            },
                            onToggleSubfolders = { include ->
                                val item = selectedItems[folder.absolutePath]
                                if (item != null) {
                                    val newMap = selectedItems.toMutableMap()
                                    newMap[folder.absolutePath] = item.copy(includeSubfolders = include)
                                    selectedItems = newMap
                                }
                            },
                            onNavigate = { currentPath = folder; selectedTabIndex = 1 }
                        )
                    }
                } else {
                    items(filesInPath) { file ->
                        FileItemRow(
                            file = file,
                            selection = selectedItems[file.absolutePath],
                            onToggleSelect = { isSelected ->
                                val newMap = selectedItems.toMutableMap()
                                if (isSelected) newMap[file.absolutePath] = SelectionItem(file.absolutePath, file.isDirectory)
                                else newMap.remove(file.absolutePath)
                                selectedItems = newMap
                            },
                            onToggleSubfolders = { include ->
                                val item = selectedItems[file.absolutePath]
                                if (item != null) {
                                    val newMap = selectedItems.toMutableMap()
                                    newMap[file.absolutePath] = item.copy(includeSubfolders = include)
                                    selectedItems = newMap
                                }
                            },
                            onNavigate = { if (file.isDirectory) currentPath = file }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Breadcrumbs(currentPath: File, onPathClick: (File) -> Unit) {
    val scrollState = rememberScrollState()
    val root = Environment.getExternalStorageDirectory()
    val parts = currentPath.absolutePath.removePrefix(root.parent ?: "").split("/").filter { it.isNotEmpty() }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onPathClick(root) }) {
            Icon(Icons.Default.Home, contentDescription = "Home")
        }
        parts.forEachIndexed { index, part ->
            Text("/", color = Color.Gray)
            TextButton(onClick = {
                val base = root.parentFile?.absolutePath ?: ""
                val targetPath = base + "/" + parts.take(index + 1).joinToString("/")
                val targetFile = File(targetPath)
                if (targetFile.exists()) onPathClick(targetFile)
            }) {
                Text(part, maxLines = 1)
            }
        }
    }
}

@Composable
fun FileItemRow(
    file: File,
    selection: SelectionItem?,
    onToggleSelect: (Boolean) -> Unit,
    onToggleSubfolders: (Boolean) -> Unit,
    onNavigate: () -> Unit
) {
    val isSupported = file.isDirectory || listOf("mp3", "m4a", "wav", "flac", "ogg").contains(file.extension.lowercase())
    if (!isSupported && !file.isDirectory) return

    ListItem(
        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (file.isDirectory && selection != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selection.includeSubfolders,
                        onCheckedChange = onToggleSubfolders,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("하위 폴더 포함", fontSize = 12.sp)
                }
            }
        },
        leadingContent = {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else Color.Gray
            )
        },
        trailingContent = {
            Checkbox(
                checked = selection != null,
                onCheckedChange = onToggleSelect
            )
        },
        modifier = Modifier.clickable { if (file.isDirectory) onNavigate() else onToggleSelect(selection == null) }
    )
}
