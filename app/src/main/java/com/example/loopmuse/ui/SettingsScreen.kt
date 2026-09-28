package com.example.loopmuse.ui

import android.net.Uri
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loopmuse.service.BackupManager
import com.example.loopmuse.service.RestoreMode
import com.example.loopmuse.service.alarm.AlarmGlobalConfig
import com.example.loopmuse.service.alarm.AlarmGlobalSettings

private enum class SettingsPage { MENU, DATA, ABOUT }

@Composable
private fun <T> SettingsDropdownCell(
    title: String,
    value: T,
    options: List<Pair<T, String>>,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$title :", modifier = Modifier.weight(1f, fill = false), maxLines = 1,
            overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true },
                modifier = Modifier.width(70.dp).height(32.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(options.firstOrNull { it.first == value }?.second ?: value.toString(),
                        maxLines = 1, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterEnd).size(14.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (option, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        onSelect(option)
                        expanded = false
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRestore: suspend (Uri, RestoreMode) -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { BackupManager(context.applicationContext) }
    var alarmConfig by remember(context) { mutableStateOf(AlarmGlobalSettings.read(context)) }
    val updateAlarmConfig: (AlarmGlobalConfig) -> Unit = { next ->
        alarmConfig = next
        AlarmGlobalSettings.save(context, next)
    }
    var page by remember { mutableStateOf(SettingsPage.MENU) }
    val goBack = { if (page == SettingsPage.MENU) onBack() else page = SettingsPage.MENU }
    BackHandler(onBack = goBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(when (page) {
                    SettingsPage.MENU -> "전체설정"
                    SettingsPage.DATA -> "백업 및 복원"
                    SettingsPage.ABOUT -> "앱 정보"
                }) },
                navigationIcon = { IconButton(onClick = goBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                } }
            )
        }
    ) { padding ->
        when (page) {
            SettingsPage.MENU -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("백업설정", style = MaterialTheme.typography.titleMedium)
                Card(Modifier.fillMaxWidth().clickable { page = SettingsPage.DATA }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text("백업 및 복원", style = MaterialTheme.typography.titleMedium)
                            Text(if (manager.hasBackupFolder()) "자동 백업 사용 중" else "백업 폴더 설정 필요",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("알람설정", style = MaterialTheme.typography.titleMedium)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val percentOptions = (10..100 step 10).map { it to "$it%" }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            SettingsDropdownCell("소리 크기", alarmConfig.volumePercent,
                                percentOptions, Modifier.weight(1f)) { volume ->
                                updateAlarmConfig(alarmConfig.copy(volumePercent = volume,
                                    fadeStartPercent = alarmConfig.fadeStartPercent.coerceAtMost(volume)))
                            }
                            SettingsDropdownCell("진동 강도", alarmConfig.vibrationPercent,
                                percentOptions, Modifier.weight(1f)) {
                                updateAlarmConfig(alarmConfig.copy(vibrationPercent = it))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            SettingsDropdownCell("점멸 간격", alarmConfig.blinkIntervalSeconds,
                                (1..5).map { it to "${it}초" }, Modifier.weight(1f)) {
                                updateAlarmConfig(alarmConfig.copy(blinkIntervalSeconds = it))
                            }
                            SettingsDropdownCell("페이드인", alarmConfig.fadeEnabled,
                                listOf(true to "켬", false to "끔"), Modifier.weight(1f)) {
                                updateAlarmConfig(alarmConfig.copy(fadeEnabled = it))
                            }
                        }
                        if (alarmConfig.fadeEnabled) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                SettingsDropdownCell("시작 음량", alarmConfig.fadeStartPercent,
                                    percentOptions.filter { it.first <= alarmConfig.volumePercent },
                                    Modifier.weight(1f)) {
                                    updateAlarmConfig(alarmConfig.copy(fadeStartPercent = it))
                                }
                                SettingsDropdownCell("목표까지", alarmConfig.fadeDurationSeconds,
                                    listOf(5, 10, 15, 20, 30, 45, 60, 90, 120).map { it to "${it}초" },
                                    Modifier.weight(1f)) {
                                    updateAlarmConfig(alarmConfig.copy(fadeDurationSeconds = it))
                                }
                            }
                        }
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("방해금지는 휴대폰의 알람 허용 설정을 따릅니다.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                    Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS else Settings.ACTION_SOUND_SETTINGS
                                runCatching { context.startActivity(Intent(action)) }
                                    .onFailure { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                            }) { Text("기기 설정") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("앱", style = MaterialTheme.typography.titleMedium)
                Card(Modifier.fillMaxWidth().clickable { page = SettingsPage.ABOUT }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text("앱 정보", style = MaterialTheme.typography.titleMedium)
                            Text("LoopMuse 버전 및 저장 방식", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            SettingsPage.DATA -> SimpleBackupPage(Modifier.padding(padding), manager, onRestore)
            SettingsPage.ABOUT -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val (versionName, versionCode) = remember(context) {
                    runCatching {
                        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.packageManager.getPackageInfo(
                                context.packageName,
                                android.content.pm.PackageManager.PackageInfoFlags.of(0)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        }
                        @Suppress("DEPRECATION")
                        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            packageInfo.versionCode.toLong()
                        }
                        Pair(packageInfo.versionName ?: "-", code.toString())
                    }.getOrDefault(Pair("-", "-"))
                }
                Text("LoopMuse", style = MaterialTheme.typography.headlineSmall)
                Text("버전 $versionName (코드 $versionCode)")
                HorizontalDivider()
                Text("실행 중인 정보는 앱 내부 데이터베이스에 저장합니다. 선택한 공유 폴더에는 복원용 백업 파일을 별도로 보관합니다.")
            }
        }
    }
}
