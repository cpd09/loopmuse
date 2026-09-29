package com.example.loopmuse.community.repository

import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.model.PostComment
import com.example.loopmuse.community.model.PostVote
import com.example.loopmuse.community.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface CommunityRepository {
    fun getCurrentUid(): String?
    
    fun getPosts(): Flow<List<SongPost>>
    suspend fun addPost(post: SongPost): Result<Unit>
    suspend fun deletePost(postId: String): Result<Unit>
    
    suspend fun reportPost(postId: String, reason: String): Result<Unit>
    fun getComments(postId: String): Flow<List<PostComment>>
    suspend fun addComment(postId: String, message: String, parentCommentId: String = ""): Result<Unit>
    suspend fun deleteComment(postId: String, commentId: String): Result<Unit>
    suspend fun reportComment(postId: String, commentId: String, reason: String): Result<Unit>
    fun getCommentVotes(postId: String, commentId: String): Flow<List<PostVote>>
    suspend fun setCommentVote(postId: String, commentId: String, value: Int): Result<Unit>
    fun getVotes(postId: String): Flow<List<PostVote>>
    suspend fun setVote(postId: String, value: Int): Result<Unit>
    
    suspend fun getUserProfile(uid: String): Result<UserProfile>
    suspend fun banUser(uid: String): Result<Unit>
}
