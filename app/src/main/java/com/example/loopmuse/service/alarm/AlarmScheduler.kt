package com.example.loopmuse.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.loopmuse.data.db.AlarmEntity
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(alarm: AlarmEntity) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("SONG_FINGERPRINT", alarm.songFingerprintId)
            putExtra("SONG_PATH", alarm.songPath)
            putExtra("START_POSITION", alarm.startPositionMs)
            putExtra("TARGET_VOLUME", alarm.targetVolume)
            putExtra("USE_FADE_IN", alarm.useFadeIn)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val repeatDays = alarm.repeatDays.split(',').mapNotNull { it.trim().toIntOrNull() }
            .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }.toSet()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            if (!alarm.isOneTime && repeatDays.isNotEmpty()) {
                while (get(Calendar.DAY_OF_WEEK) !in repeatDays) add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Exact alarm that can wake up the device
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    @Suppress("unused")
    fun cancelAlarm(alarmId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
