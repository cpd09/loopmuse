package com.example.loopmuse.ui

import android.app.NotificationManager
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.db.AlarmEntity
import com.example.loopmuse.data.db.AppDatabase
import com.example.loopmuse.service.MusicServiceConnection
import com.example.loopmuse.service.alarm.AlarmPlaybackService
import com.example.loopmuse.service.alarm.AlarmGlobalSettings
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val weekdays = listOf(2 to "월", 3 to "화", 4 to "수", 5 to "목", 6 to "금", 7 to "토", 1 to "일")
private const val DEFAULT_ALARM_SONG = "__default_alarm_song__"

@Composable
private fun AlarmWeekdayChips(days: Set<Int>, onToggle: ((Int) -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        weekdays.forEach { (day, title) ->
            if (day == 7) Spacer(Modifier.width(5.dp))
            val selected = day in days
            Box(
                modifier = Modifier.size(width = 18.dp, height = 20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .then(if (onToggle != null) Modifier.clickable { onToggle(day) } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(title, fontSize = 10.sp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SongPickerRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { stateDescription = if (selected) "선택됨" else "선택 안 됨" }
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingDialog(
    currentTrack: MusicFile?,
    connection: MusicServiceConnection,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh++
    }
    LaunchedEffect(Unit) {
        AlarmPlaybackService.ensureNotificationChannel(context)
        permissionRefresh++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRefresh++ }
    val needsExactAlarmPermission = permissionRefresh.let {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }
    val needsNotificationPermission = permissionRefresh.let {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    val needsFullScreenPermission = permissionRefresh.let {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
    }
    val needsNotificationSettings = permissionRefresh.let {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationsBlocked = !manager.areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                listOf(AlarmPlaybackService.ALARM_CHANNEL_ID, AlarmPlaybackService.SILENT_ALARM_CHANNEL_ID)
                    .any { channelId -> manager.getNotificationChannel(channelId)
                        ?.importance?.let { it < NotificationManager.IMPORTANCE_HIGH } == true })
        if (needsNotificationPermission) false else notificationsBlocked
    }
    fun canEnableAlarm(): Boolean {
        if (!needsExactAlarmPermission && !needsNotificationPermission &&
            !needsNotificationSettings && !needsFullScreenPermission) return true
        Toast.makeText(context, "알람 화면을 표시하려면 위의 알람 권한을 먼저 허용해 주세요.", Toast.LENGTH_LONG).show()
        return false
    }
    val scope = rememberCoroutineScope()
    val alarmListState = rememberLazyListState()
    val pickerListState = rememberLazyListState()
    val alarmFlow = remember(context) { AppDatabase.getDatabase(context).alarmDao().getAllAlarms() }
    val alarms by alarmFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlistSongs by connection.allSongsInQueue.collectAsStateWithLifecycle()
    var isEditing by remember { mutableStateOf(false) }
    var showSongPicker by remember { mutableStateOf(false) }
    var pickerSelection by remember { mutableStateOf<String?>(null) }
    var pickerPreviewKey by remember { mutableStateOf<String?>(null) }
    var soundMenuExpanded by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var pendingSelectedAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    val selectedAlarm = alarms.firstOrNull { it.id == selectedId }
        ?: pendingSelectedAlarm?.takeIf { it.id == selectedId }
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(0) }
    var days by remember { mutableStateOf((1..7).toSet()) }
    var label by remember { mutableStateOf("") }
    var soundMode by remember { mutableStateOf("SOUND") }
    var showTimePicker by remember { mutableStateOf(false) }
    var songPath by remember { mutableStateOf(currentTrack?.path) }
    var songTitle by remember { mutableStateOf(currentTrack?.title) }
    var songFingerprint by remember { mutableStateOf(currentTrack?.fingerprintId) }
    var startPositionMs by remember { mutableLongStateOf(0L) }
    var endPositionMs by remember { mutableLongStateOf(0L) }
    var songDurationMs by remember { mutableLongStateOf(0L) }
    var isPreviewing by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var updatingAlarmId by remember { mutableStateOf<Int?>(null) }
    var alarmToDelete by remember { mutableStateOf<AlarmEntity?>(null) }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        isPreviewing = false
    }

    fun openSongPicker() {
        stopPreview()
        pickerSelection = null
        pickerPreviewKey = null
        showSongPicker = true
    }

    fun closeSongPicker() {
        showSongPicker = false
        pickerPreviewKey = null
    }

    fun previewSound() {
        if (isPreviewing) {
            stopPreview()
            return
        }
        if (AlarmPlaybackService.activeAlarmId != 0) {
            Toast.makeText(context, "울리는 알람을 먼저 종료해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val previewPath = songPath
        val previewStart = startPositionMs
        val previewEnd = endPositionMs
        val previewVolume = AlarmGlobalSettings.read(context).volumePercent / 100f
        previewJob = scope.launch {
            isPreviewing = true
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val player = MediaPlayer()
            var savedAlarmVolume: Int? = null
            var songPrepared = false
            try {
                withContext(Dispatchers.IO) {
                    player.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    val file = previewPath?.let(::File)
                    if (file?.isFile == true && file.canRead()) {
                        try {
                            player.setDataSource(file.absolutePath)
                            player.prepare()
                            songPrepared = true
                        } catch (_: Exception) {
                            player.reset()
                            player.setAudioAttributes(AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        }
                    }
                    if (!songPrepared) {
                        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            ?: error("기본 알람음을 찾을 수 없습니다.")
                        player.setDataSource(context, uri)
                        player.prepare()
                    }
                }
                if (songPrepared && previewStart > 0L) {
                    player.seekTo(previewStart.coerceAtMost((player.duration - 1).coerceAtLeast(0).toLong()).toInt())
                }
                savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                    (maxVolume * previewVolume).roundToInt().coerceAtLeast(1), 0)
                player.start()
                val sampleLength = if (songPrepared && previewEnd > previewStart)
                    (previewEnd - previewStart).coerceAtMost(5_000L) else 5_000L
                var elapsed = 0L
                while (elapsed < sampleLength && player.isPlaying && AlarmPlaybackService.activeAlarmId == 0) {
                    delay(100L)
                    elapsed += 100L
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "알람음을 미리 들을 수 없습니다.", Toast.LENGTH_SHORT).show()
            } finally {
                player.release()
                if (AlarmPlaybackService.activeAlarmId == 0) {
                    savedAlarmVolume?.let { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
                }
                isPreviewing = false
                previewJob = null
            }
        }
    }

    fun useCurrentSong() {
        stopPreview()
        val sameSong = songPath == currentTrack?.path
        if (!sameSong) songDurationMs = 0L
        songPath = currentTrack?.path
        songTitle = currentTrack?.title
        songFingerprint = currentTrack?.fingerprintId
        startPositionMs = 0L
        endPositionMs = if (sameSong) songDurationMs else 0L
    }

    fun newAlarm() {
        isEditing = true
        selectedId = null
        pendingSelectedAlarm = null
        hour = 7
        minute = 0
        days = (1..7).toSet()
        label = ""
        soundMode = "SOUND"
        useCurrentSong()
    }

    fun loadAlarm(alarm: AlarmEntity) {
        isEditing = true
        stopPreview()
        val durationForSavedSong = if (alarm.songPath == songPath) songDurationMs else 0L
        if (alarm.songPath != songPath) songDurationMs = 0L
        selectedId = alarm.id
        pendingSelectedAlarm = alarm
        hour = alarm.hour
        minute = alarm.minute
        days = alarm.repeatDays.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.toSet()
        label = alarm.label.orEmpty()
        soundMode = when (alarm.soundMode) {
            "SOUND", "VIBRATE", "LIGHT", "PHONE" -> alarm.soundMode
            else -> if (alarm.respectPhoneSoundMode) "PHONE" else "SOUND"
        }
        songPath = alarm.songPath
        songTitle = alarm.songTitle
        songFingerprint = alarm.songFingerprintId
        startPositionMs = if (durationForSavedSong > 1000L) {
            alarm.startPositionMs.coerceIn(0L, durationForSavedSong - 1000L)
        } else alarm.startPositionMs
        endPositionMs = if (durationForSavedSong > 0L) {
            if (alarm.endPositionMs <= startPositionMs) durationForSavedSong
            else alarm.endPositionMs.coerceAtMost(durationForSavedSong)
        } else alarm.endPositionMs
    }

    fun closeEditor() {
        stopPreview()
        isEditing = false
        selectedId = null
        pendingSelectedAlarm = null
    }

    DisposableEffect(Unit) {
        onDispose { previewJob?.cancel() }
    }

    LaunchedEffect(songPath) {
        songDurationMs = 0L
        val path = songPath ?: return@LaunchedEffect
        songDurationMs = withContext(Dispatchers.IO) {
            if (!File(path).isFile) return@withContext 0L
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                0L
            } finally {
                retriever.release()
            }
        }
        if (songDurationMs > 0L) {
            startPositionMs = startPositionMs.coerceIn(0L, (songDurationMs - 1000L).coerceAtLeast(0L))
            endPositionMs = if (endPositionMs <= startPositionMs) songDurationMs
                else endPositionMs.coerceAtMost(songDurationMs)
        }
    }

    alarmToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { alarmToDelete = null },
            title = { Text("알람 삭제") },
            text = { Text("선택한 알람을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    alarmToDelete = null
                    scope.launch {
                        try {
                            connection.deleteAlarm(target)
                            if (selectedId == target.id) closeEditor()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "알람 삭제에 실패했습니다.", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { alarmToDelete = null }) { Text("취소") } }
        )
    }

    if (showTimePicker) {
        AlarmTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { selectedHour, selectedMinute ->
                hour = selectedHour
                minute = selectedMinute
                showTimePicker = false
            }
        )
    }

    LaunchedEffect(showSongPicker, pickerPreviewKey) {
        val previewKey = pickerPreviewKey ?: return@LaunchedEffect
        if (!showSongPicker || AlarmPlaybackService.activeAlarmId != 0) return@LaunchedEffect
        val player = MediaPlayer()
        try {
            withContext(Dispatchers.IO) {
                player.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                if (previewKey == DEFAULT_ALARM_SONG) {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: error("기본 알람음을 찾을 수 없습니다.")
                    player.setDataSource(context, uri)
                } else {
                    player.setDataSource(previewKey)
                }
                player.prepare()
            }
            player.setVolume(AlarmGlobalSettings.read(context).volumePercent / 100f,
                AlarmGlobalSettings.read(context).volumePercent / 100f)
            player.start()
            while (player.isPlaying && AlarmPlaybackService.activeAlarmId == 0) delay(200L)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Toast.makeText(context, "선택한 곡을 재생할 수 없습니다.", Toast.LENGTH_SHORT).show()
        } finally {
            runCatching { player.release() }
        }
    }

    if (showSongPicker) {
        AlertDialog(
            onDismissRequest = ::closeSongPicker,
            title = { Text("알람 곡 선택") },
            text = {
                Column {
                    Text("현재 재생목록", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth().height(320.dp)) {
                        val totalRows = playlistSongs.size + 1
                        val visibleRows = (maxHeight.value / 48f).toInt().coerceAtLeast(1)
                        LazyColumn(state = pickerListState,
                            modifier = Modifier.fillMaxSize().padding(end = if (totalRows > visibleRows) 8.dp else 0.dp)) {
                            item(key = DEFAULT_ALARM_SONG) {
                                SongPickerRow("기본 알람음", pickerSelection == DEFAULT_ALARM_SONG) {
                                    val next = if (pickerSelection == DEFAULT_ALARM_SONG) null else DEFAULT_ALARM_SONG
                                    pickerSelection = next
                                    pickerPreviewKey = next
                                }
                            }
                            items(playlistSongs, key = { it.id }) { song ->
                                SongPickerRow(song.title, pickerSelection == song.path) {
                                    val next = if (pickerSelection == song.path) null else song.path
                                    pickerSelection = next
                                    pickerPreviewKey = next
                                }
                            }
                        }
                        if (totalRows > visibleRows) {
                            val rowPx = with(LocalDensity.current) { 48.dp.toPx() }
                            val scrollFraction = ((pickerListState.firstVisibleItemIndex +
                                pickerListState.firstVisibleItemScrollOffset / rowPx) /
                                (totalRows - visibleRows).toFloat()).coerceIn(0f, 1f)
                            val thumbHeight = maxHeight * (visibleRows.toFloat() / totalRows)
                            Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().width(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)))
                            Box(Modifier.align(Alignment.TopEnd)
                                .offset(y = (maxHeight - thumbHeight) * scrollFraction)
                                .width(4.dp).height(thumbHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary))
                        }
                    }
                    if (playlistSongs.isEmpty()) {
                        Text("현재 재생목록에 곡이 없습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = pickerSelection ?: return@TextButton
                    val song = playlistSongs.firstOrNull { it.path == chosen }
                    if (chosen != DEFAULT_ALARM_SONG && song == null) return@TextButton
                    val sameSong = songPath == song?.path
                    songPath = song?.path
                    songTitle = song?.title
                    songFingerprint = song?.fingerprintId
                    startPositionMs = 0L
                    endPositionMs = if (sameSong) songDurationMs else 0L
                    closeSongPicker()
                }, enabled = pickerSelection != null) { Text("선택완료") }
            },
            dismissButton = { TextButton(onClick = ::closeSongPicker) { Text("취소") } }
        )
    }

    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.86f
    Dialog(onDismissRequest = { stopPreview(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = maxDialogHeight),
            contentAlignment = Alignment.Center
        ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("알람 관리", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { stopPreview(); onDismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
                if (needsExactAlarmPermission || needsNotificationPermission ||
                    needsFullScreenPermission || needsNotificationSettings) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("알람이 정시에 울리고 잠금 화면에 표시되려면 아래 권한을 허용하세요.", fontSize = 13.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically) {
                            if (needsNotificationPermission) {
                                TextButton(onClick = {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                    Text("알림 허용", fontSize = 12.sp)
                                }
                            }
                            if (needsNotificationSettings) {
                                TextButton(onClick = {
                                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    val blockedChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                        listOf(AlarmPlaybackService.ALARM_CHANNEL_ID,
                                            AlarmPlaybackService.SILENT_ALARM_CHANNEL_ID).firstOrNull { channelId ->
                                            manager.getNotificationChannel(channelId)
                                                ?.importance?.let { it < NotificationManager.IMPORTANCE_HIGH } == true
                                        } else null
                                    val settingsIntent = when {
                                        blockedChannelId != null -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            putExtra(Settings.EXTRA_CHANNEL_ID, blockedChannelId)
                                        }
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}"))
                                    }
                                    context.startActivity(settingsIntent)
                                }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                    Text("알림 설정", fontSize = 12.sp)
                                }
                            }
                            if (needsFullScreenPermission) {
                                TextButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:${context.packageName}")))
                                }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                    Text("전체 화면 허용", fontSize = 12.sp)
                                }
                            }
                        }
                        if (needsExactAlarmPermission) {
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")))
                            }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                Text("정확한 알람 허용", fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Surface(shape = AppCardStyle.shape,
                    border = AppCardStyle.border(),
                    shadowElevation = AppCardStyle.elevation,
                    modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = AppCardStyle.compactHorizontalPadding,
                    vertical = AppCardStyle.compactVerticalPadding)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("알람목록", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = ::newAlarm,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("알람추가", fontSize = 12.sp)
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(
                    if (alarms.isEmpty()) 58.dp else (alarms.size * 44).coerceAtMost(if (isEditing) 132 else 176).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)) {
                    val visibleAlarmRows = (maxHeight.value / 44f).toInt().coerceAtLeast(1)
                    if (alarms.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("알람이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            state = alarmListState,
                            modifier = Modifier.fillMaxSize().padding(end = if (alarms.size > visibleAlarmRows) 7.dp else 0.dp)
                        ) {
                            items(alarms, key = { it.id }) { alarm ->
                                val selected = selectedId == alarm.id
                                val activeDays = alarm.repeatDays.split(',').mapNotNull { it.toIntOrNull() }.toSet()
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(44.dp).padding(vertical = 1.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { loadAlarm(alarm) }
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AlarmListToggle(
                                        checked = alarm.isEnabled,
                                        enabled = updatingAlarmId != alarm.id,
                                        time = clockText(alarm.hour, alarm.minute),
                                        onCheckedChange = { enabled ->
                                            if (enabled && !canEnableAlarm()) return@AlarmListToggle
                                            updatingAlarmId = alarm.id
                                            scope.launch {
                                                try {
                                                    connection.scheduleAlarm(alarm.copy(isEnabled = enabled))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context,
                                                        e.message ?: "알람 상태를 변경하지 못했습니다.", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    updatingAlarmId = null
                                                }
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        alarm.label?.takeIf { it.isNotBlank() }?.let { title ->
                                            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                                lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(3.dp))
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(clockText(alarm.hour, alarm.minute),
                                                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                                lineHeight = 18.sp, maxLines = 1)
                                            Spacer(Modifier.width(10.dp))
                                            if (alarm.isOneTime || activeDays.isEmpty()) {
                                                Text("1회", fontSize = 10.sp, lineHeight = 18.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else AlarmWeekdayChips(activeDays)
                                        }
                                    }
                                    Spacer(Modifier.width(2.dp))
                                    Box(
                                        modifier = Modifier.width(28.dp).height(30.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { alarmToDelete = alarm },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Delete,
                                            contentDescription = "${clockText(alarm.hour, alarm.minute)} 알람 삭제",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (alarms.size > visibleAlarmRows) {
                        val rowPx = with(LocalDensity.current) { 44.dp.toPx() }
                        val scrollFraction = ((alarmListState.firstVisibleItemIndex +
                            alarmListState.firstVisibleItemScrollOffset / rowPx) /
                            (alarms.size - visibleAlarmRows).toFloat()).coerceIn(0f, 1f)
                        val thumbHeight = maxHeight * (visibleAlarmRows.toFloat() / alarms.size)
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().width(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        )
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd)
                                .offset(y = (maxHeight - thumbHeight) * scrollFraction)
                                .width(4.dp).height(thumbHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                }
                }
                if (isEditing) {
                Spacer(Modifier.height(14.dp))
                Surface(shape = AppCardStyle.shape,
                    color = MaterialTheme.colorScheme.surface,
                    border = AppCardStyle.border(),
                    shadowElevation = AppCardStyle.elevation,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(modifier = Modifier.padding(horizontal = AppCardStyle.compactHorizontalPadding,
                    vertical = AppCardStyle.compactVerticalPadding)) {
                Row(modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedAlarm == null) "새 알람 추가" else "알람 수정",
                        fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().height(36.dp)) {
                        BasicTextField(value = label, onValueChange = { label = it.take(60) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (label.isEmpty()) Text("알람제목을 입력하세요",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    innerTextField()
                                }
                            })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppOutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                        ) { Text(clockText(hour, minute), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        AppOutlinedButton(
                            onClick = { days = if (days.size == 7) emptySet() else (1..7).toSet() },
                            modifier = Modifier.size(width = 36.dp, height = 34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Icon(Icons.Default.SelectAll, contentDescription = "요일 전체 선택 또는 해제",
                            modifier = Modifier.size(18.dp)) }
                        AlarmWeekdayChips(days) { day ->
                            days = if (day in days) days - day else days + day
                        }
                    }
                    if (days.isEmpty()) {
                        Text("요일 미선택: 한 번만 울림", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = AppCardStyle.shape,
                        color = MaterialTheme.colorScheme.surface,
                        border = AppCardStyle.border(),
                        shadowElevation = AppCardStyle.elevation,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = AppCardStyle.compactHorizontalPadding,
                            vertical = AppCardStyle.compactVerticalPadding)) {
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("곡", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(29.dp))
                        Text(songTitle ?: "기본 알람음", fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = ::openSongPicker, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "알람 곡 변경", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (songPath != null && songDurationMs == 0L) {
                        Text("곡 파일을 찾을 수 없어 기본 알람음으로 재생됩니다.",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (songDurationMs > 0L) "시작 ${timeText(startPositionMs)}  끝 ${timeText(endPositionMs)}" else "전체 곡",
                            fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (songDurationMs > 1000L) {
                        RangeSlider(
                            value = startPositionMs.toFloat()..endPositionMs.coerceAtLeast(startPositionMs + 1000L).toFloat(),
                            onValueChange = { range ->
                                stopPreview()
                                val start = ((range.start / 1000f).roundToInt().toLong() * 1000L)
                                    .coerceIn(0L, songDurationMs - 1000L)
                                val end = if (range.endInclusive >= songDurationMs - 500f) songDurationMs
                                    else ((range.endInclusive / 1000f).roundToInt().toLong() * 1000L)
                                        .coerceIn(start + 1000L, songDurationMs)
                                startPositionMs = start
                                endPositionMs = end
                            },
                            valueRange = 0f..songDurationMs.toFloat(),
                            modifier = Modifier.weight(1f).height(24.dp),
                            track = { sliderState ->
                                SliderDefaults.Track(sliderState, modifier = Modifier.height(4.dp))
                            }
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    IconButton(onClick = ::previewSound, modifier = Modifier.size(32.dp)) {
                        Icon(if (isPreviewing) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = if (isPreviewing) "미리듣기 중지" else "5초 미리듣기",
                            modifier = Modifier.size(20.dp))
                    }
                    }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)) {
                        val comboWidth = minOf(180.dp, maxWidth * 0.6f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("알람벨소리설정", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.width(comboWidth)) {
                                val choices = listOf(
                                    "SOUND" to "소리",
                                    "VIBRATE" to "진동",
                                    "LIGHT" to "무음(화면)",
                                    "PHONE" to "휴대폰 모드"
                                )
                                AppOutlinedButton(onClick = { soundMenuExpanded = true },
                                    modifier = Modifier.fillMaxWidth().height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                    Text(choices.firstOrNull { it.first == soundMode }?.second ?: choices.first().second,
                                        fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(expanded = soundMenuExpanded,
                                    onDismissRequest = { soundMenuExpanded = false }) {
                                    choices.forEach { (mode, title) ->
                                        DropdownMenuItem(text = { Text(title) }, onClick = {
                                            soundMode = mode
                                            soundMenuExpanded = false
                                        })
                                    }
                                }
                            }
                        }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val footerButtonWidth = minOf(112.dp,
                    (maxWidth - if (selectedAlarm != null) 20.dp else 10.dp) /
                        (if (selectedAlarm != null) 3 else 2))
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    AppOutlinedButton(onClick = ::closeEditor,
                        modifier = Modifier.width(footerButtonWidth).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)) {
                        Text("취소", fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    if (selectedAlarm != null) AppOutlinedButton(
                        onClick = {
                            val source = selectedAlarm ?: return@AppOutlinedButton
                            if (!canEnableAlarm()) return@AppOutlinedButton
                            stopPreview()
                            isSaving = true
                            scope.launch {
                                try {
                                    val newId = connection.scheduleAlarm(source.copy(id = 0, isEnabled = true))
                                    loadAlarm(source.copy(id = newId, isEnabled = true))
                                    val copiedIndex = snapshotFlow { alarms.indexOfFirst { it.id == newId } }
                                        .first { it >= 0 }
                                    alarmListState.scrollToItem(copiedIndex)
                                    Toast.makeText(context, "알람을 복사해 켰습니다. 시간을 수정할 수 있습니다.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "알람 복사에 실패했습니다.", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.width(footerButtonWidth).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
                    ) { Text("복사", fontSize = 12.sp) }
                    if (selectedAlarm != null) Spacer(Modifier.width(10.dp))
                    AppButton(onClick = {
                        val enabledAfterSave = selectedAlarm?.isEnabled ?: true
                        if (enabledAfterSave && !canEnableAlarm()) return@AppButton
                        if (songDurationMs > 0L && (endPositionMs <= startPositionMs || endPositionMs > songDurationMs)) {
                            Toast.makeText(context, "곡 구간을 다시 선택해 주세요.", Toast.LENGTH_SHORT).show()
                            return@AppButton
                        }
                        val wholeSong = songDurationMs > 0L && startPositionMs == 0L && endPositionMs == songDurationMs
                        val global = AlarmGlobalSettings.read(context)
                        val alarm = (selectedAlarm ?: AlarmEntity(hour = hour, minute = minute)).copy(
                            hour = hour, minute = minute, isEnabled = enabledAfterSave,
                            label = label.trim(), soundMode = soundMode,
                            repeatDays = days.sorted().joinToString(","), isOneTime = days.isEmpty(),
                            songFingerprintId = songFingerprint, songTitle = songTitle, songPath = songPath,
                            startPositionMs = if (wholeSong) 0L else startPositionMs,
                            endPositionMs = if (wholeSong) 0L else endPositionMs,
                            targetVolume = global.volumePercent / 100f, useFadeIn = global.fadeEnabled,
                            respectPhoneSoundMode = soundMode == "PHONE"
                        )
                        stopPreview()
                        isSaving = true
                        scope.launch {
                            try {
                                connection.scheduleAlarm(alarm)
                                closeEditor()
                                Toast.makeText(context, "알람을 저장했습니다.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "알람 저장에 실패했습니다.", Toast.LENGTH_LONG).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    }, enabled = !isSaving, modifier = Modifier.width(footerButtonWidth).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 0.dp)) {
                        Text("저장", fontSize = 12.sp)
                    }
                }
                }
                }
                }
                }
            }
        }
        }
    }
}

private fun clockText(hour: Int, minute: Int) = String.format(Locale.KOREA, "%02d:%02d", hour, minute)

private fun timeText(millis: Long): String {
    val seconds = millis / 1000L
    return String.format(Locale.KOREA, "%02d:%02d", seconds / 60L, seconds % 60L)
}

@Composable
private fun AlarmListToggle(
    checked: Boolean,
    enabled: Boolean,
    time: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier.width(43.dp).height(32.dp)
            .semantics {
                contentDescription = "$time 알람"
                stateDescription = if (checked) "켜짐" else "꺼짐"
            }
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.width(37.dp).height(22.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline)
        )
        Box(
            modifier = Modifier.offset(x = if (checked) 7.dp else (-7).dp)
                .size(18.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onPrimary)
        )
    }
}
