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

        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            putExtras(intent)
        }
        
        // Start Foreground Service for reliable playback
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } finally {
            wakeLock.release()
        }

        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        if (alarmId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                dao.getAlarmById(alarmId)?.let { alarm ->
                    if (alarm.isOneTime) dao.updateAlarmEnabled(alarmId, false)
                    else if (alarm.isEnabled) AlarmScheduler(context).scheduleAlarm(alarm)
                }
            } catch (e: Exception) {
                Log.w("LoopMuse", "Cannot update fired alarm $alarmId", e)
            } finally {
                pending.finish()
            }
        }
    }
}
