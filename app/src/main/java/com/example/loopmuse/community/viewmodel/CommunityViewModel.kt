package com.example.loopmuse.community.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.repository.CommunityRepository
import com.example.loopmuse.community.repository.FirebaseCommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class CommunityViewModel(
    private val repository: CommunityRepository = FirebaseCommunityRepository()
) : ViewModel() {

    private val _posts = MutableStateFlow<List<SongPost>>(emptyList())
    val posts: StateFlow<List<SongPost>> = _posts.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            if (repository.signInAnonymously() != null) {
                checkAdminStatus()
                loadPosts()
            } else {
                _errorMessage.value = "Lounge에 연결하지 못했습니다. 인터넷 연결을 확인해 주세요."
            }
        }
    }

    private fun checkAdminStatus() {
        val uid = repository.getCurrentUid() ?: return
        viewModelScope.launch {
            val result = repository.getUserProfile(uid)
            if (result.isSuccess) {
                _isAdmin.value = result.getOrNull()?.isAdmin == true
            }
        }
    }

    private fun loadPosts() {
        viewModelScope.launch {
            repository.getPosts()
                .catch { _errorMessage.value = "추천글을 불러오지 못했습니다: ${it.message}" }
                .collect { postList -> _posts.value = postList }
        }
    }

    suspend fun addPost(message: String, title: String, artist: String): Result<Unit> {
        val cleanMessage = message.trim()
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanMessage.isEmpty() || cleanMessage.length > 500) {
            return Result.failure(IllegalArgumentException("추천글은 1~500자로 입력해 주세요."))
        }
        if (cleanTitle.length > 100 || cleanArtist.length > 100 || (cleanTitle.isEmpty() != cleanArtist.isEmpty())) {
            return Result.failure(IllegalArgumentException("곡 링크를 추가하려면 가수명과 곡명을 모두 입력해 주세요."))
        }
        val uid = repository.getCurrentUid() ?: repository.signInAnonymously()
            ?: return Result.failure(IllegalStateException("Lounge에 연결하지 못했습니다. 인터넷 연결을 확인해 주세요."))
        val post = SongPost(
            message = cleanMessage,
            title = cleanTitle,
            artist = cleanArtist,
            uid = uid,
            userDisplayName = "익명"
        )
        return repository.addPost(post)
    }

    fun reportPost(postId: String, reason: String) {
        viewModelScope.launch {
            val result = repository.reportPost(postId, reason)
            if (result.isFailure) {
                _errorMessage.value = "Failed to report: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            val result = repository.deletePost(postId)
            if (result.isFailure) {
                _errorMessage.value = "Failed to delete: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun banUser(uid: String) {
        viewModelScope.launch {
            val result = repository.banUser(uid)
            if (result.isFailure) {
                _errorMessage.value = "Failed to ban user: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
