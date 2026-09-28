package com.example.loopmuse.data

import java.io.File

data class MusicFile(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "",
    val duration: Long,
    val path: String,
    val folder: String,
    val dateAdded: Long,
    val file: File
) {
    // Keep the existing identity after switching display/search fields to embedded tags.
    // Older versions used the filename, "Unknown Artist", and file length.
    val fingerprintId: String
        get() = "${file.nameWithoutExtension}_Unknown Artist_${file.length()}".hashCode().toString()

    companion object {
        fun fromFile(
            file: File,
            tagTitle: String? = null,
            tagArtist: String? = null,
            tagAlbum: String? = null,
            tagGenre: String? = null,
            tagDurationMs: Long = 0L
        ): MusicFile {
            val fileName = file.nameWithoutExtension
            return MusicFile(
                id = file.absolutePath.hashCode().toString(),
                title = tagTitle?.takeIf { it.isNotBlank() } ?: fileName,
                artist = tagArtist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                album = tagAlbum?.takeIf { it.isNotBlank() } ?: "Unknown Album",
                genre = tagGenre?.trim().orEmpty(),
                duration = tagDurationMs,
                path = file.absolutePath,
                folder = file.parent ?: "",
                dateAdded = file.lastModified(),
                file = file
            )
        }
    }
}
