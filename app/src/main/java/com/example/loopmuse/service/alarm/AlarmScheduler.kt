package com.example.loopmuse.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.loopmuse.MainActivity
import com.example.loopmuse.data.db.AlarmEntity
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ACTION_SNOOZE_TRIGGER = "com.example.loopmuse.action.SNOOZE_TRIGGER"
    }

    fun scheduleAlarm(alarm: AlarmEntity) {
        requireExactAlarmAccess()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("SONG_FINGERPRINT", alarm.songFingerprintId)
            putExtra("SONG_PATH", alarm.songPath)
            putExtra("START_POSITION", alarm.startPositionMs)
            putExtra("END_POSITION", alarm.endPositionMs)
            putExtra("TARGET_VOLUME", alarm.targetVolume)
            putExtra("USE_FADE_IN", alarm.useFadeIn)
        }

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
        intent.putExtra("SCHEDULED_AT", calendar.timeInMillis)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // User-facing wake-up alarms must be delivered even during Doze.
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(calendar.timeInMillis, showAlarmPendingIntent(alarm.id)),
            pendingIntent
        )
    }

    fun cancelAlarm(alarmId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        cancelSnooze(alarmId)
    }

    fun scheduleSnooze(alarmId: Int) {
        requireExactAlarmAccess()
        val delayMs = AlarmGlobalSettings.read(context).snoozeMinutes * 60_000L
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis() + delayMs,
                showAlarmPendingIntent(alarmId)),
            snoozePendingIntent(alarmId, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!
        )
    }

    fun cancelSnooze(alarmId: Int) {
        snoozePendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    fun hasSnooze(alarmId: Int): Boolean =
        snoozePendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null

    private fun requireExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            throw SecurityException("정확한 알람 권한을 허용해 주세요.")
        }
    }

    private fun showAlarmPendingIntent(alarmId: Int): PendingIntent = PendingIntent.getActivity(
        context,
        alarmId,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun snoozePendingIntent(alarmId: Int, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        alarmId,
        Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_TRIGGER
            putExtra("ALARM_ID", alarmId)
        },
        flags or PendingIntent.FLAG_ONE_SHOT
    )
}
