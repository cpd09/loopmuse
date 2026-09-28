package com.example.loopmuse.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val fingerprintId: String,
    val title: String,
    val artist: String,
    val plainLyrics: String,
    val syncedLyrics: String,
    val source: String,
    val updatedAt: Long
)
