package com.example.loopmuse.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_metadata")
data class SongMetaEntity(
    @PrimaryKey
    val fingerprintId: String, // hash of "title_artist_size" to keep identity even if path changes
    val title: String, // kept for easy debugging/viewing in DB
    val artist: String,
    val isLiked: Boolean = false,
    val vibeTags: String = "", // comma-separated for now
    val occasionTags: String = "" // comma-separated for now
)