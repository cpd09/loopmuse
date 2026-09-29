package com.example.loopmuse.community.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.model.PostComment
import com.example.loopmuse.community.model.PostVote
import com.example.loopmuse.community.repository.CommunityRepository
import com.example.loopmuse.community.repository.FirebaseCommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class CommunityViewModel(
    private val repository: CommunityRepository = FirebaseCommunityRepository()
) : ViewModel() {

    val currentUid: String? = repository.getCurrentUid()

    private val _posts = MutableStateFlow<List<SongPost>>(emptyList())
    val posts: StateFlow<List<SongPost>> = _posts.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _isBanned = MutableStateFlow(false)
    val isBanned: StateFlow<Boolean> = _isBanned.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = repository.getCurrentUid()
            if (uid != null) {
                val profile = repository.getUserProfile(uid)
                if (profile.isSuccess) {
                    _isAdmin.value = profile.getOrNull()?.isAdmin == true
                    _isBanned.value = profile.getOrNull()?.isBanned == true
                    if (!_isBanned.value) loadPosts()
                } else {
                    _errorMessage.value = "Lounge 이용 상태를 확인하지 못했습니다. 인터넷 연결을 확인해 주세요."
                }
            } else {
                _errorMessage.value = "Google 로그인이 필요합니다."
            }
        }
    }

    private fun loadPosts() {
        viewModelScope.launch {
            repository.getPosts()
                .catch { _errorMessage.value = "게시글을 불러오지 못했습니다. 네트워크나 이용 권한을 확인해 주세요." }
                .collect { postList -> _posts.value = postList }
        }
    }

    suspend fun addPost(message: String, title: String, artist: String): Result<Unit> {
        if (_isBanned.value) return Result.failure(IllegalStateException("Lounge 이용이 제한된 계정입니다."))
        val cleanMessage = message.trim()
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanMessage.isEmpty() || cleanMessage.length > 500) {
            return Result.failure(IllegalArgumentException("추천글은 1~500자로 입력해 주세요."))
        }
        if (cleanTitle.length > 100 || cleanArtist.length > 100 || (cleanTitle.isEmpty() != cleanArtist.isEmpty())) {
            return Result.failure(IllegalArgumentException("곡 링크를 추가하려면 가수명과 곡명을 모두 입력해 주세요."))
        }
        val uid = repository.getCurrentUid()
            ?: return Result.failure(IllegalStateException("Lounge에 연결하지 못했습니다. 인터넷 연결을 확인해 주세요."))
        val post = SongPost(
            message = cleanMessage,
            title = cleanTitle,
            artist = cleanArtist,
            uid = uid,
            userDisplayName = "익명"
        )
        val result = repository.addPost(post)
        return if (result.isSuccess) result else Result.failure(
            IllegalStateException("글을 올리지 못했습니다. 네트워크나 Lounge 이용 권한을 확인해 주세요.")
        )
    }

    fun reportPost(postId: String, reason: String) {
        if (_isBanned.value) {
            _errorMessage.value = "Lounge 이용이 제한된 계정입니다."
            return
        }
        viewModelScope.launch {
            val result = repository.reportPost(postId, reason)
            if (result.isFailure) {
                _errorMessage.value = "신고하지 못했습니다. 네트워크나 Lounge 이용 권한을 확인해 주세요."
            }
        }
    }

    fun comments(postId: String): Flow<List<PostComment>> = repository.getComments(postId)
        .catch { _errorMessage.value = "댓글을 불러오지 못했습니다. 인터넷 연결을 확인해 주세요." }

    fun votes(postId: String): Flow<List<PostVote>> = repository.getVotes(postId)
        .catch { _errorMessage.value = "좋아요 정보를 불러오지 못했습니다. 인터넷 연결을 확인해 주세요." }

    fun commentVotes(postId: String, commentId: String): Flow<List<PostVote>> =
        repository.getCommentVotes(postId, commentId)
            .catch { _errorMessage.value = "댓글의 좋아요 정보를 불러오지 못했습니다. 인터넷 연결을 확인해 주세요." }

    suspend fun addComment(postId: String, message: String, parentCommentId: String = ""): Result<Unit> {
        if (_isBanned.value) return Result.failure(IllegalStateException("Lounge 이용이 제한된 계정입니다."))
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty() || cleanMessage.length > 500) {
            return Result.failure(IllegalArgumentException("댓글은 1~500자로 입력해 주세요."))
        }
        return repository.addComment(postId, cleanMessage, parentCommentId).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(IllegalStateException("댓글을 올리지 못했습니다. 네트워크나 Lounge 이용 권한을 확인해 주세요.")) }
        )
    }

    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch {
            if (repository.deleteComment(postId, commentId).isFailure) {
                _errorMessage.value = "댓글을 삭제하지 못했습니다. 이용 권한을 확인해 주세요."
            }
        }
    }

    fun reportComment(postId: String, commentId: String, reason: String) {
        if (_isBanned.value) {
            _errorMessage.value = "Lounge 이용이 제한된 계정입니다."
            return
        }
        viewModelScope.launch {
            if (repository.reportComment(postId, commentId, reason.trim()).isFailure) {
                _errorMessage.value = "댓글을 신고하지 못했습니다. 네트워크나 Lounge 이용 권한을 확인해 주세요."
            }
        }
    }

    fun setVote(postId: String, value: Int) {
        if (_isBanned.value || value !in -1..1) {
            _errorMessage.value = "좋아요를 변경할 수 없습니다. Lounge 이용 상태를 확인해 주세요."
            return
        }
        viewModelScope.launch {
            if (repository.setVote(postId, value).isFailure) {
                _errorMessage.value = "좋아요를 변경하지 못했습니다. 네트워크나 Lounge 이용 권한을 확인해 주세요."
            }
        }
    }

    fun setCommentVote(postId: String, commentId: String, value: Int) {
        if (_isBanned.value || value !in -1..1) {
            _errorMessage.value = "댓글의 좋아요를 변경할 수 없습니다. Lounge 이용 상태를 확인해 주세요."
            return
        }
        viewModelScope.launch {
            if (repository.setCommentVote(postId, commentId, value).isFailure) {
                _errorMessage.value = "댓글의 좋아요를 변경하지 못했습니다. 네트워크나 이용 권한을 확인해 주세요."
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            val result = repository.deletePost(postId)
            if (result.isFailure) {
                _errorMessage.value = "글을 삭제하지 못했습니다. 이용 권한을 확인해 주세요."
            }
        }
    }

    fun banUser(uid: String) {
        if (uid == repository.getCurrentUid()) {
            _errorMessage.value = "자신의 계정은 제한할 수 없습니다."
            return
        }
        viewModelScope.launch {
            val result = repository.banUser(uid)
            if (result.isFailure) {
                _errorMessage.value = "사용자 이용 제한을 적용하지 못했습니다. 관리자 권한을 확인해 주세요."
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
