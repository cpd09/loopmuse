package com.example.loopmuse.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.loopmuse.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LoopMuse::AlarmWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)

        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        val isSnooze = intent.action == AlarmScheduler.ACTION_SNOOZE_TRIGGER
        val scheduledAt = intent.getLongExtra("SCHEDULED_AT", 0L)
        Log.i("LoopMuse", "Alarm received: id=$alarmId snooze=$isSnooze delayMs=" +
            if (scheduledAt > 0L) (System.currentTimeMillis() - scheduledAt).toString() else "unknown")
        if (alarmId <= 0) {
            wakeLock.release()
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val alarm = dao.getAlarmById(alarmId) ?: return@launch
                if (!alarm.isEnabled && !(isSnooze && alarm.isOneTime)) return@launch
                // Read the saved row so an edit or deletion is respected even if a broadcast was queued.
                val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
                    putExtra("ALARM_ID", alarm.id)
                    putExtra("ALARM_HOUR", alarm.hour)
                    putExtra("ALARM_MINUTE", alarm.minute)
                    putExtra("IS_SNOOZE", isSnooze)
                    putExtra("SONG_TITLE", alarm.songTitle)
                    putExtra("SONG_PATH", alarm.songPath)
                    putExtra("START_POSITION", alarm.startPositionMs)
                    putExtra("END_POSITION", alarm.endPositionMs)
                    putExtra("TARGET_VOLUME", alarm.targetVolume)
                    putExtra("USE_FADE_IN", alarm.useFadeIn)
                    putExtra("RESPECT_PHONE_SOUND_MODE", alarm.respectPhoneSoundMode)
                    putExtra("SOUND_MODE", alarm.soundMode)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                if (!isSnooze) {
                    if (alarm.isOneTime) dao.updateAlarmEnabled(alarmId, false)
                    else AlarmScheduler(context).scheduleAlarm(alarm)
                }
            } catch (e: Exception) {
                Log.w("LoopMuse", "Cannot start or update fired alarm $alarmId", e)
            } finally {
                wakeLock.release()
                pending.finish()
            }
        }
    }
}
