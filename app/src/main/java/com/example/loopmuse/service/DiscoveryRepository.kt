package com.example.loopmuse.service

import android.content.Context
import com.example.loopmuse.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class DiscoverySong(
    val songKey: String,
    val artist: String,
    val title: String,
    val listenCount: Long,
    val rank: Int
)

data class DiscoveryFeed(
    val songs: List<DiscoverySong>,
    val loadedAt: Long,
    val isOfflineCopy: Boolean
)

fun discoverySongKey(artist: String, title: String): String {
    fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    return "${normalize(artist)}\u0000${normalize(title)}"
}

/** Public ListenBrainz listening statistics. This is not a YouTube view chart. */
class DiscoveryRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("discovery_feed", Context.MODE_PRIVATE)
    private val endpoint = "https://api.listenbrainz.org/1/stats/sitewide/recordings?range=week&count=500"
    private val maxCacheAgeMs = 6 * 60 * 60 * 1000L

    suspend fun load(forceRefresh: Boolean = false): DiscoveryFeed = withContext(Dispatchers.IO) {
        val cachedJson = prefs.getString("weekly_json_v2", null)
        val cachedAt = prefs.getLong("weekly_loaded_at_v2", 0L)
        val cachedSongs = cachedJson?.let { runCatching { parse(it) }.getOrNull() }.orEmpty()
        if (!forceRefresh && cachedSongs.isNotEmpty() && System.currentTimeMillis() - cachedAt < maxCacheAgeMs) {
            return@withContext DiscoveryFeed(cachedSongs, cachedAt, false)
        }

        try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LoopMuse/${BuildConfig.VERSION_NAME} (https://github.com/cpd09/loopmuse)")
            }
            val json = try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("추천곡을 불러오지 못했습니다. (${connection.responseCode})")
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val songs = parse(json)
            if (songs.isEmpty()) throw IllegalStateException("이번 주 추천곡이 아직 없습니다.")
            val now = System.currentTimeMillis()
            prefs.edit().putString("weekly_json_v2", json).putLong("weekly_loaded_at_v2", now).apply()
            DiscoveryFeed(songs, now, false)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (cachedSongs.isNotEmpty()) DiscoveryFeed(cachedSongs, cachedAt, true)
            else throw error
        }
    }

    private fun parse(json: String): List<DiscoverySong> {
        val rows = JSONObject(json).getJSONObject("payload").getJSONArray("recordings")
        val songs = mutableListOf<DiscoverySong>()
        val seen = mutableSetOf<String>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val artist = row.optString("artist_name").trim()
            val title = row.optString("track_name").trim()
            if (artist.isEmpty() || title.isEmpty()) continue
            val key = discoverySongKey(artist, title)
            if (!seen.add(key)) continue
            songs += DiscoverySong(key, artist, title, row.optLong("listen_count"), index + 1)
        }
        return songs
    }
}
