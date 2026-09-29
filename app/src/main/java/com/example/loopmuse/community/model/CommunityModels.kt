package com.example.loopmuse.community.model

import java.util.UUID

data class SongPost(
    val id: String = UUID.randomUUID().toString(),
    val message: String = "",
    val title: String = "",
    val artist: String = "",
    val vibes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val link: String = "", // YouTube or Spotify Link
    val timestamp: Long = System.currentTimeMillis(),
    val uid: String = "", // The UID of the user who posted
    val userDisplayName: String = "Anonymous",
    val isDeleting: Boolean = false
)

data class Report(
    val id: String = UUID.randomUUID().toString(),
    val postId: String = "",
    val commentId: String = "",
    val reporterUid: String = "",
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class PostComment(
    val id: String = UUID.randomUUID().toString(),
    val postId: String = "",
    val parentCommentId: String = "",
    val message: String = "",
    val uid: String = "",
    val userDisplayName: String = "익명",
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class PostVote(
    val uid: String = "",
    val value: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class UserProfile(
    val uid: String = "",
    val isAdmin: Boolean = false,
    val isBanned: Boolean = false
)
