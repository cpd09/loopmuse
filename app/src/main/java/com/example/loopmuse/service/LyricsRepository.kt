package com.example.loopmuse.service

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.loopmuse.BuildConfig
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.db.AppDatabase
import com.example.loopmuse.data.db.LyricsEntity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface LyricsLoadResult {
    data class Found(val lyrics: LyricsEntity) : LyricsLoadResult
    data class Candidates(val songs: List<LyricsCandidate>) : LyricsLoadResult
    data class Unavailable(val message: String) : LyricsLoadResult
}

data class LyricsCandidate(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int?,
    val plainLyrics: String,
    val syncedLyrics: String
)

data class TimedLyricLine(val timeMs: Long, val text: String)

/** Keeps lyrics beside song metadata so normal app updates and LoopMuse backups retain them. */
class LyricsRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val dao = AppDatabase.getDatabase(context).lyricsDao()

    suspend fun load(song: MusicFile): LyricsLoadResult = withContext(Dispatchers.IO) {
        val cached = dao.getById(song.fingerprintId)
        if (cached != null && cached.source != "LRCLIB") return@withContext LyricsLoadResult.Found(cached)
        val sidecarName = "${song.file.nameWithoutExtension}.lrc"
        val sidecar = File(song.file.parentFile, sidecarName).takeIf { it.isFile }
            ?: song.file.parentFile?.listFiles()?.firstOrNull {
                it.isFile && it.name.equals(sidecarName, ignoreCase = true)
            }
        if (sidecar != null && sidecar.length() in 1..100_000) {
            val local = runCatching { decodeLocalLyrics(sidecar.readBytes()) }.getOrNull()
            if (!local.isNullOrBlank()) {
                saveLocal(song, local, "local")?.let { return@withContext it }
            }
        }
        EmbeddedLyricsReader.read(song.file)?.let { embedded ->
            saveLocal(song, embedded, "embedded")?.let { return@withContext it }
        }
        if (cached != null) return@withContext LyricsLoadResult.Found(cached)
        val (title, artist) = lyricSearchFields(song)
        if (title.isBlank() || artist.isBlank()) {
            return@withContext LyricsLoadResult.Unavailable("곡명과 가수 정보가 없어 가사를 찾을 수 없습니다.")
        }
        when (val exact = fetchExact(song, title, artist)) {
            is ExactResult.Found -> save(song, exact.candidate)
            ExactResult.NoMatch -> searchRemote(song, title, artist)
            is ExactResult.Failure -> LyricsLoadResult.Unavailable(exact.message)
        }
    }

    suspend fun search(song: MusicFile, title: String, artist: String): LyricsLoadResult = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) {
            LyricsLoadResult.Unavailable("곡명과 가수를 모두 입력해 주세요.")
        } else {
            searchRemote(song, title.trim(), artist.trim())
        }
    }

    suspend fun select(song: MusicFile, candidate: LyricsCandidate): LyricsLoadResult = withContext(Dispatchers.IO) {
        save(song, candidate)
    }

    suspend fun importLrc(song: MusicFile, uri: Uri): LyricsLoadResult = withContext(Dispatchers.IO) {
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        if (!name.endsWith(".lrc", ignoreCase = true)) {
            return@withContext LyricsLoadResult.Unavailable(".lrc 가사 파일을 선택해 주세요.")
        }
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    if (output.size() + read > 100_000) return@use null
                    output.write(chunk, 0, read)
                }
                output.toByteArray()
            }
        }.getOrNull() ?: return@withContext LyricsLoadResult.Unavailable("가사 파일을 읽지 못했습니다. 100KB 이하의 파일을 선택해 주세요.")
        val content = decodeLocalLyrics(bytes)
            ?: return@withContext LyricsLoadResult.Unavailable("가사 파일의 내용을 읽지 못했습니다.")
        saveLocal(song, content, "imported")
            ?: LyricsLoadResult.Unavailable("표시할 가사가 없는 파일입니다.")
    }

    private suspend fun saveLocal(song: MusicFile, content: String, source: String): LyricsLoadResult.Found? {
        val timed = parseTimedLyrics(content)
        val plain = if (timed.isNotEmpty()) timed.joinToString("\n") { it.text }
            else content.lineSequence().filterNot { it.trim().matches(Regex("\\[(ti|ar|al|by|offset):.*]", RegexOption.IGNORE_CASE)) }
                .joinToString("\n").trim()
        if (plain.isBlank() || plain.length > 100_000 || content.length > 100_000) return null
        val entity = LyricsEntity(song.fingerprintId, song.title, song.artist,
            plain, if (timed.isNotEmpty()) content else "", source, System.currentTimeMillis())
        dao.insertOrUpdate(entity)
        return LyricsLoadResult.Found(entity)
    }

    private suspend fun save(song: MusicFile, candidate: LyricsCandidate): LyricsLoadResult.Found {
        val plain = candidate.plainLyrics.ifBlank {
            parseTimedLyrics(candidate.syncedLyrics).joinToString("\n") { it.text }
        }
        val entity = LyricsEntity(song.fingerprintId, candidate.title, candidate.artist,
            plain, candidate.syncedLyrics, "LRCLIB", System.currentTimeMillis())
        dao.insertOrUpdate(entity)
        return LyricsLoadResult.Found(entity)
    }

    private sealed interface ExactResult {
        data class Found(val candidate: LyricsCandidate) : ExactResult
        data object NoMatch : ExactResult
        data class Failure(val message: String) : ExactResult
    }

    private suspend fun fetchExact(song: MusicFile, title: String, artist: String): ExactResult {
        val params = buildList {
            add("track_name" to title)
            add("artist_name" to artist)
            song.album.takeUnless { it.isBlank() || it == "Unknown Album" }?.let { add("album_name" to it) }
            (song.duration / 1000).takeIf { it in 1..3600 }?.let { add("duration" to it.toString()) }
        }.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        return when (val response = request("https://lrclib.net/api/get?$params")) {
            is LyricsResponse.Success -> {
                val candidate = runCatching {
                    parseCandidate(JsonParser.parseString(response.body).asJsonObject)
                }.getOrNull() ?: return ExactResult.NoMatch
                if (candidate.title.equals(title, ignoreCase = true) &&
                    candidate.artist.equals(artist, ignoreCase = true)) ExactResult.Found(candidate)
                else ExactResult.NoMatch
            }
            LyricsResponse.NotFound -> ExactResult.NoMatch
            is LyricsResponse.Failure -> ExactResult.Failure(response.message)
        }
    }

    private suspend fun searchRemote(song: MusicFile, title: String, artist: String): LyricsLoadResult {
        val params = "track_name=${URLEncoder.encode(title, "UTF-8")}&artist_name=${URLEncoder.encode(artist, "UTF-8")}"
        val structured = fetchSearch("https://lrclib.net/api/search?$params")
        if (structured is SearchResult.Failure) return LyricsLoadResult.Unavailable(structured.message)
        var candidates = (structured as SearchResult.Found).candidates
        if (candidates.none { lyricNameStrength(title, it.title) > 0 &&
                lyricNameStrength(artist, it.artist) > 0 }) {
            val titleOnly = fetchSearch("https://lrclib.net/api/search?track_name=${URLEncoder.encode(title, "UTF-8")}")
            if (titleOnly is SearchResult.Failure) return LyricsLoadResult.Unavailable(titleOnly.message)
            candidates += (titleOnly as SearchResult.Found).candidates
        }
        val ranked = rankLyricsCandidates(title, artist, song.album, song.duration, candidates)
        if (ranked.isEmpty()) return LyricsLoadResult.Unavailable(
            "가사를 찾지 못했습니다. 곡명과 가수를 고쳐 다시 찾아보세요.")
        val automatic = chooseAutomaticLyrics(title, artist, song.album, song.duration, ranked)
        return if (automatic != null) save(song, automatic) else LyricsLoadResult.Candidates(ranked)
    }

    private sealed interface SearchResult {
        data class Found(val candidates: List<LyricsCandidate>) : SearchResult
        data class Failure(val message: String) : SearchResult
    }

    private suspend fun fetchSearch(url: String): SearchResult {
        return when (val response = request(url)) {
            is LyricsResponse.Success -> {
                val candidates = runCatching {
                    JsonParser.parseString(response.body).asJsonArray.mapNotNull { item ->
                        runCatching { parseCandidate(item.asJsonObject) }.getOrNull()
                    }
                }.getOrNull() ?: return SearchResult.Failure("가사 검색 결과를 읽지 못했습니다.")
                SearchResult.Found(candidates)
            }
            LyricsResponse.NotFound -> SearchResult.Found(emptyList())
            is LyricsResponse.Failure -> SearchResult.Failure(response.message)
        }
    }

    private fun parseCandidate(json: JsonObject): LyricsCandidate? {
        fun field(name: String) = json.get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty().trim()
        val title = field("trackName")
        val artist = field("artistName")
        val plain = field("plainLyrics")
        val synced = field("syncedLyrics")
        if (title.isBlank() || artist.isBlank() || (plain.isBlank() && synced.isBlank()) ||
            plain.length > 100_000 || synced.length > 100_000) return null
        return LyricsCandidate(
            id = json.get("id")?.takeUnless { it.isJsonNull }?.asLong ?: 0L,
            title = title,
            artist = artist,
            album = field("albumName"),
            durationSeconds = json.get("duration")?.takeUnless { it.isJsonNull }?.asDouble?.roundToInt(),
            plainLyrics = plain,
            syncedLyrics = synced
        )
    }

    private sealed interface LyricsResponse {
        data class Success(val body: String) : LyricsResponse
        data object NotFound : LyricsResponse
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
                    404 -> LyricsResponse.NotFound
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

internal fun decodeLocalLyrics(bytes: ByteArray): String? {
    if (bytes.isEmpty() || bytes.size > 100_000) return null
    val decoded = try {
        when {
            bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() &&
                bytes[2] == 0xbf.toByte() -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 2 && ((bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) ||
                (bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte())) -> String(bytes, Charsets.UTF_16)
            else -> runCatching {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            }.getOrElse { String(bytes, Charset.forName("EUC-KR")) }
        }
    } catch (_: Exception) {
        return null
    }
    return decoded.trim('\uFEFF', '\u0000', ' ', '\n', '\r').takeIf {
        it.isNotBlank() && '\u0000' !in it && it.count { char -> char.isISOControl() && char != '\n' && char != '\r' && char != '\t' } == 0
    }
}

internal fun rankLyricsCandidates(
    title: String,
    artist: String,
    album: String,
    durationMs: Long,
    candidates: List<LyricsCandidate>
): List<LyricsCandidate> = candidates
    .filter { lyricNameStrength(title, it.title) > 0 }
    .sortedByDescending { lyricCandidateScore(title, artist, album, durationMs, it) }
    .distinctBy {
        listOf(compactLyricName(it.title), compactLyricName(it.artist),
            compactLyricName(it.album), it.durationSeconds.toString()).joinToString("|")
    }
    .take(8)

internal fun chooseAutomaticLyrics(
    title: String,
    artist: String,
    album: String,
    durationMs: Long,
    ranked: List<LyricsCandidate>
): LyricsCandidate? {
    val top = ranked.firstOrNull() ?: return null
    if (lyricNameStrength(title, top.title) != 4 || lyricNameStrength(artist, top.artist) != 4) return null
    val durationDifference = top.durationSeconds?.let { abs(it * 1000L - durationMs) }
    if (durationMs > 0L && durationDifference != null && durationDifference > 8_000L) return null
    val next = ranked.getOrNull(1)
    return top.takeIf { next == null || lyricCandidateScore(title, artist, album, durationMs, top) -
        lyricCandidateScore(title, artist, album, durationMs, next) >= 5 }
}

internal fun lyricNameStrength(query: String, candidate: String): Int {
    val target = compactLyricName(query)
    val full = compactLyricName(candidate)
    if (target.isBlank() || full.isBlank()) return 0
    if (target == full || Regex("[()\\[\\]{}]").split(candidate).any { compactLyricName(it) == target }) return 4
    return if (target.length >= 2 && (full.contains(target) ||
            (full.length >= 2 && target.contains(full)))) 2 else 0
}

private fun compactLyricName(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

private fun lyricCandidateScore(
    title: String,
    artist: String,
    album: String,
    durationMs: Long,
    candidate: LyricsCandidate
): Int {
    val durationScore = if (durationMs <= 0L || candidate.durationSeconds == null) 0 else {
        when (abs(candidate.durationSeconds * 1000L - durationMs)) {
            in 0..2_000 -> 8
            in 2_001..8_000 -> 5
            in 8_001..20_000 -> 0
            else -> -15
        }
    }
    return lyricNameStrength(title, candidate.title) * 10 +
        lyricNameStrength(artist, candidate.artist) * 10 + durationScore +
        (if (album.isNotBlank() && lyricNameStrength(album, candidate.album) == 4) 2 else 0) +
        (if (candidate.syncedLyrics.isNotBlank()) 1 else 0)
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
