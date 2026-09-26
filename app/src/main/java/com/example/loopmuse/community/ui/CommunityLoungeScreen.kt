package com.example.loopmuse.community.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.viewmodel.CommunityViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

data class RecommendationDraft(
    val message: String = "",
    val artist: String = "",
    val title: String = "",
    val includeSong: Boolean = false
)

@Composable
fun RecommendationComposer(
    draft: RecommendationDraft,
    onDraftChange: (RecommendationDraft) -> Unit,
    isSubmitting: Boolean,
    error: String?,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
    requireSong: Boolean = false
) {
    val showSongFields = requireSong || draft.includeSong
    val messageValid = draft.message.trim().isNotEmpty() && draft.message.length <= 500
    val songValid = !showSongFields || (
        draft.artist.trim().isNotEmpty() && draft.title.trim().isNotEmpty() &&
            draft.artist.length <= 100 && draft.title.length <= 100
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.message,
            onValueChange = { if (it.length <= 500) onDraftChange(draft.copy(message = it)) },
            label = { Text("짧은 글") },
            placeholder = { Text("좋았던 곡이나 오늘의 한마디를 남겨 주세요") },
            supportingText = { Text(draft.message.length.toString() + "/500") },
            minLines = 2,
            maxLines = 4,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )

        if (!requireSong) {
            TextButton(
                onClick = { onDraftChange(draft.copy(includeSong = !draft.includeSong)) },
                enabled = !isSubmitting
            ) {
                Text(if (draft.includeSong) "곡 링크 접기" else "+ YouTube 검색 링크 추가")
            }
        }

        if (showSongFields) {
            OutlinedTextField(
                value = draft.artist,
                onValueChange = { if (it.length <= 100) onDraftChange(draft.copy(artist = it)) },
                label = { Text("가수명") },
                singleLine = true,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { if (it.length <= 100) onDraftChange(draft.copy(title = it)) },
                label = { Text("곡 제목") },
                singleLine = true,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "글 하단에 가수명과 곡 제목으로 YouTube 검색 링크가 표시됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onPublish, enabled = messageValid && songValid && !isSubmitting) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("게시")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityLoungeScreen(
    viewModel: CommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBackPressed: () -> Unit
) {
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var message by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var includeSong by rememberSaveable { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<String?>(null) }
    val draft = RecommendationDraft(message, artist, title, includeSong)

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Lounge 오류") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("확인") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LoopMuse Lounge", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    RecommendationComposer(
                        draft = draft,
                        onDraftChange = {
                            message = it.message
                            artist = it.artist
                            title = it.title
                            includeSong = it.includeSong
                            publishError = null
                        },
                        isSubmitting = isSubmitting,
                        error = publishError,
                        onPublish = {
                            scope.launch {
                                isSubmitting = true
                                publishError = null
                                val result = viewModel.addPost(
                                    draft.message,
                                    if (draft.includeSong) draft.title else "",
                                    if (draft.includeSong) draft.artist else ""
                                )
                                isSubmitting = false
                                if (result.isSuccess) {
                                    message = ""
                                    artist = ""
                                    title = ""
                                    includeSong = false
                                } else {
                                    publishError = result.exceptionOrNull()?.message ?: "게시하지 못했습니다."
                                }
                            }
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (posts.isEmpty()) {
                item {
                    Text(
                        "아직 글이 없습니다. 첫 글을 남겨 주세요.",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(posts, key = { it.id }) { post ->
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
    val context = LocalContext.current
    val hasSong = post.artist.isNotBlank() && post.title.isNotBlank()
    val displayName = when (post.userDisplayName) {
        "", "Anonymous", "LoopMuse Fan" -> "익명"
        else -> post.userDisplayName
    }
    val postedAt = remember(post.timestamp) {
        SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date(post.timestamp))
    }
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    if (showReportDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("글 신고") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 200) reason = it },
                    label = { Text("신고 사유") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReport(reason.trim())
                        showReportDialog = false
                    },
                    enabled = reason.isNotBlank()
                ) { Text("신고") }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text("취소") } }
        )
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayName + " · " + postedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "글 메뉴")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (isAdmin) {
                            DropdownMenuItem(
                                text = { Text("글 삭제") },
                                onClick = { showMenu = false; onDelete() }
                            )
                            DropdownMenuItem(
                                text = { Text("사용자 차단") },
                                onClick = { showMenu = false; onBanUser() }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("신고") },
                                onClick = { showMenu = false; showReportDialog = true }
                            )
                        }
                    }
                }
            }

            if (post.message.isNotBlank()) {
                Text(post.message, style = MaterialTheme.typography.bodyLarge)
            }

            if (hasSong) {
                Text(
                    "♪ " + post.artist + " · " + post.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { openYouTubeSearch(context, post.artist, post.title) }) {
                    Text("YouTube에서 이 곡 검색 ↗")
                }
            }
        }
    }
}

private fun openYouTubeSearch(context: Context, artist: String, title: String) {
    val uri = Uri.parse("https://www.youtube.com/results")
        .buildUpon()
        .appendQueryParameter("search_query", artist.trim() + " " + title.trim())
        .build()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "웹 링크를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
    }
}
