package com.example.loopmuse.data

import java.io.File

data class MusicFile(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val folder: String,
    val dateAdded: Long,
    val file: File
) {
    companion object {
        fun fromFile(file: File): MusicFile {
            val fileName = file.nameWithoutExtension
            return MusicFile(
                id = file.absolutePath.hashCode().toString(),
                title = fileName,
                artist = "Unknown Artist",
                album = "Unknown Album",
                duration = 0L,
                path = file.absolutePath,
                folder = file.parent ?: "",
                dateAdded = file.lastModified(),
                file = file
            )
        }
    }
}