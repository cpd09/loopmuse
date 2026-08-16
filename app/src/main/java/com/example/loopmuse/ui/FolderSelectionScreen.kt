package com.example.loopmuse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loopmuse.service.MusicScanner
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSelectionScreen(
    musicScanner: MusicScanner,
    selectedFolders: List<String>,
    onFoldersSelected: (List<String>) -> Unit,
    onBackPressed: () -> Unit
) {
    var availableFolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFolderPaths by remember { mutableStateOf(selectedFolders.toSet()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            availableFolders = musicScanner.getAvailableFolders()
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackPressed) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Select Music Folders",
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column {
                Text(
                    text = "Choose folders to include in random playback:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            selectedFolderPaths = emptySet()
                        }
                    ) {
                        Text("Clear All")
                    }
                    
                    TextButton(
                        onClick = {
                            selectedFolderPaths = availableFolders.map { it.absolutePath }.toSet()
                        }
                    ) {
                        Text("Select All")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(availableFolders) { folder ->
                        FolderItem(
                            folder = folder,
                            isSelected = selectedFolderPaths.contains(folder.absolutePath),
                            onSelectionChanged = { isSelected ->
                                selectedFolderPaths = if (isSelected) {
                                    selectedFolderPaths + folder.absolutePath
                                } else {
                                    selectedFolderPaths - folder.absolutePath
                                }
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        onFoldersSelected(selectedFolderPaths.toList())
                        onBackPressed()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Selection (${selectedFolderPaths.size} folders)")
                }
            }
        }
    }
}

@Composable
fun FolderItem(
    folder: File,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChanged
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontSize = 16.sp
                )
                Text(
                    text = folder.absolutePath,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}