package com.example.loopmuse.community.repository

import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface CommunityRepository {
    suspend fun signInAnonymously(): String?
    fun getCurrentUid(): String?
    
    fun getPosts(): Flow<List<SongPost>>
    suspend fun addPost(post: SongPost): Result<Unit>
    suspend fun deletePost(postId: String): Result<Unit>
    
    suspend fun reportPost(postId: String, reason: String): Result<Unit>
    
    suspend fun getUserProfile(uid: String): Result<UserProfile>
    suspend fun banUser(uid: String): Result<Unit>
}
