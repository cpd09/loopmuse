package com.example.loopmuse.service

import android.content.Context
import com.example.loopmuse.BuildConfig
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.db.AppDatabase
import com.example.loopmuse.data.db.LyricsEntity
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed interface LyricsLoadResult {
    data class Found(val lyrics: LyricsEntity) : LyricsLoadResult
    data class Unavailable(val message: String) : LyricsLoadResult
}

data class TimedLyricLine(val timeMs: Long, val text: String)

/** Keeps lyrics beside song metadata so normal app updates and LoopMuse backups retain them. */
class LyricsRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).lyricsDao()

    suspend fun load(song: MusicFile): LyricsLoadResult = withContext(Dispatchers.IO) {
        dao.getById(song.fingerprintId)?.let { return@withContext LyricsLoadResult.Found(it) }
        val sidecar = File(song.file.parentFile, "${song.file.nameWithoutExtension}.lrc")
        if (sidecar.isFile && sidecar.length() in 1..100_000) {
            val local = runCatching { sidecar.readText(Charsets.UTF_8) }.getOrNull()
            if (!local.isNullOrBlank()) {
                val timed = parseTimedLyrics(local)
                val entity = LyricsEntity(song.fingerprintId, song.title, song.artist,
                    if (timed.isNotEmpty()) timed.joinToString("\n") { it.text } else local.trim(),
                    if (timed.isNotEmpty()) local else "", "local", System.currentTimeMillis())
                dao.insertOrUpdate(entity)
                return@withContext LyricsLoadResult.Found(entity)
            }
        }
        val (title, artist) = lyricSearchFields(song)
        if (title.isBlank() || artist.isBlank()) {
            return@withContext LyricsLoadResult.Unavailable("곡명과 가수 정보가 없어 가사를 찾을 수 없습니다.")
        }
        fetch(song, title, artist, useSongDetails = true)
    }

    suspend fun search(song: MusicFile, title: String, artist: String): LyricsLoadResult = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) {
            LyricsLoadResult.Unavailable("곡명과 가수를 모두 입력해 주세요.")
        } else {
            fetch(song, title.trim(), artist.trim(), useSongDetails = false)
        }
    }

    private suspend fun fetch(song: MusicFile, title: String, artist: String, useSongDetails: Boolean): LyricsLoadResult {
        val params = buildList {
            add("track_name" to title)
            add("artist_name" to artist)
            if (useSongDetails) {
                song.album.takeUnless { it.isBlank() || it == "Unknown Album" }?.let { add("album_name" to it) }
                (song.duration / 1000).takeIf { it in 1..3600 }?.let { add("duration" to it.toString()) }
            }
        }.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        return when (val response = request("https://lrclib.net/api/get?$params")) {
            is LyricsResponse.Success -> {
                val json = runCatching { JsonParser.parseString(response.body).asJsonObject }.getOrNull()
                    ?: return LyricsLoadResult.Unavailable("가사 응답을 읽지 못했습니다.")
                val returnedTitle = json.get("trackName")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                val returnedArtist = json.get("artistName")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                if (!returnedTitle.equals(title, ignoreCase = true) || !returnedArtist.equals(artist, ignoreCase = true)) {
                    return LyricsLoadResult.Unavailable("일치하는 곡의 가사를 찾지 못했습니다. 곡 정보를 바꿔 다시 찾아보세요.")
                }
                val plain = json.get("plainLyrics")?.takeUnless { it.isJsonNull }?.asString.orEmpty().trim()
                val synced = json.get("syncedLyrics")?.takeUnless { it.isJsonNull }?.asString.orEmpty().trim()
                if (plain.isBlank() && synced.isBlank()) {
                    return LyricsLoadResult.Unavailable("이 곡에 표시할 가사가 없습니다.")
                }
                if (plain.length > 100_000 || synced.length > 100_000) {
                    return LyricsLoadResult.Unavailable("가사 데이터가 너무 큽니다.")
                }
                val entity = LyricsEntity(song.fingerprintId, returnedTitle, returnedArtist,
                    plain.ifBlank { parseTimedLyrics(synced).joinToString("\n") { it.text } },
                    synced, "LRCLIB", System.currentTimeMillis())
                dao.insertOrUpdate(entity)
                LyricsLoadResult.Found(entity)
            }
            is LyricsResponse.Failure -> LyricsLoadResult.Unavailable(response.message)
        }
    }

    private sealed interface LyricsResponse {
        data class Success(val body: String) : LyricsResponse
        data class Failure(val message: String) : LyricsResponse
    }

    private suspend fun request(url: String): LyricsResponse = requestMutex.withLock {
        val remaining = nextAllowedAt - System.currentTimeMillis()
        if (remaining > 0) return@withLock LyricsResponse.Failure("잠시 후 가사를 다시 찾아보세요.")
        val spacing = lastRequestAt + 350 - System.currentTimeMillis()
        if (spacing > 0) delay(spacing)
        lastRequestAt = System.currentTimeMillis()
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "LoopMuse/${BuildConfig.VERSION_NAME} (https://github.com/cpd09/loopmuse)")
            }
            try {
                when (connection.responseCode) {
                    200 -> {
                        if (connection.contentLength > 250_000) LyricsResponse.Failure("가사 응답이 너무 큽니다.")
                        else {
                            val body = connection.inputStream.bufferedReader().use { it.readText().take(250_001) }
                            if (body.length > 250_000) LyricsResponse.Failure("가사 응답이 너무 큽니다.")
                            else LyricsResponse.Success(body)
                        }
                    }
                    404 -> LyricsResponse.Failure("가사를 찾지 못했습니다. 곡명과 가수를 확인해 주세요.")
                    429 -> {
                        val seconds = connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1, 3600) ?: 60L
                        nextAllowedAt = System.currentTimeMillis() + seconds * 1000
                        LyricsResponse.Failure("가사 검색 요청이 많습니다. ${seconds}초 후 다시 시도해 주세요.")
                    }
                    else -> LyricsResponse.Failure("가사 서비스에 연결하지 못했습니다.")
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LyricsResponse.Failure("인터넷 연결을 확인한 뒤 다시 시도해 주세요.")
        }
    }

    companion object {
        private val requestMutex = Mutex()
        private var lastRequestAt = 0L
        private var nextAllowedAt = 0L
    }
}

fun lyricSearchFields(song: MusicFile): Pair<String, String> {
    if (song.artist.isNotBlank() && song.artist != "Unknown Artist") return song.title.trim() to song.artist.trim()
    val parts = song.title.split(" - ", limit = 2)
    return if (parts.size == 2) parts[1].trim() to parts[0].trim() else song.title.trim() to ""
}

fun parseTimedLyrics(source: String): List<TimedLyricLine> {
    val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    return source.lineSequence().flatMap { raw ->
        val matches = stamp.findAll(raw).toList()
        val text = stamp.replace(raw, "").trim()
        if (text.isBlank()) emptySequence() else matches.asSequence().map { match ->
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            TimedLyricLine((minutes * 60 + seconds) * 1000 + fraction, text)
        }
    }.sortedBy { it.timeMs }.toList()
}
