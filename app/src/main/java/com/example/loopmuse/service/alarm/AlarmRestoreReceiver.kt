package com.example.loopmuse.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.os.Build
import android.util.Log
import com.example.loopmuse.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager registrations do not survive a reboot or every package replacement. */
class AlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = AlarmScheduler(context)
                AppDatabase.getDatabase(context).alarmDao().getAllAlarmsOnce()
                    .filter { it.isEnabled }
                    .forEach { alarm ->
                        try { scheduler.scheduleAlarm(alarm) }
                        catch (e: Exception) { Log.w("LoopMuse", "Cannot restore alarm ${alarm.id}", e) }
                    }
            } catch (e: Exception) {
                Log.w("LoopMuse", "Cannot reload saved alarms", e)
            } finally {
                pending.finish()
            }
        }
    }
}
