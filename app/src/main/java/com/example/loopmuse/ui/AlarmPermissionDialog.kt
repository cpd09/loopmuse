package com.example.loopmuse.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.core.content.ContextCompat
import com.example.loopmuse.service.alarm.AlarmPlaybackService

internal const val ALARM_PERMISSION_INTRO_MARKER = "alarm_permission_intro_v1"

internal enum class NotificationIssue { RUNTIME, APP_BLOCKED, CHANNEL_BLOCKED }

internal data class AlarmPermissionStatus(
    val notificationReady: Boolean,
    val fullScreenReady: Boolean,
    val exactAlarmReady: Boolean,
    val notificationIssue: NotificationIssue?,
    val blockedChannelId: String?
) {
    val ready: Boolean get() = notificationReady && fullScreenReady && exactAlarmReady
}

internal fun readAlarmPermissionStatus(context: Context): AlarmPermissionStatus {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val runtimeDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    val blockedChannel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        listOf(AlarmPlaybackService.ALARM_CHANNEL_ID, AlarmPlaybackService.SILENT_ALARM_CHANNEL_ID)
            .firstOrNull { channelId ->
                notificationManager.getNotificationChannel(channelId)
                    ?.importance?.let { it < NotificationManager.IMPORTANCE_HIGH } == true
            } else null
    val issue = when {
        runtimeDenied -> NotificationIssue.RUNTIME
        !notificationManager.areNotificationsEnabled() -> NotificationIssue.APP_BLOCKED
        blockedChannel != null -> NotificationIssue.CHANNEL_BLOCKED
        else -> null
    }
    return AlarmPermissionStatus(
        notificationReady = issue == null,
        fullScreenReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent(),
        exactAlarmReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms(),
        notificationIssue = issue,
        blockedChannelId = blockedChannel
    )
}

@Composable
internal fun AlarmPermissionDialog(
    continueLabel: String,
    onReady: () -> Unit,
    onLater: () -> Unit
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var notificationRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationRequested = true
        refresh++
    }
    LaunchedEffect(Unit) {
        AlarmPlaybackService.ensureNotificationChannel(context)
        refresh++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val status = remember(context, refresh) { readAlarmPermissionStatus(context) }

    fun openNotificationSettings() {
        val intent = when (status.notificationIssue) {
            NotificationIssue.CHANNEL_BLOCKED -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, status.blockedChannelId)
            }
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent)
    }

    val total = if (status.exactAlarmReady) 2 else 3
    val completed = listOf(status.notificationReady, status.fullScreenReady, status.exactAlarmReady)
        .take(total).count { it }
    androidx.compose.ui.window.Dialog(onDismissRequest = onLater) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.82f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = AppCardStyle.border(),
            shadowElevation = 6.dp
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("알람 사용 준비", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                }
                Text("정해진 시간에 울리고 잠금 화면에 알람을 표시하려면 아래 항목을 허용해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                PermissionRow(
                    title = "알림 허용",
                    detail = "알람 알림과 종료 버튼 표시",
                    granted = status.notificationReady,
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    onClick = {
                        if (status.notificationIssue == NotificationIssue.RUNTIME && !notificationRequested)
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openNotificationSettings()
                    }
                )
                PermissionRow(
                    title = "전체 화면 알람",
                    detail = "잠금 화면에 알람 화면 표시",
                    granted = status.fullScreenReady,
                    icon = { Icon(Icons.Default.Fullscreen, contentDescription = null) },
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}")))
                    }
                )
                if (!status.exactAlarmReady) PermissionRow(
                    title = "정확한 알람",
                    detail = "설정한 시각에 알람 시작",
                    granted = false,
                    icon = { Icon(Icons.Default.Alarm, contentDescription = null) },
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")))
                    }
                )
                Text(if (status.ready) "알람 사용 준비가 완료되었습니다."
                    else "${completed}/${total}개 완료 · 알람을 켜려면 모두 허용해 주세요.",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status.ready) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onLater) { Text("나중에") }
                    Spacer(Modifier.width(8.dp))
                    AppButton(onClick = onReady, enabled = status.ready, elevation = null,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(continueLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(Modifier.fillMaxWidth(), shape = AppButtonStyle.shape,
        color = MaterialTheme.colorScheme.surface,
        border = AppCardStyle.border()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (granted) Icon(Icons.Default.CheckCircle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                else icon()
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) Text("완료", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            else AppOutlinedButton(onClick = onClick, elevation = null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)) {
                Text("허용", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
