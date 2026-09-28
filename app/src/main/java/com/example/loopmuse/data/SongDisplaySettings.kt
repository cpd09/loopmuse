package com.example.loopmuse.data

import android.content.Context

enum class SongDisplayField(val label: String) {
    NONE("없음"),
    FILE_NAME("파일명"),
    ARTIST("가수"),
    TITLE("곡명"),
    GENRE("장르"),
    ALBUM("앨범명");

    fun value(song: MusicFile): String = when (this) {
        NONE -> ""
        FILE_NAME -> song.file.name
        ARTIST -> song.artist.takeUnless { it == "Unknown Artist" }.orEmpty()
        TITLE -> song.title
        GENRE -> song.genre
        ALBUM -> song.album.takeUnless { it == "Unknown Album" }.orEmpty()
    }.trim()
}

data class SongDisplayConfig(
    val fields: List<SongDisplayField> = listOf(
        SongDisplayField.TITLE, SongDisplayField.ARTIST,
        SongDisplayField.NONE, SongDisplayField.NONE
    )
) {
    fun normalized(): SongDisplayConfig {
        val unique = mutableSetOf<SongDisplayField>()
        val slots = fields.take(4).map { field ->
            if (field == SongDisplayField.NONE || !unique.add(field)) SongDisplayField.NONE else field
        }
        return copy(fields = (slots + List(4) { SongDisplayField.NONE }).take(4))
    }

    fun format(song: MusicFile): String = normalized().fields
        .map { it.value(song) }
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .ifBlank { song.title }
}

// Reads the single-choice setting from the previous app version/backups.
enum class SongDisplayMode { TITLE_ONLY, ARTIST, GENRE, ARTIST_GENRE;
    fun toConfig(): SongDisplayConfig = SongDisplayConfig(when (this) {
        TITLE_ONLY -> listOf(SongDisplayField.TITLE)
        ARTIST -> listOf(SongDisplayField.TITLE, SongDisplayField.ARTIST)
        GENRE -> listOf(SongDisplayField.TITLE, SongDisplayField.GENRE)
        ARTIST_GENRE -> listOf(SongDisplayField.TITLE, SongDisplayField.ARTIST, SongDisplayField.GENRE)
    }).normalized()
}

object SongDisplaySettings {
    private const val PREFS = "music_prefs"
    private const val KEY = "song_display_fields"
    private const val LEGACY_KEY = "song_display_mode"

    fun read(context: Context): SongDisplayConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        if (raw != null) {
            return SongDisplayConfig(raw.split(",").map { name ->
                SongDisplayField.entries.firstOrNull { it.name == name } ?: SongDisplayField.NONE
            }).normalized()
        }
        val oldMode = prefs.getString(LEGACY_KEY, null)?.let { name ->
            SongDisplayMode.entries.firstOrNull { it.name == name }
        }
        return oldMode?.toConfig() ?: SongDisplayConfig()
    }

    fun save(context: Context, config: SongDisplayConfig) {
        val value = config.normalized().fields.joinToString(",") { it.name }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value).remove(LEGACY_KEY).apply()
    }
}
