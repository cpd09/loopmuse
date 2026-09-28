package com.example.loopmuse.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/** A shown recommendation and its feedback, independent of the local music library. */
@Entity(tableName = "discovery_reactions")
data class DiscoveryReactionEntity(
    @PrimaryKey val songKey: String,
    val artist: String,
    val title: String,
    val isLiked: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val isRead: Boolean = false,
    @ColumnInfo(defaultValue = "0") val recommendedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val batchId: Long = 0L,
    @ColumnInfo(defaultValue = "0") val listenCount: Long = 0L,
    @ColumnInfo(defaultValue = "0") val isHidden: Boolean = false
)
