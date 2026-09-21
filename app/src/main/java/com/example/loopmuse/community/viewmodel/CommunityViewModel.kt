package com.example.loopmuse.community.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.repository.CommunityRepository
import com.example.loopmuse.community.repository.FirebaseCommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            repository.signInAnonymously()
            checkAdminStatus()
            loadPosts()
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
            repository.getPosts().collect { postList ->
                _posts.value = postList
            }
        }
    }

    fun addPost(title: String, artist: String, vibes: List<String>, tags: List<String>, link: String) {
        val uid = repository.getCurrentUid() ?: return
        val post = SongPost(
            title = title,
            artist = artist,
            vibes = vibes,
            tags = tags,
            link = link,
            uid = uid,
            userDisplayName = "LoopMuse Fan"
        )
        viewModelScope.launch {
            val result = repository.addPost(post)
            if (result.isFailure) {
                _errorMessage.value = "Failed to share song: ${result.exceptionOrNull()?.message}"
            }
        }
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
