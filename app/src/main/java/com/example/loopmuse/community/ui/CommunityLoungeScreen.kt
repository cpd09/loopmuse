package com.example.loopmuse.community.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.model.PostComment
import com.example.loopmuse.community.model.PostVote
import com.example.loopmuse.community.repository.CommunityBlockManager
import com.example.loopmuse.community.viewmodel.CommunityViewModel
import com.example.loopmuse.ui.AppButton
import com.example.loopmuse.ui.AppOutlinedButton
import com.example.loopmuse.ui.AppCard
import com.example.loopmuse.ui.AppCardStyle
import com.example.loopmuse.ui.AppFolderTab
import com.example.loopmuse.ui.SoftBlueFolderColor
import com.example.loopmuse.ui.SoftGreenFolderColor
import com.example.loopmuse.ui.DiscoveryRecommendationsPage
import com.example.loopmuse.ui.openYouTubeSearch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val LoungeLikeGreen = Color(0xFF43A047)
private val LoungeDislikeRed = Color(0xFFE53935)
private const val EmptyReactionAlpha = 0.38f

private data class ThreadedComment(val comment: PostComment, val depth: Int)

private fun threadComments(comments: List<PostComment>): List<ThreadedComment> {
    val byParent = comments.groupBy { it.parentCommentId }
    val knownIds = comments.mapTo(mutableSetOf()) { it.id }
    val visited = mutableSetOf<String>()
    val ordered = mutableListOf<ThreadedComment>()

    fun append(comment: PostComment, depth: Int) {
        if (!visited.add(comment.id)) return
        ordered += ThreadedComment(comment, depth)
        byParent[comment.id].orEmpty().forEach { append(it, depth + 1) }
    }

    comments.filter { it.parentCommentId.isBlank() || it.parentCommentId !in knownIds }
        .forEach { append(it, 0) }
    comments.forEach { append(it, 0) }
    return ordered
}

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
                else "가수·곡명을 입력하면 YouTube 링크가 붙습니다.",
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
    val isBanned by viewModel.isBanned.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        CommunityBlockManager.init(context)
    }
    val blockedUids by CommunityBlockManager.blockedUids.collectAsStateWithLifecycle()
    var userToBlock by remember { mutableStateOf<String?>(null) }
    var showBlockedListDialog by remember { mutableStateOf(false) }

    val visiblePosts = remember(posts, blockedUids) {
        posts.filter { it.uid !in blockedUids }
    }

    var message by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var showComposer by rememberSaveable { mutableStateOf(false) }
    var showDiscovery by rememberSaveable { mutableStateOf(false) }
    var selectedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedReplyCommentId by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<String?>(null) }
    val draft = RecommendationDraft(message, artist, title)
    val postsFolderColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val writeFolderColor = SoftBlueFolderColor
    val discoveryFolderColor = SoftGreenFolderColor
    val postsListState = rememberLazyListState()
    val composerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler(enabled = showDiscovery && !showComposer) {
        showDiscovery = false
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
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (showComposer || showDiscovery) {
                                if (!isSubmitting) {
                                    showComposer = false
                                    showDiscovery = false
                                }
                            } else onBackPressed()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "LoopMuse Lounge",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppFolderTab(
                    label = "공감 게시판",
                    icon = null,
                    selected = !showDiscovery,
                    folderColor = postsFolderColor,
                    enabled = !isSubmitting,
                    onClick = { showComposer = false; showDiscovery = false },
                    modifier = Modifier.weight(1f)
                )
                AppFolderTab(
                    label = "앱 추천",
                    icon = Icons.Default.MusicNote,
                    selected = showDiscovery,
                    folderColor = discoveryFolderColor,
                    enabled = !isSubmitting,
                    onClick = { showComposer = false; showDiscovery = true },
                    modifier = Modifier.weight(1f)
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                color = if (showDiscovery) discoveryFolderColor else postsFolderColor,
                tonalElevation = 2.dp
            ) {
                if (showDiscovery) {
                    DiscoveryRecommendationsPage(modifier = Modifier.fillMaxSize())
                } else if (isBanned) {
                    Text("이 계정은 Lounge 이용이 제한되어 있습니다. 음악 재생과 알람은 계속 사용할 수 있습니다.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = postsListState,
                            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(key = "lounge_operating_guide_notice") {
                                LoungeNoticeCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    blockedCount = blockedUids.size,
                                    onManageBlocks = { showBlockedListDialog = true }
                                )
                            }
                            if (visiblePosts.isEmpty()) {
                                item {
                                    Text(
                                        if (posts.isNotEmpty() && blockedUids.isNotEmpty()) "차단된 사용자의 글을 제외하여 표시할 글이 없습니다."
                                        else "아직 글이 없습니다. 첫 글을 남겨 주세요.",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(visiblePosts, key = { it.id }) { post ->
                                    if (post.isDeleting) {
                                        AppCard(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Text("삭제 처리 중인 글", modifier = Modifier.weight(1f))
                                                if (isAdmin || post.uid == viewModel.currentUid) {
                                                    TextButton(onClick = { viewModel.deletePost(post.id) }) {
                                                        Text("다시 시도")
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        val votesFlow = remember(post.id, viewModel) { viewModel.votes(post.id) }
                                        val votes by votesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                                        val commentsFlow = remember(post.id, viewModel) { viewModel.comments(post.id) }
                                        val comments by commentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                                        SongPostCard(
                                            post = post,
                                            isAdmin = isAdmin,
                                            currentUid = viewModel.currentUid,
                                            votes = votes,
                                            comments = comments,
                                            viewModel = viewModel,
                                            onDelete = { viewModel.deletePost(post.id) },
                                            onBanUser = { viewModel.banUser(post.uid) },
                                            onBlockUser = { uid -> userToBlock = uid },
                                            onReport = { reason -> viewModel.reportPost(post.id, reason) },
                                            onVote = { value -> viewModel.setVote(post.id, value) },
                                            onOpenComments = {
                                                selectedReplyCommentId = null
                                                selectedPostId = post.id
                                            },
                                            onReplyToComment = { commentId ->
                                                selectedReplyCommentId = commentId
                                                selectedPostId = post.id
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        FloatingActionButton(
                            onClick = {
                                publishError = null
                                showDiscovery = false
                                showComposer = true
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "글 남기기")
                        }
                    }
                }
            }
        }
    }

    if (showComposer) {
        ModalBottomSheet(
            onDismissRequest = { if (!isSubmitting) showComposer = false },
            sheetState = composerSheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppCardStyle.horizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("글 남기기", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (!isSubmitting) showComposer = false }) {
                        Icon(Icons.Default.Close, contentDescription = "글쓰기 닫기")
                    }
                }
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = writeFolderColor),
                    elevation = 6.dp
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
                        modifier = Modifier.padding(AppCardStyle.horizontalPadding, AppCardStyle.verticalPadding),
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
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (userToBlock != null) {
        val targetUid = userToBlock!!
        AlertDialog(
            onDismissRequest = { userToBlock = null },
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "사용자 차단",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Text(
                    text = "이 사용자를 차단하시겠습니까?\n차단하면 해당 사용자가 작성한 모든 게시글과 댓글이 내 화면에서 즉시 숨겨집니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                AppButton(
                    onClick = {
                        CommunityBlockManager.blockUser(context, targetUid)
                        userToBlock = null
                        Toast.makeText(context, "해당 사용자를 차단했습니다. 작성한 글과 댓글이 숨겨집니다.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("차단하기")
                }
            },
            dismissButton = {
                AppOutlinedButton(
                    onClick = { userToBlock = null },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("취소")
                }
            }
        )
    }

    if (showBlockedListDialog) {
        BlockedUsersDialog(
            blockedUids = blockedUids,
            onUnblock = { uid ->
                CommunityBlockManager.unblockUser(context, uid)
                Toast.makeText(context, "차단을 해제했습니다.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showBlockedListDialog = false }
        )
    }
}

@Composable
fun SongPostCard(
    post: SongPost,
    isAdmin: Boolean,
    currentUid: String?,
    votes: List<PostVote>,
    comments: List<PostComment>,
    viewModel: CommunityViewModel,
    onDelete: () -> Unit,
    onBanUser: () -> Unit,
    onBlockUser: (String) -> Unit = {},
    onReport: (String) -> Unit,
    onVote: (Int) -> Unit,
    onOpenComments: () -> Unit,
    onReplyToComment: (String) -> Unit
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
    val ownVote = votes.firstOrNull { it.uid == currentUid }?.value ?: 0
    val likes = votes.count { it.value == 1 }
    val dislikes = votes.count { it.value == -1 }
    val blockedUids by CommunityBlockManager.blockedUids.collectAsStateWithLifecycle()
    val visibleComments = remember(comments, blockedUids) { comments.filter { it.uid !in blockedUids } }
    val commentCount = visibleComments.count { !it.isDeleted }
    val threadedComments = remember(visibleComments) { threadComments(visibleComments) }
    val replyCounts = remember(visibleComments) {
        visibleComments.filter { !it.isDeleted && it.parentCommentId.isNotBlank() }
            .groupingBy { it.parentCommentId }.eachCount()
    }

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

    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).clickable(
        onClickLabel = if (isExpanded) "글과 댓글 접기" else "글과 댓글 펼치기"
    ) { isExpanded = !isExpanded }) {
        val cardVerticalPadding = 4.dp
        val itemSpacing = 2.dp // 이름 아래 및 요소 간 여백: 카드 상하여백(4dp)의 50%
        var showCommentInput by rememberSaveable(post.id) { mutableStateOf(false) }

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = cardVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$displayName · $postedAt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PostReactionAction(
                        icon = if (ownVote == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        count = likes,
                        color = LoungeLikeGreen.copy(alpha = if (likes == 0) EmptyReactionAlpha else 1f),
                        label = if (ownVote == 1) "좋아요 취소" else "좋아요",
                        onClick = { onVote(if (ownVote == 1) 0 else 1) }
                    )
                    PostReactionAction(
                        icon = if (ownVote == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        count = dislikes,
                        color = LoungeDislikeRed.copy(alpha = if (dislikes == 0) EmptyReactionAlpha else 1f),
                        label = if (ownVote == -1) "싫어요 취소" else "싫어요",
                        onClick = { onVote(if (ownVote == -1) 0 else -1) }
                    )
                    PostReactionAction(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        count = commentCount,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (commentCount == 0) EmptyReactionAlpha else 1f
                        ),
                        label = "댓글 쓰기",
                        onClick = {
                            isExpanded = true
                            showCommentInput = !showCommentInput
                        }
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "글 메뉴",
                            modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (isAdmin || post.uid == currentUid) {
                            DropdownMenuItem(
                                text = { Text("글 삭제") },
                                onClick = { showMenu = false; onDelete() }
                            )
                        }
                        if (isAdmin) {
                            DropdownMenuItem(
                                text = { Text("사용자 차단 (관리자)") },
                                onClick = { showMenu = false; onBanUser() }
                            )
                        }
                        if (post.uid != currentUid) {
                            DropdownMenuItem(
                                text = { Text("이 사용자 차단") },
                                onClick = {
                                    showMenu = false
                                    onBlockUser(post.uid)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("신고") },
                                onClick = { showMenu = false; showReportDialog = true }
                            )
                        }
                    }
                }
            }

            if (hasSong) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSystemInDarkTheme()) Color(0xFF1E293B) else Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0xFF334155) else Color(0xFFBFDBFE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClickLabel = "YouTube에서 음악 검색") {
                            openYouTubeSearch(context, post.artist, post.title)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (isSystemInDarkTheme()) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${post.artist} · ${post.title}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSystemInDarkTheme()) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "음악 검색",
                            tint = if (isSystemInDarkTheme()) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            if (post.message.isNotBlank()) {
                val foldedMaxLines = if (hasSong) 1 else 2
                Text(
                    text = post.message,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else foldedMaxLines,
                    overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (isExpanded) {
                if (showCommentInput) {
                    SlimCommentInputCard(
                        submitLabel = "댓글쓰기",
                        placeholder = "댓글을 입력하세요...",
                        onSubmit = { msg ->
                            val result = viewModel.addComment(post.id, msg, "")
                            if (result.isSuccess) {
                                showCommentInput = false
                                null
                            } else {
                                result.exceptionOrNull()?.message ?: "댓글을 올리지 못했습니다."
                            }
                        },
                        onCancel = { showCommentInput = false }
                    )
                }
                if (threadedComments.isNotEmpty()) {
                    threadedComments.forEach { item ->
                        key(item.comment.id) {
                            PostCommentRow(
                                postId = post.id,
                                comment = item.comment,
                                depth = item.depth,
                                replyCount = replyCounts[item.comment.id] ?: 0,
                                currentUid = currentUid,
                                isAdmin = isAdmin,
                                viewModel = viewModel,
                                onBlockUser = onBlockUser
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlimCommentInputCard(
    submitLabel: String = "댓글쓰기",
    placeholder: String,
    onSubmit: suspend (String) -> String?,
    onCancel: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSystemInDarkTheme()) Color(0xFF1E293B) else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0xFF334155) else Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            BasicTextField(
                value = text,
                onValueChange = {
                    if (it.length <= 500) {
                        text = it
                        error = null
                    }
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        if (isSystemInDarkTheme()) Color(0xFF0F172A) else Color.White,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        if (isSystemInDarkTheme()) Color(0xFF334155) else Color(0xFFCBD5E1),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )
            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${text.length}/500",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(22.dp)
                ) {
                    Text(
                        "취소",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                AppButton(
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            val err = onSubmit(text.trim())
                            isSubmitting = false
                            if (err != null) {
                                error = err
                            }
                        }
                    },
                    enabled = text.isNotBlank() && !isSubmitting,
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(22.dp)
                ) {
                    Text(submitLabel, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PostReactionAction(
    icon: ImageVector,
    count: Int,
    color: Color,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.width(38.dp).height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = "$label ${count}개", tint = color,
            modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(2.dp))
        Text("$count", fontSize = 11.sp, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PostCommentRow(
    postId: String,
    comment: PostComment,
    depth: Int,
    replyCount: Int,
    currentUid: String?,
    isAdmin: Boolean,
    viewModel: CommunityViewModel,
    onBlockUser: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showReplyInput by rememberSaveable(comment.id) { mutableStateOf(false) }
    val votes = if (comment.isDeleted) {
        emptyList()
    } else {
        val votesFlow = remember(postId, comment.id, viewModel) {
            viewModel.commentVotes(postId, comment.id)
        }
        val currentVotes by votesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        currentVotes
    }
    val ownVote = votes.firstOrNull { it.uid == currentUid }?.value ?: 0
    val likes = votes.count { it.value == 1 }
    val dislikes = votes.count { it.value == -1 }
    val canDelete = isAdmin || comment.uid == currentUid
    val canReport = comment.uid != currentUid
    val postedAt = remember(comment.timestamp) {
        SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date(comment.timestamp))
    }
    if (showReportDialog && !comment.isDeleted) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("댓글 신고") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 200) reason = it },
                    label = { Text("신고 사유") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reportComment(postId, comment.id, reason)
                    showReportDialog = false
                },
                    enabled = reason.isNotBlank()) { Text("신고") }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text("취소") } }
        )
    }
    AppCard(Modifier.fillMaxWidth().padding(start = (depth.coerceAtMost(2) * 12).dp, top = 2.dp, bottom = 2.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${comment.userDisplayName} · $postedAt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 4.dp))
                if (!comment.isDeleted) {
                    PostReactionAction(
                        icon = if (ownVote == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        count = likes,
                        color = LoungeLikeGreen.copy(alpha = if (likes == 0) EmptyReactionAlpha else 1f),
                        label = if (ownVote == 1) "댓글 좋아요 취소" else "댓글 좋아요",
                        onClick = {
                            viewModel.setCommentVote(postId, comment.id, if (ownVote == 1) 0 else 1)
                        }
                    )
                    PostReactionAction(
                        icon = if (ownVote == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        count = dislikes,
                        color = LoungeDislikeRed.copy(alpha = if (dislikes == 0) EmptyReactionAlpha else 1f),
                        label = if (ownVote == -1) "댓글 싫어요 취소" else "댓글 싫어요",
                        onClick = {
                            viewModel.setCommentVote(postId, comment.id, if (ownVote == -1) 0 else -1)
                        }
                    )
                    PostReactionAction(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        count = replyCount,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (replyCount == 0) EmptyReactionAlpha else 1f
                        ),
                        label = "답글 쓰기",
                        onClick = { showReplyInput = !showReplyInput }
                    )
                }
                if (!comment.isDeleted && (canDelete || canReport)) {
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "댓글 메뉴",
                                modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (canDelete) DropdownMenuItem(text = { Text("댓글 삭제") },
                                onClick = {
                                    showMenu = false
                                    viewModel.deleteComment(postId, comment.id)
                                })
                            if (canReport) {
                                DropdownMenuItem(text = { Text("이 사용자 차단") },
                                    onClick = {
                                        showMenu = false
                                        onBlockUser(comment.uid)
                                    })
                                DropdownMenuItem(text = { Text("댓글 신고") },
                                    onClick = { showMenu = false; showReportDialog = true })
                            }
                        }
                    }
                }
            }
            Text(if (comment.isDeleted) "삭제된 댓글입니다." else comment.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (comment.isDeleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface)
            if (showReplyInput) {
                SlimCommentInputCard(
                    submitLabel = "답글쓰기",
                    placeholder = "${comment.userDisplayName}님에게 답글을 입력하세요...",
                    onSubmit = { msg ->
                        val result = viewModel.addComment(postId, msg, comment.id)
                        if (result.isSuccess) {
                            showReplyInput = false
                            null
                        } else {
                            result.exceptionOrNull()?.message ?: "답글을 올리지 못했습니다."
                        }
                    },
                    onCancel = { showReplyInput = false }
                )
            }
        }
    }
}

@Composable
private fun LoungeNoticeCard(
    modifier: Modifier = Modifier,
    blockedCount: Int = 0,
    onManageBlocks: () -> Unit = {}
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isDark) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = if (isExpanded) "운영 가이드 접기" else "운영 가이드 펼치기") {
                isExpanded = !isExpanded
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.35f else 0.12f),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = "공지",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "라운지 커뮤니티 운영 가이드",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isExpanded) "접기" else "수칙 보기",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = if (isExpanded) "접기" else "펼치기",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(
                        color = borderColor.copy(alpha = 0.5f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    LoungeGuidelineItem(
                        icon = "🎵",
                        title = "음악과 따뜻한 공감의 공간",
                        desc = "LoopMuse 라운지는 서로에게 좋은 곡을 추천하고 소소한 일상을 편안하게 나누는 공간입니다."
                    )
                    LoungeGuidelineItem(
                        icon = "⚠️",
                        title = "금지 행위 (위반 시 콘텐츠 삭제 및 이용 제한)",
                        desc = "• 타인에 대한 욕설, 비방, 혐오 표현 및 모욕\n• 음란물, 폭력성, 불법 콘텐츠 및 청소년 유해 정보\n• 상업적 광고/스팸, 무단 도배 및 허위 사실 유포"
                    )
                    LoungeGuidelineItem(
                        icon = "🛡️",
                        title = "이용자 보호 (신고 및 사용자 차단)",
                        desc = "• 부적절한 글이나 댓글은 우측 메뉴(⋮)에서 즉시 '신고'할 수 있습니다.\n• 불쾌한 작성자는 '이 사용자 차단'을 통해 내 화면에서 즉시 숨길 수 있습니다."
                    )

                    if (blockedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "현재 차단한 사용자: ${blockedCount}명",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = onManageBlocks,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Text(
                                        "차단 관리 >",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoungeGuidelineItem(
    icon: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BlockedUsersDialog(
    blockedUids: Set<String>,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("차단한 사용자 관리", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            if (blockedUids.isEmpty()) {
                Text(
                    "차단된 사용자가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    blockedUids.forEach { uid ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "사용자 (${uid.take(8)}...)",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = { onUnblock(uid) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        "차단 해제",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("닫기")
            }
        }
    )
}

