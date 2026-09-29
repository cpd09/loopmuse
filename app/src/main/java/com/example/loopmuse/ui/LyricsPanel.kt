package com.example.loopmuse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.service.LyricsCandidate
import com.example.loopmuse.service.LyricsLoadResult
import com.example.loopmuse.service.LyricsRepository
import com.example.loopmuse.service.lyricSearchFields
import com.example.loopmuse.service.parseTimedLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LyricsPanel(song: MusicFile?, currentPosition: Long) {
    val context = LocalContext.current
    val repository = remember(context) { LyricsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var result by remember(song?.fingerprintId) { mutableStateOf<LyricsLoadResult?>(null) }
    var loading by remember(song?.fingerprintId) { mutableStateOf(false) }
    var searchOpen by remember(song?.fingerprintId) { mutableStateOf(false) }
    var expanded by remember(song?.fingerprintId) { mutableStateOf(false) }
    val lyrics = (result as? LyricsLoadResult.Found)?.lyrics

    LaunchedEffect(song?.fingerprintId) {
        if (song != null) {
            loading = true
            result = repository.load(song)
            loading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().height(88.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Row(modifier = Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("가사", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lyrics != null) {
                    Text(" · ${if (lyrics.source == "local") "내 파일" else "LRCLIB"}",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (song != null) {
                    IconButton(onClick = { searchOpen = true }, modifier = Modifier.height(26.dp)) {
                        Icon(Icons.Default.Search, contentDescription = "가사 다시 찾기", modifier = Modifier.height(17.dp))
                    }
                }
                if (lyrics != null) {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.height(26.dp)) {
                        Icon(Icons.Default.OpenInFull, contentDescription = "가사 전체 보기", modifier = Modifier.height(17.dp))
                    }
                }
            }
            when {
                song == null -> LyricsMessage("재생 중인 곡이 없습니다.")
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                }
                lyrics != null -> {
                    val timed = remember(lyrics.syncedLyrics) { parseTimedLyrics(lyrics.syncedLyrics) }
                    if (timed.isNotEmpty()) {
                        val active = timed.indexOfLast { it.timeMs <= currentPosition }
                        val start = (active - 1).coerceIn(0, (timed.size - 3).coerceAtLeast(0))
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            repeat(3) { index ->
                                val itemIndex = start + index
                                val line = timed.getOrNull(itemIndex)
                                Text(
                                    text = line?.text.orEmpty(),
                                    modifier = Modifier.fillMaxWidth().height(18.dp),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (itemIndex == active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (itemIndex == active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = lyrics.plainLyrics,
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                result is LyricsLoadResult.Candidates ->
                    LyricsMessage("가사 후보가 있습니다. 돋보기를 눌러 곡을 선택해 주세요.")
                else -> LyricsMessage((result as? LyricsLoadResult.Unavailable)?.message ?: "가사를 불러오는 중입니다.")
            }
        }
    }

    if (song != null && searchOpen) {
        val initial = remember(song.fingerprintId, lyrics?.title, lyrics?.artist) {
            lyrics?.let { it.title to it.artist } ?: lyricSearchFields(song)
        }
        var title by remember(song.fingerprintId) { mutableStateOf(initial.first) }
        var artist by remember(song.fingerprintId) { mutableStateOf(initial.second) }
        var searchError by remember(song.fingerprintId) { mutableStateOf<String?>(null) }
        var searching by remember(song.fingerprintId) { mutableStateOf(false) }
        var candidates by remember(song.fingerprintId) {
            mutableStateOf((result as? LyricsLoadResult.Candidates)?.songs.orEmpty())
        }
        LaunchedEffect((result as? LyricsLoadResult.Candidates)?.songs) {
            (result as? LyricsLoadResult.Candidates)?.let { candidates = it.songs }
        }
        Dialog(onDismissRequest = { if (!searching) searchOpen = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.82f).imePadding(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = AppCardStyle.border(),
                shadowElevation = 6.dp
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                        Text("가사 찾기", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                    }
                    Text("곡 정보가 다르면 고쳐서 다시 찾아보세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = title, onValueChange = {
                        title = it; candidates = emptyList(); searchError = null
                    }, label = { Text("곡명") },
                        singleLine = true, shape = AppButtonStyle.shape, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = artist, onValueChange = {
                        artist = it; candidates = emptyList(); searchError = null
                    }, label = { Text("가수") },
                        singleLine = true, shape = AppButtonStyle.shape, modifier = Modifier.fillMaxWidth())
                    searchError?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error) }
                    if (candidates.isNotEmpty()) {
                        Text("곡명과 가수를 확인하고 가사를 선택해 주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        candidates.forEach { candidate ->
                            LyricsCandidateRow(candidate, enabled = !searching) {
                                searching = true
                                scope.launch {
                                    try {
                                        result = repository.select(song, candidate)
                                        searchOpen = false
                                    } catch (error: Exception) {
                                        if (error is CancellationException) throw error
                                        searchError = "선택한 가사를 저장하지 못했습니다. 다시 시도해 주세요."
                                    } finally {
                                        searching = false
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { searchOpen = false }, enabled = !searching) { Text("취소") }
                        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                        AppButton(onClick = {
                            searching = true
                            searchError = null
                            candidates = emptyList()
                            scope.launch {
                                try {
                                    when (val found = repository.search(song, title, artist)) {
                                        is LyricsLoadResult.Found -> {
                                            result = found
                                            searchOpen = false
                                        }
                                        is LyricsLoadResult.Candidates -> candidates = found.songs
                                        is LyricsLoadResult.Unavailable -> searchError = found.message
                                    }
                                } catch (error: Exception) {
                                    if (error is CancellationException) throw error
                                    searchError = "가사를 찾지 못했습니다. 잠시 후 다시 시도해 주세요."
                                } finally {
                                    searching = false
                                }
                            }
                        }, enabled = !searching && title.isNotBlank() && artist.isNotBlank(),
                            elevation = null) {
                            if (searching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("찾기")
                        }
                    }
                }
            }
        }
    }

    if (lyrics != null && expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("${lyrics.title} · ${lyrics.artist}") },
            text = {
                Text(
                    lyrics.plainLyrics.ifBlank { parseTimedLyrics(lyrics.syncedLyrics).joinToString("\n") { it.text } },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            },
            confirmButton = { TextButton(onClick = { expanded = false }) { Text("닫기") } }
        )
    }
}

@Composable
private fun LyricsCandidateRow(candidate: LyricsCandidate, enabled: Boolean, onClick: () -> Unit) {
    val duration = candidate.durationSeconds?.let { "%d:%02d".format(it / 60, it % 60) }
    val details = listOfNotNull(candidate.artist, candidate.album.takeIf { it.isNotBlank() }, duration)
        .joinToString(" · ")
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = AppButtonStyle.shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = AppCardStyle.border()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(candidate.title, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LyricsMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
