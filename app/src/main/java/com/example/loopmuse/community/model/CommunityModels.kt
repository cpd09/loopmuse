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
    val userDisplayName: String = "Anonymous"
)

data class Report(
    val id: String = UUID.randomUUID().toString(),
    val postId: String = "",
    val reporterUid: String = "",
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class UserProfile(
    val uid: String = "",
    val isAdmin: Boolean = false,
    val isBanned: Boolean = false
)
