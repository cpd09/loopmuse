package com.example.loopmuse.service.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.loopmuse.MainActivity
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var audioManager: AudioManager
    private var originalVolume: Int = 0

    companion object {
        const val ALARM_CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val fingerprintId = intent?.getStringExtra("SONG_FINGERPRINT")
        val songPath = intent?.getStringExtra("SONG_PATH")
        val startPositionMs = intent?.getLongExtra("START_POSITION", 0L) ?: 0L
        val targetVolume = intent?.getFloatExtra("TARGET_VOLUME", 0.5f) ?: 0.5f
        val useFadeIn = intent?.getBooleanExtra("USE_FADE_IN", true) ?: true

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            playAlarm(songPath, startPositionMs, targetVolume, useFadeIn)
        }

        return START_STICKY
    }

    private fun playAlarm(songPath: String?, startPositionMs: Long, targetVolume: Float, useFadeIn: Boolean) {
        // --- Volume Override Logic (Ignore Silent/Vibrate) ---
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val finalVolumeIdx = (maxVol * targetVolume).toInt().coerceAtLeast(1)
        
        if (useFadeIn) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, finalVolumeIdx, 0)
        }

        // mediaPlayer setup using STREAM_ALARM to bypass silent mode
        mediaPlayer?.release()
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
        if (!songPath.isNullOrBlank()) {
            val file = java.io.File(songPath)
            if (file.exists() && file.canRead()) {
                try {
                    player.setDataSource(file.absolutePath)
                    player.prepare()
                    if (startPositionMs > 0L) {
                        player.seekTo(startPositionMs.toInt())
                    }
                    isPrepared = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Fallback to system default alarm if song file is not available
        if (!isPrepared) {
            try {
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
            player.start()
            mediaPlayer = player
        } else {
            player.release()
        }

        // Fade-in effect
        if (useFadeIn && isPrepared) {
            serviceScope.launch {
                for (v in 1..finalVolumeIdx) {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, v, 0)
                    delay(3.seconds) // Increase every 3 seconds for a gentle wake up
                }
            }
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        // Restore user's original volume
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Alarms",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val fullScreenIntent = Intent(this, MainActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("LoopMuse 알람")
            .setContentText("알람이 울리고 있습니다.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "알람 끄기", stopPendingIntent)
            .build()
    }
}