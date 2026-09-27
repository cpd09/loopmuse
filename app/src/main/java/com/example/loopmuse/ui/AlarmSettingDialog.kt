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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
                manager.getNotificationChannel(AlarmPlaybackService.ALARM_CHANNEL_ID)
                    ?.importance?.let { it < NotificationManager.IMPORTANCE_HIGH } == true)
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
    val alarmFlow = remember(context) { AppDatabase.getDatabase(context).alarmDao().getAllAlarms() }
    val alarms by alarmFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var pendingSelectedAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    val selectedAlarm = alarms.firstOrNull { it.id == selectedId }
        ?: pendingSelectedAlarm?.takeIf { it.id == selectedId }
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(0) }
    var days by remember { mutableStateOf((1..7).toSet()) }
    var targetVolume by remember { mutableFloatStateOf(0.7f) }
    var useFadeIn by remember { mutableStateOf(true) }
    var respectPhoneSoundMode by remember { mutableStateOf(false) }
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
        val previewVolume = targetVolume
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
        selectedId = null
        pendingSelectedAlarm = null
        hour = 7
        minute = 0
        days = (1..7).toSet()
        targetVolume = 0.7f
        useFadeIn = true
        respectPhoneSoundMode = false
        useCurrentSong()
    }

    fun loadAlarm(alarm: AlarmEntity) {
        stopPreview()
        val durationForSavedSong = if (alarm.songPath == songPath) songDurationMs else 0L
        if (alarm.songPath != songPath) songDurationMs = 0L
        selectedId = alarm.id
        pendingSelectedAlarm = alarm
        hour = alarm.hour
        minute = alarm.minute
        days = alarm.repeatDays.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.toSet()
        targetVolume = alarm.targetVolume.coerceIn(0.1f, 1f)
        useFadeIn = alarm.useFadeIn
        respectPhoneSoundMode = alarm.respectPhoneSoundMode
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
                            if (selectedId == target.id) newAlarm()
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

    Dialog(onDismissRequest = { stopPreview(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            contentAlignment = Alignment.Center
        ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
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
                                    val alarmChannelBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                        manager.getNotificationChannel(AlarmPlaybackService.ALARM_CHANNEL_ID)
                                            ?.importance?.let { it < NotificationManager.IMPORTANCE_HIGH } == true
                                    val settingsIntent = when {
                                        alarmChannelBlocked -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            putExtra(Settings.EXTRA_CHANNEL_ID, AlarmPlaybackService.ALARM_CHANNEL_ID)
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("저장된 알람", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = ::newAlarm,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("+ 새 알람", fontSize = 12.sp)
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(
                    if (alarms.isEmpty()) 70.dp else (alarms.size * 34).coerceAtMost(102).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)) {
                    val visibleAlarmRows = (maxHeight.value / 34f).toInt().coerceAtLeast(1)
                    if (alarms.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("저장된 알람이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    modifier = Modifier.fillMaxWidth().height(34.dp).padding(vertical = 1.dp)
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
                                    Spacer(Modifier.width(2.dp))
                                    Column(modifier = Modifier.width(56.dp)) {
                                        Text(clockText(alarm.hour, alarm.minute),
                                            fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                                        Text(if (alarm.isOneTime || activeDays.isEmpty()) "1회"
                                            else if (activeDays.size == 7) "매일" else "반복",
                                            fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.width(2.dp))
                                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        weekdays.forEach { (dayNumber, label) ->
                                            val daySelected = !alarm.isOneTime && dayNumber in activeDays
                                            Box(
                                                modifier = Modifier.weight(1f).height(23.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (daySelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(label, fontSize = 10.sp,
                                                    color = if (daySelected) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
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
                        val rowPx = with(LocalDensity.current) { 34.dp.toPx() }
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
                HorizontalDivider(modifier = Modifier.padding(top = 9.dp, bottom = 6.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selectedAlarm == null) "새 알람 설정" else "${clockText(hour, minute)} 알람 수정",
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f))
                    Text("저장하면 켜짐", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Surface(shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                    Text("시간과 반복", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                        ) { Text(clockText(hour, minute), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { days = if (days.size == 7) emptySet() else (1..7).toSet() },
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) { Text("매일", fontSize = 11.sp) }
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            weekdays.forEach { (dayNumber, label) ->
                                val selected = dayNumber in days
                                Box(
                                    modifier = Modifier.weight(1f).height(30.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { days = if (selected) days - dayNumber else days + dayNumber },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 11.sp,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (days.isEmpty()) {
                        Text("요일 미선택: 한 번만 울림", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                    Text("재생 곡과 구간", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("곡", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(29.dp))
                        Text(songTitle ?: "기본 알람음", fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (currentTrack != null && currentTrack.path != songPath) {
                            TextButton(onClick = ::useCurrentSong, contentPadding = PaddingValues(horizontal = 5.dp)) {
                                Text("현재 곡", fontSize = 11.sp)
                            }
                        }
                    }
                    if (songPath != null && songDurationMs == 0L) {
                        Text("곡 파일을 찾을 수 없어 기본 알람음으로 재생됩니다.",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 43.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("구간", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(29.dp))
                        Text(if (songDurationMs > 0L) "시작 ${timeText(startPositionMs)}  끝 ${timeText(endPositionMs)}" else "전체 곡",
                            fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    }
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("알람벨 소리 설정", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text("이 알람에만 적용", fontSize = 11.sp,
                                    lineHeight = 14.sp, textAlign = TextAlign.End,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Column(modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.surface)) {
                                AlarmSoundModeOption(
                                    selected = !respectPhoneSoundMode,
                                    title = "무음·진동이어도 소리",
                                    onClick = { respectPhoneSoundMode = false }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                AlarmSoundModeOption(
                                    selected = respectPhoneSoundMode,
                                    title = "휴대폰 소리 모드 따르기",
                                    onClick = { respectPhoneSoundMode = true }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("페이드인", fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Switch(checked = useFadeIn, onCheckedChange = { useFadeIn = it })
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("알람 음량", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f))
                                Text("${(targetVolume * 100).roundToInt()}%", fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = ::previewSound,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                                    Text(if (isPreviewing) "멈추기" else "5초 미리 듣기", fontSize = 12.sp)
                                }
                            }
                            Slider(value = targetVolume.coerceIn(0.1f, 1f),
                                onValueChange = { targetVolume = it; stopPreview() },
                                valueRange = 0.1f..1f, steps = 8,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("방해금지에서는 휴대폰의 '알람 허용' 설정이 적용됩니다.",
                                fontSize = 11.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                    Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS else Settings.ACTION_SOUND_SETTINGS
                                runCatching { context.startActivity(Intent(action)) }
                                    .onFailure { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                            }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("설정", fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            val source = selectedAlarm ?: return@OutlinedButton
                            if (!canEnableAlarm()) return@OutlinedButton
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
                        enabled = selectedAlarm != null && !isSaving,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
                    ) { Text("복사", fontSize = 12.sp) }
                    Spacer(Modifier.width(5.dp))
                    Button(onClick = {
                        if (!canEnableAlarm()) return@Button
                        if (songDurationMs > 0L && (endPositionMs <= startPositionMs || endPositionMs > songDurationMs)) {
                            Toast.makeText(context, "곡 구간을 다시 선택해 주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val wholeSong = songDurationMs > 0L && startPositionMs == 0L && endPositionMs == songDurationMs
                        val alarm = (selectedAlarm ?: AlarmEntity(hour = hour, minute = minute)).copy(
                            hour = hour, minute = minute, isEnabled = true,
                            repeatDays = days.sorted().joinToString(","), isOneTime = days.isEmpty(),
                            songFingerprintId = songFingerprint, songTitle = songTitle, songPath = songPath,
                            startPositionMs = if (wholeSong) 0L else startPositionMs,
                            endPositionMs = if (wholeSong) 0L else endPositionMs,
                            targetVolume = targetVolume, useFadeIn = useFadeIn,
                            respectPhoneSoundMode = respectPhoneSoundMode
                        )
                        stopPreview()
                        isSaving = true
                        scope.launch {
                            try {
                                connection.scheduleAlarm(alarm)
                                if (selectedAlarm == null) newAlarm()
                                Toast.makeText(context, "알람을 저장했습니다.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "알람 저장에 실패했습니다.", Toast.LENGTH_LONG).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    }, enabled = !isSaving, modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 0.dp)) {
                        Text("저장하고 켜기", fontSize = 12.sp)
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
private fun AlarmSoundModeOption(
    selected: Boolean,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
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
