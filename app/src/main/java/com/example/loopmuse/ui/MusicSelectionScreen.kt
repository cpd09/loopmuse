package com.example.loopmuse.ui

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loopmuse.data.SelectionItem
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSelectionScreen(
    initialSelectedItems: List<SelectionItem>,
    onSelectionApplied: (List<SelectionItem>) -> Unit,
    onBackPressed: () -> Unit
) {
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var selectedItems by remember { mutableStateOf(initialSelectedItems.associateBy { it.path }.toMutableMap()) }
    
    val filesInPath = remember(currentPath) {
        currentPath.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }

    BackHandler {
        val parent = currentPath.parentFile
        if (parent != null && currentPath != Environment.getExternalStorageDirectory()) {
            currentPath = parent
        } else {
            onBackPressed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Select Music", fontSize = 18.sp)
                        Text(currentPath.absolutePath, fontSize = 12.sp, overflow = TextOverflow.Ellipsis, maxLines = 1)
                    }
                },
                navigationIcon = {
                    TextButton(onClick = {
                        val parent = currentPath.parentFile
                        if (parent != null && currentPath != Environment.getExternalStorageDirectory()) {
                            currentPath = parent
                        } else {
                            onBackPressed()
                        }
                    }) {
                        Text("Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${selectedItems.size} items selected", modifier = Modifier.align(Alignment.CenterVertically))
                    Button(onClick = { onSelectionApplied(selectedItems.values.toList()) }) {
                        Text("Apply Selection")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(filesInPath) { file ->
                FileItemRow(
                    file = file,
                    selection = selectedItems[file.absolutePath],
                    onToggleSelect = { isSelected ->
                        val newMap = selectedItems.toMutableMap()
                        if (isSelected) {
                            newMap[file.absolutePath] = SelectionItem(file.absolutePath, file.isDirectory)
                        } else {
                            newMap.remove(file.absolutePath)
                        }
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
                    onNavigate = {
                        if (file.isDirectory) {
                            currentPath = file
                        }
                    }
                )
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
                    Text("Include subfolders", fontSize = 12.sp)
                }
            }
        },
        leadingContent = {
            Icon(
                if (file.isDirectory) Icons.Default.Add else Icons.Default.Check,
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
