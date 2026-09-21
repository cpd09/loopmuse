package com.example.loopmuse.community.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.viewmodel.CommunityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityLoungeScreen(
    viewModel: CommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBackPressed: () -> Unit
) {
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    
    var showAddDialog by remember { mutableStateOf(false) }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    if (showAddDialog) {
        ShareSongDialog(
            onDismiss = { showAddDialog = false },
            onShare = { title, artist, vibes, tags, link ->
                viewModel.addPost(title, artist, vibes, tags, link)
                showAddDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LoopMuse Lounge", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Share Song")
            }
        }
    ) { padding ->
        if (posts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No songs shared yet. Be the first!")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(posts) { post ->
                    SongPostCard(
                        post = post,
                        isAdmin = isAdmin,
                        onDelete = { viewModel.deletePost(post.id) },
                        onBanUser = { viewModel.banUser(post.uid) },
                        onReport = { reason -> viewModel.reportPost(post.id, reason) }
                    )
                }
            }
        }
    }
}

@Composable
fun SongPostCard(
    post: SongPost,
    isAdmin: Boolean,
    onDelete: () -> Unit,
    onBanUser: () -> Unit,
    onReport: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    
    if (showReportDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Post") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") }
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    onReport(reason)
                    showReportDialog = false 
                }) { Text("Report") }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(post.artist, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (isAdmin) {
                            DropdownMenuItem(
                                text = { Text("Delete Post", color = MaterialTheme.colorScheme.error) },
                                onClick = { 
                                    showMenu = false
                                    onDelete() 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ban User", color = MaterialTheme.colorScheme.error) },
                                onClick = { 
                                    showMenu = false
                                    onBanUser() 
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Report") },
                                onClick = { 
                                    showMenu = false
                                    showReportDialog = true
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            // Vibes and Tags
            if (post.vibes.isNotEmpty() || post.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.vibes.forEach { vibe ->
                        AssistChip(onClick = {}, label = { Text(vibe, fontSize = 12.sp) })
                    }
                    post.tags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag, fontSize = 12.sp) })
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shared by ${post.userDisplayName}", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                if (post.link.isNotBlank()) {
                    FilledTonalButton(onClick = { /* Open Link in browser/YouTube */ }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Listen")
                    }
                }
            }
        }
    }
}

@Composable
fun ShareSongDialog(
    onDismiss: () -> Unit,
    onShare: (title: String, artist: String, vibes: List<String>, tags: List<String>, link: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var vibeInput by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share a Song") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true)
                OutlinedTextField(value = vibeInput, onValueChange = { vibeInput = it }, label = { Text("Vibe (comma separated)") }, singleLine = true)
                OutlinedTextField(value = tagInput, onValueChange = { tagInput = it }, label = { Text("Tags (comma separated)") }, singleLine = true)
                OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Listen Link (e.g., YouTube)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { 
                val vibes = vibeInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val tags = tagInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                onShare(title, artist, vibes, tags, link) 
            }, enabled = title.isNotBlank() && artist.isNotBlank()) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
