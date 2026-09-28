package com.example.loopmuse.service.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import android.util.Log
import com.example.loopmuse.MainActivity
import com.example.loopmuse.ui.AlarmRingingActivity
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var alarmVibrator: Vibrator? = null
    private var segmentJob: Job? = null
    private var fadeJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var audioManager: AudioManager
    private var originalVolume: Int = 0
    private var volumeWasChanged = false
    private var currentAlarmId = 0
    private var isSnoozeRing = false

    companion object {
        const val ALARM_CHANNEL_ID = "alarm_channel"
        const val SILENT_ALARM_CHANNEL_ID = "alarm_silent_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"
        const val ACTION_DISMISS_SNOOZE = "ACTION_DISMISS_SNOOZE"
        const val ACTION_ALARM_FINISHED = "com.example.loopmuse.action.ALARM_FINISHED"
        const val ACTION_ALARM_SILENCED = "com.example.loopmuse.action.ALARM_SILENCED"
        const val ACTION_SNOOZE_FAILED = "com.example.loopmuse.action.SNOOZE_FAILED"
        @Volatile var activeAlarmId = 0
            private set

        private data class RingingAlarm(
            val id: Int,
            val hour: Int,
            val minute: Int,
            val songTitle: String?,
            val isSnooze: Boolean,
            val isVisualAlarm: Boolean
        )

        @Volatile private var ringingAlarm: RingingAlarm? = null

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    ALARM_CHANNEL_ID,
                    "Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }
                context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
                val silentChannel = NotificationChannel(
                    SILENT_ALARM_CHANNEL_ID,
                    "Silent alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                }
                context.getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(silentChannel)
            }
        }

        fun activeScreenIntent(context: Context): Intent? = ringingAlarm?.let { alarm ->
            Intent(context, AlarmRingingActivity::class.java).apply {
                putExtra("ALARM_ID", alarm.id)
                putExtra("ALARM_HOUR", alarm.hour)
                putExtra("ALARM_MINUTE", alarm.minute)
                putExtra("SONG_TITLE", alarm.songTitle)
                putExtra("IS_SNOOZE", alarm.isSnooze)
                putExtra("IS_VISUAL_ALARM", alarm.isVisualAlarm)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_DISMISS_SNOOZE) {
            val requestedId = intent.getIntExtra("ALARM_ID", 0)
            if (requestedId > 0) {
                AlarmScheduler(this).cancelSnooze(requestedId)
                if (currentAlarmId == requestedId) endAlarm(ACTION_ALARM_FINISHED)
                else {
                    sendBroadcast(Intent(ACTION_ALARM_FINISHED).setPackage(packageName)
                        .putExtra("ALARM_ID", requestedId))
                    if (currentAlarmId == 0) stopSelf(startId)
                }
            } else if (currentAlarmId == 0) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_STOP_ALARM) {
            val requestedId = intent.getIntExtra("ALARM_ID", 0)
            if (requestedId != currentAlarmId || requestedId == 0) {
                if (currentAlarmId == 0) stopSelf(startId)
                return START_NOT_STICKY
            }
            if (AlarmGlobalSettings.read(this).snoozeEnabled) {
                try {
                    AlarmScheduler(this).scheduleSnooze(requestedId)
                } catch (e: Exception) {
                    Log.w("LoopMuse", "Cannot snooze alarm $requestedId", e)
                    sendBroadcast(Intent(ACTION_SNOOZE_FAILED).setPackage(packageName).putExtra("ALARM_ID", requestedId))
                    return START_NOT_STICKY
                }
                endAlarm(ACTION_ALARM_SILENCED)
            } else {
                endAlarm(ACTION_ALARM_FINISHED)
            }
            return START_NOT_STICKY
        }

        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        if (alarmId <= 0) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (currentAlarmId != 0) {
            sendBroadcast(Intent(ACTION_ALARM_FINISHED).setPackage(packageName).putExtra("ALARM_ID", currentAlarmId))
        }
        releasePlayback()
        currentAlarmId = alarmId
        activeAlarmId = alarmId
        isSnoozeRing = intent.getBooleanExtra("IS_SNOOZE", false)
        val soundMode = intent.getStringExtra("SOUND_MODE") ?: "LEGACY"
        ringingAlarm = RingingAlarm(
            alarmId,
            intent.getIntExtra("ALARM_HOUR", 0),
            intent.getIntExtra("ALARM_MINUTE", 0),
            intent.getStringExtra("SONG_TITLE"),
            isSnoozeRing,
            soundMode == "LIGHT"
        )
        val songPath = intent.getStringExtra("SONG_PATH")
        val startPositionMs = intent.getLongExtra("START_POSITION", 0L)
        val endPositionMs = intent.getLongExtra("END_POSITION", 0L)
        val respectPhoneSoundMode = intent.getBooleanExtra("RESPECT_PHONE_SOUND_MODE", false)
        val notification = createNotification(
            alarmId,
            intent.getIntExtra("ALARM_HOUR", 0),
            intent.getIntExtra("ALARM_MINUTE", 0),
            intent.getStringExtra("SONG_TITLE"),
            if (soundMode == "LEGACY" && respectPhoneSoundMode) "PHONE" else soundMode
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (MainActivity.isResumed || AlarmRingingActivity.isResumed) {
            try {
                startActivity(alarmScreenIntent(
                    alarmId,
                    intent.getIntExtra("ALARM_HOUR", 0),
                    intent.getIntExtra("ALARM_MINUTE", 0),
                    intent.getStringExtra("SONG_TITLE"),
                    isSnoozeRing,
                    soundMode == "LIGHT"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            } catch (e: Exception) {
                Log.w("LoopMuse", "Cannot show alarm screen while app is open", e)
            }
        }

        serviceScope.launch {
            playAlarm(songPath, startPositionMs, endPositionMs, respectPhoneSoundMode, soundMode)
        }

        return START_NOT_STICKY
    }

    private fun playAlarm(songPath: String?, startPositionMs: Long, endPositionMs: Long,
                          respectPhoneSoundMode: Boolean, soundMode: String) {
        val global = AlarmGlobalSettings.read(this)
        when (soundMode) {
            "LIGHT" -> return
            "VIBRATE" -> { startAlarmVibration(global.vibrationPercent); return }
        }
        if (soundMode == "PHONE" || (soundMode == "LEGACY" && respectPhoneSoundMode)) {
            when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> return
                AudioManager.RINGER_MODE_VIBRATE -> {
                    startAlarmVibration(global.vibrationPercent)
                    return
                }
            }
        }
        // Alarm audio uses the separate alarm stream in every phone sound mode.
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        volumeWasChanged = true
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val finalVolumeIdx = (maxVol * global.volumePercent / 100f).roundToInt().coerceAtLeast(1)
        val firstVolumeIdx = (maxVol * global.fadeStartPercent / 100f)
            .roundToInt().coerceIn(1, finalVolumeIdx)
        
        if (global.fadeEnabled) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, firstVolumeIdx, 0)
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, finalVolumeIdx, 0)
        }

        // mediaPlayer setup using STREAM_ALARM to bypass silent mode
        segmentJob?.cancel()
        fadeJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            isLooping = true
        }

        var isPrepared = false
        var isSongPrepared = false
        if (!songPath.isNullOrBlank()) {
            val file = java.io.File(songPath)
            if (file.exists() && file.canRead()) {
                try {
                    player.setDataSource(file.absolutePath)
                    player.prepare()
                    isPrepared = true
                    isSongPrepared = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Fallback to system default alarm if song file is not available
        if (!isPrepared) {
            try {
                player.reset()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                player.isLooping = true
                val alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                player.setDataSource(applicationContext, alarmUri)
                player.prepare()
                isPrepared = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (isPrepared) {
            val durationMs = player.duration.toLong().coerceAtLeast(0L)
            val segmentEnabled = isSongPrepared && endPositionMs > startPositionMs &&
                endPositionMs <= durationMs && startPositionMs >= 0L
            val segmentStart = if (segmentEnabled) startPositionMs.toInt() else 0
            if (segmentEnabled) {
                player.isLooping = false
                player.setOnCompletionListener { finished ->
                    finished.seekTo(segmentStart)
                    finished.start()
                }
                if (segmentStart > 0) player.seekTo(segmentStart)
            } else if (isSongPrepared && endPositionMs == 0L && startPositionMs > 0L) {
                // Alarms saved before range selection existed used a start-only position.
                player.seekTo(startPositionMs.coerceAtMost((durationMs - 1L).coerceAtLeast(0L)).toInt())
            }
            player.start()
            mediaPlayer = player
            if (segmentEnabled) {
                segmentJob = serviceScope.launch {
                    while (true) {
                        delay(100L)
                        if (mediaPlayer !== player) break
                        try {
                            if (player.isPlaying && player.currentPosition >= endPositionMs) {
                                player.seekTo(segmentStart)
                            }
                        } catch (_: IllegalStateException) {
                            break
                        }
                    }
                }
            }
        } else {
            player.release()
        }

        // Fade-in effect
        if (global.fadeEnabled && isPrepared) {
            fadeJob = serviceScope.launch {
                val durationMs = global.fadeDurationSeconds * 1000L
                val startedAt = android.os.SystemClock.elapsedRealtime()
                while (true) {
                    val progress = ((android.os.SystemClock.elapsedRealtime() - startedAt).toFloat() / durationMs)
                        .coerceIn(0f, 1f)
                    val level = (firstVolumeIdx + (finalVolumeIdx - firstVolumeIdx) * progress)
                        .roundToInt().coerceIn(firstVolumeIdx, finalVolumeIdx)
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, level, 0)
                    if (progress >= 1f) break
                    delay(250L)
                }
            }
        }
    }

    private fun releasePlayback() {
        alarmVibrator?.cancel()
        alarmVibrator = null
        segmentJob?.cancel()
        segmentJob = null
        fadeJob?.cancel()
        fadeJob = null
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        // Restore user's original volume
        if (volumeWasChanged) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            volumeWasChanged = false
        }
    }

    private fun startAlarmVibration(percent: Int) {
        @Suppress("DEPRECATION")
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        alarmVibrator = vibrator
        val pattern = longArrayOf(0L, 600L, 400L)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                if (vibrator.hasAmplitudeControl()) {
                    val amplitude = (percent.coerceIn(10, 100) * 255 / 100f).roundToInt()
                    VibrationEffect.createWaveform(pattern, intArrayOf(0, amplitude, 0), 0)
                } else VibrationEffect.createWaveform(pattern, 0),
                attributes
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0, attributes)
        }
    }

    private fun endAlarm(finishAction: String) {
        val finishedId = currentAlarmId
        releasePlayback()
        currentAlarmId = 0
        activeAlarmId = 0
        ringingAlarm = null
        isSnoozeRing = false
        sendBroadcast(Intent(finishAction).setPackage(packageName).putExtra("ALARM_ID", finishedId))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (currentAlarmId != 0) {
            val finishedId = currentAlarmId
            releasePlayback()
            currentAlarmId = 0
            activeAlarmId = 0
            ringingAlarm = null
            isSnoozeRing = false
            sendBroadcast(Intent(ACTION_ALARM_FINISHED).setPackage(packageName).putExtra("ALARM_ID", finishedId))
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(alarmId: Int, hour: Int, minute: Int, songTitle: String?,
                                   soundMode: String): Notification {
        val stopIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = ACTION_STOP_ALARM
            putExtra("ALARM_ID", alarmId)
        }
        val stopPendingIntent = PendingIntent.getService(this, alarmId, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val fullScreenIntent = alarmScreenIntent(alarmId, hour, minute, songTitle, isSnoozeRing,
            soundMode == "LIGHT")
        val activityOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                pendingIntentCreatorBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }.toBundle()
        } else null
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, alarmId, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            activityOptions
        )

        val timeText = String.format(Locale.KOREA, "%02d:%02d", hour, minute)

        val channelId = if (soundMode == "SOUND" || soundMode == "LEGACY") ALARM_CHANNEL_ID
            else SILENT_ALARM_CHANNEL_ID
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("LoopMuse 알람")
            .setContentText("$timeText · ${songTitle ?: "알람음"}")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "알람 멈춤", stopPendingIntent)
            .build()
    }

    private fun alarmScreenIntent(alarmId: Int, hour: Int, minute: Int, songTitle: String?,
                                  isSnooze: Boolean, isVisualAlarm: Boolean) =
        Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_HOUR", hour)
            putExtra("ALARM_MINUTE", minute)
            putExtra("SONG_TITLE", songTitle)
            putExtra("IS_SNOOZE", isSnooze)
            putExtra("IS_VISUAL_ALARM", isVisualAlarm)
        }
}
