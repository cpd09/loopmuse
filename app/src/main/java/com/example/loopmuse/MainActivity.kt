package com.example.loopmuse

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.loopmuse.data.db.AppDatabase
import com.example.loopmuse.service.alarm.AlarmPlaybackService
import com.example.loopmuse.ui.HomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    companion object {
        @Volatile var isResumed = false
            private set
    }

    private enum class AlarmNotificationIssue { PERMISSION, APP_SETTINGS, CHANNEL_SETTINGS, FULL_SCREEN_SETTINGS }
    private var notificationIssue by mutableStateOf<AlarmNotificationIssue?>(null)
    private val shownNotificationIssues = mutableSetOf<AlarmNotificationIssue>()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { checkAlarmNotificationSettings() }

    override fun onResume() {
        super.onResume()
        isResumed = true
        AlarmPlaybackService.activeScreenIntent(this)?.let(::startActivity)
        checkAlarmNotificationSettings()
    }

    private fun checkAlarmNotificationSettings() {
        if (notificationIssue != null || AlarmPlaybackService.activeAlarmId != 0) return
        lifecycleScope.launch {
            val hasEnabledAlarm = try {
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@MainActivity).alarmDao().getAllAlarmsOnce()
                        .any { it.isEnabled }
                }
            } catch (_: Exception) {
                false
            }
            if (!hasEnabledAlarm || notificationIssue != null || AlarmPlaybackService.activeAlarmId != 0) return@launch
            AlarmPlaybackService.ensureNotificationChannel(this@MainActivity)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val currentIssue = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                    AlarmNotificationIssue.PERMISSION
                !manager.areNotificationsEnabled() -> AlarmNotificationIssue.APP_SETTINGS
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    manager.getNotificationChannel(AlarmPlaybackService.ALARM_CHANNEL_ID)
                        ?.importance?.let { it < NotificationManager.IMPORTANCE_HIGH } == true ->
                    AlarmNotificationIssue.CHANNEL_SETTINGS
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    !manager.canUseFullScreenIntent() -> AlarmNotificationIssue.FULL_SCREEN_SETTINGS
                else -> null
            }
            if (currentIssue != null && shownNotificationIssues.add(currentIssue)) notificationIssue = currentIssue
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
            notificationIssue?.let { issue ->
                AlertDialog(
                    onDismissRequest = { notificationIssue = null },
                    title = { Text(if (issue == AlarmNotificationIssue.FULL_SCREEN_SETTINGS)
                        "잠금화면 알람 표시가 꺼져 있습니다" else "알람 알림을 확인해 주세요") },
                    text = { Text(when (issue) {
                        AlarmNotificationIssue.FULL_SCREEN_SETTINGS ->
                            "잠금화면에서 알람 화면을 바로 보려면 '전체 화면 알림'을 허용해 주세요."
                        AlarmNotificationIssue.CHANNEL_SETTINGS ->
                            "알람 알림을 '중요' 이상으로 설정하면 알림창에 종료 버튼이 나타납니다."
                        else -> "알람 소리가 나도 알림창에 종료 버튼이 나타나지 않습니다. 알림을 허용해 주세요."
                    }) },
                    confirmButton = {
                        TextButton(onClick = {
                            notificationIssue = null
                            when (issue) {
                                AlarmNotificationIssue.PERMISSION ->
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                AlarmNotificationIssue.APP_SETTINGS ->
                                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    })
                                AlarmNotificationIssue.CHANNEL_SETTINGS ->
                                    startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                        putExtra(Settings.EXTRA_CHANNEL_ID, AlarmPlaybackService.ALARM_CHANNEL_ID)
                                    })
                                AlarmNotificationIssue.FULL_SCREEN_SETTINGS ->
                                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:$packageName")))
                            }
                        }) { Text(if (issue == AlarmNotificationIssue.FULL_SCREEN_SETTINGS) "전체 화면 허용" else "설정 열기") }
                    },
                    dismissButton = { TextButton(onClick = { notificationIssue = null }) { Text("나중에") } }
                )
            }
        }
    }
}
