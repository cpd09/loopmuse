package com.example.loopmuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.loopmuse.data.db.AppDatabase
import com.example.loopmuse.service.alarm.AlarmPlaybackService
import com.example.loopmuse.ui.AlarmPermissionDialog
import com.example.loopmuse.ui.HomeScreen
import com.example.loopmuse.ui.ALARM_PERMISSION_INTRO_MARKER
import com.example.loopmuse.ui.readAlarmPermissionStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    companion object {
        @Volatile var isResumed = false
            private set
    }

    private var showAlarmPermissionReminder by mutableStateOf(false)
    private var reminderShownThisSession = false

    override fun onResume() {
        super.onResume()
        isResumed = true
        AlarmPlaybackService.activeScreenIntent(this)?.let(::startActivity)
        checkAlarmPermissions()
    }

    private fun checkAlarmPermissions() {
        if (showAlarmPermissionReminder || reminderShownThisSession || AlarmPlaybackService.activeAlarmId != 0 ||
            !File(noBackupFilesDir, ALARM_PERMISSION_INTRO_MARKER).exists()) return
        lifecycleScope.launch {
            val hasEnabledAlarm = try {
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@MainActivity).alarmDao().getAllAlarmsOnce()
                        .any { it.isEnabled }
                }
            } catch (_: Exception) {
                false
            }
            if (!hasEnabledAlarm || showAlarmPermissionReminder || reminderShownThisSession ||
                AlarmPlaybackService.activeAlarmId != 0) return@launch
            AlarmPlaybackService.ensureNotificationChannel(this@MainActivity)
            if (!readAlarmPermissionStatus(this@MainActivity).ready) {
                reminderShownThisSession = true
                showAlarmPermissionReminder = true
            }
        }
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeScreen()
            if (showAlarmPermissionReminder) AlarmPermissionDialog(
                continueLabel = "완료",
                onReady = { showAlarmPermissionReminder = false },
                onLater = { showAlarmPermissionReminder = false }
            )
        }
    }
}
