package com.example.loopmuse.community.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.viewmodel.CommunityViewModel
import com.example.loopmuse.ui.AppButton
import com.example.loopmuse.ui.AppOutlinedButton
import com.example.loopmuse.ui.AppCard
import com.example.loopmuse.ui.AppCardStyle
import com.example.loopmuse.ui.AppFolderTab
import com.example.loopmuse.ui.SoftBlueFolderColor
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
    requireSong: Boolean = false,
    alwaysShowSongFields: Boolean = false,
    forSongDialog: Boolean = false,
    messagePlaceholder: String = "좋았던 곡이나 오늘의 한마디를 남겨 주세요"
) {
    val context = LocalContext.current
    val showSongFields = requireSong || alwaysShowSongFields || draft.includeSong
    val messageValid = draft.message.trim().isNotEmpty() && draft.message.length <= 500
    val artistProvided = draft.artist.trim().isNotEmpty()
    val titleProvided = draft.title.trim().isNotEmpty()
    val songValid = (!requireSong || (artistProvided && titleProvided)) &&
        (!showSongFields || artistProvided == titleProvided) &&
        draft.artist.length <= 100 && draft.title.length <= 100

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.message,
            onValueChange = { if (it.length <= 500) onDraftChange(draft.copy(message = it)) },
            label = { Text("짧은 글") },
            placeholder = { Text(messagePlaceholder) },
            supportingText = { Text(draft.message.length.toString() + "/500") },
            minLines = if (forSongDialog) 5 else 2,
            maxLines = if (forSongDialog) 8 else 4,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().then(
                if (forSongDialog) Modifier.heightIn(min = 160.dp) else Modifier
            )
        )

        if (!requireSong && !alwaysShowSongFields) {
            TextButton(
                onClick = { onDraftChange(draft.copy(includeSong = !draft.includeSong)) },
                enabled = !isSubmitting
            ) {
                Text(if (draft.includeSong) "곡 링크 접기" else "+ YouTube 검색 링크 추가")
            }
        }

        if (showSongFields) {
            if (forSongDialog) {
                CompactSongField(
                    label = "가수명",
                    value = draft.artist,
                    onValueChange = { if (it.length <= 100) onDraftChange(draft.copy(artist = it)) },
                    enabled = !isSubmitting
                )
                CompactSongField(
                    label = "곡 제목",
                    value = draft.title,
                    onValueChange = { if (it.length <= 100) onDraftChange(draft.copy(title = it)) },
                    enabled = !isSubmitting
                )
            } else {
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
            }
            Text(
                if ((requireSong && (!artistProvided || !titleProvided)) || artistProvided != titleProvided)
                    "곡을 추천하려면 가수명과 곡 제목을 모두 입력해 주세요."
                else if (requireSong) "글 하단에 가수명과 곡 제목으로 YouTube 검색 링크가 표시됩니다."
                else "두 칸을 비우면 글만, 모두 입력하면 YouTube 검색 링크가 함께 게시됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = if ((requireSong && (!artistProvided || !titleProvided)) || artistProvided != titleProvided)
                    MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 300.dp
            val actionFontSize = if (compact) 12.sp else 14.sp
            val actionPadding = PaddingValues(horizontal = if (compact) 8.dp else 12.dp, vertical = 8.dp)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showSongFields) {
                    AppOutlinedButton(
                        onClick = { openYouTubeSearch(context, draft.artist, draft.title) },
                        enabled = artistProvided && titleProvided && !isSubmitting,
                        modifier = if (compact) Modifier.weight(1f) else Modifier,
                        contentPadding = actionPadding
                    ) {
                        Text("유튜브 검색테스트", fontSize = actionFontSize,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(if (compact && showSongFields) Modifier.width(8.dp) else Modifier.weight(1f))
                AppButton(
                    onClick = onPublish,
                    enabled = messageValid && songValid && !isSubmitting,
                    modifier = if (compact && showSongFields) Modifier.weight(0.65f) else Modifier,
                    contentPadding = actionPadding
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("저장하기", fontSize = actionFontSize,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSongField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    val fieldShape = RoundedCornerShape(12.dp)
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .height(44.dp)
            .clip(fieldShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                fieldShape
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).onFocusChanged { isFocused = it.isFocused }
        )
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
    var showComposer by rememberSaveable { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<String?>(null) }
    val draft = RecommendationDraft(message, artist, title)
    val postsFolderColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val writeFolderColor = SoftBlueFolderColor
    val postsListState = rememberLazyListState()

    BackHandler(enabled = showComposer) {
        if (!isSubmitting) showComposer = false
    }

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
                    IconButton(onClick = { if (showComposer) {
                        if (!isSubmitting) showComposer = false
                    } else onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppFolderTab(
                    label = "게시글",
                    icon = Icons.Default.List,
                    selected = !showComposer,
                    folderColor = postsFolderColor,
                    enabled = !isSubmitting,
                    onClick = { showComposer = false },
                    modifier = Modifier.weight(1f)
                )
                AppFolderTab(
                    label = "글 남기기",
                    icon = Icons.Default.Edit,
                    selected = showComposer,
                    folderColor = writeFolderColor,
                    enabled = !isSubmitting,
                    onClick = { showComposer = true },
                    modifier = Modifier.weight(1f)
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                color = if (showComposer) writeFolderColor else postsFolderColor,
                tonalElevation = 2.dp
            ) {
                if (showComposer) {
                    Column(
                        modifier = Modifier.fillMaxSize().imePadding()
                            .verticalScroll(rememberScrollState()).padding(AppCardStyle.horizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecommendationComposer(
                            draft = draft,
                            onDraftChange = {
                                message = it.message
                                artist = it.artist
                                title = it.title
                                publishError = null
                            },
                            isSubmitting = isSubmitting,
                            error = publishError,
                            alwaysShowSongFields = true,
                            forSongDialog = true,
                            messagePlaceholder = "인사말이나 짧은 글을 남겨 주세요",
                            onPublish = {
                                scope.launch {
                                    isSubmitting = true
                                    publishError = null
                                    val result = viewModel.addPost(draft.message, draft.title, draft.artist)
                                    isSubmitting = false
                                    if (result.isSuccess) {
                                        message = ""
                                        artist = ""
                                        title = ""
                                        showComposer = false
                                    } else {
                                        publishError = result.exceptionOrNull()?.message ?: "게시하지 못했습니다."
                                    }
                                }
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = postsListState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (posts.isEmpty()) {
                            item {
                                Text("아직 글이 없습니다. 첫 글을 남겨 주세요.",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var isExpanded by rememberSaveable(post.id) { mutableStateOf(false) }

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

    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column(modifier = Modifier.padding(horizontal = AppCardStyle.horizontalPadding,
            vertical = AppCardStyle.compactVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayName + " · " + postedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "글 메뉴",
                            modifier = Modifier.size(18.dp))
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
                Text(if (isExpanded) post.message else post.message.replace('\n', ' ').trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable(
                        onClickLabel = if (isExpanded) "글 접기" else "글 펼치기"
                    ) { isExpanded = !isExpanded })
            }

            if (hasSong) {
                Text(
                    "♪ ${post.artist} · ${post.title} ↗",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable {
                        openYouTubeSearch(context, post.artist, post.title)
                    })
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
