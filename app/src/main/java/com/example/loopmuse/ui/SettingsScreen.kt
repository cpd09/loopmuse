package com.example.loopmuse.ui

import android.net.Uri
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.example.loopmuse.service.BackupManager
import com.example.loopmuse.service.RestoreMode
import com.example.loopmuse.data.SongDisplayConfig
import com.example.loopmuse.data.SongDisplayField
import com.example.loopmuse.data.SongDisplaySettings
import com.example.loopmuse.service.alarm.AlarmGlobalConfig
import com.example.loopmuse.service.alarm.AlarmGlobalSettings

private enum class SettingsPage { MENU, DATA, ABOUT }

@Composable
private fun <T> SettingsDropdownButton(
    value: T,
    options: List<Pair<T, String>>,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppOutlinedButton(onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(32.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(options.firstOrNull { it.first == value }?.second ?: value.toString(),
                    maxLines = 1, fontSize = 11.sp, textAlign = TextAlign.Center)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterEnd).size(12.dp))
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

@Composable
private fun <T> SettingsInlineDropdown(
    title: String,
    value: T,
    options: List<Pair<T, String>>,
    buttonWidth: Dp,
    labelWidth: Dp = 64.dp,
    labelGap: Dp = 3.dp,
    labelFontSize: TextUnit = 11.sp,
    onSelect: (T) -> Unit
) {
    Text("$title :", modifier = Modifier.width(labelWidth), fontSize = labelFontSize,
        maxLines = 1, textAlign = TextAlign.End)
    Spacer(Modifier.width(labelGap))
    SettingsDropdownButton(value, options, Modifier.width(buttonWidth), onSelect)
}

@Composable
private fun SettingsInlineToggle(
    title: String,
    checked: Boolean,
    labelWidth: Dp = 52.dp,
    labelGap: Dp = 3.dp,
    labelFontSize: TextUnit = 11.sp,
    switchWidth: Dp = 38.dp,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.height(32.dp)
        .semantics {
            contentDescription = title
            stateDescription = if (checked) "켜짐" else "꺼짐"
        }
        .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text("$title :", modifier = Modifier.width(labelWidth), fontSize = labelFontSize,
            maxLines = 1, textAlign = TextAlign.Start)
        Spacer(Modifier.width(labelGap))
        AppCompactSwitchIndicator(checked, width = switchWidth)
    }
}

@Composable
private fun <T> SettingsGroupedControl(
    title: String,
    value: T,
    options: List<Pair<T, String>>,
    modifier: Modifier,
    onSelect: (T) -> Unit
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$title :", fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        SettingsDropdownButton(value, options, Modifier.width(72.dp), onSelect)
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
    var songDisplayConfig by remember(context) { mutableStateOf(SongDisplaySettings.read(context)) }
    val updateAlarmConfig: (AlarmGlobalConfig) -> Unit = { next ->
        alarmConfig = next
        AlarmGlobalSettings.save(context, next)
    }
    var page by remember { mutableStateOf(SettingsPage.MENU) }
    val goBack = {
        if (page == SettingsPage.MENU) onBack() else {
            page = SettingsPage.MENU
            songDisplayConfig = SongDisplaySettings.read(context)
        }
    }
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
                AppCard(Modifier.fillMaxWidth().clickable { page = SettingsPage.DATA }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = AppCardStyle.horizontalPadding,
                        vertical = AppCardStyle.verticalPadding), verticalAlignment = Alignment.CenterVertically) {
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
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = AppCardStyle.horizontalPadding,
                        vertical = AppCardStyle.verticalPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val percentOptions = (10..100 step 10).map { it to "$it%" }
                        Row(Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsGroupedControl("소리 크기", alarmConfig.volumePercent,
                                percentOptions, Modifier.weight(1f)) { volume ->
                                updateAlarmConfig(alarmConfig.copy(volumePercent = volume,
                                    fadeStartPercent = alarmConfig.fadeStartPercent.coerceAtMost(volume)))
                            }
                            SettingsGroupedControl("진동 강도", alarmConfig.vibrationPercent,
                                percentOptions, Modifier.weight(1f)) {
                                updateAlarmConfig(alarmConfig.copy(vibrationPercent = it))
                            }
                            SettingsGroupedControl("점멸 간격", alarmConfig.blinkIntervalSeconds,
                                (1..5).map { it to "${it}초" }, Modifier.weight(1f)) {
                                updateAlarmConfig(alarmConfig.copy(blinkIntervalSeconds = it))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val compact = maxWidth < 355.dp
                            val narrow = maxWidth < 304.dp
                            val labelWidth = if (compact) 46.dp else 64.dp
                            val labelGap = if (compact) 1.dp else 3.dp
                            val labelFontSize = if (compact) 10.sp else 11.sp
                            val fadeToggle: @Composable () -> Unit = {
                                SettingsInlineToggle("페이드인", alarmConfig.fadeEnabled,
                                    labelWidth = if (compact) 46.dp else 52.dp,
                                    labelGap = labelGap, labelFontSize = labelFontSize,
                                    switchWidth = if (compact) 34.dp else 38.dp) {
                                    updateAlarmConfig(alarmConfig.copy(fadeEnabled = it))
                                }
                            }
                            val fadeStart: @Composable () -> Unit = {
                                SettingsInlineDropdown("시작음량", alarmConfig.fadeStartPercent,
                                    percentOptions.filter { it.first <= alarmConfig.volumePercent },
                                    if (compact) 56.dp else 48.dp,
                                    labelWidth = labelWidth, labelGap = labelGap,
                                    labelFontSize = labelFontSize) {
                                    updateAlarmConfig(alarmConfig.copy(fadeStartPercent = it))
                                }
                            }
                            val fadeDuration: @Composable () -> Unit = {
                                SettingsInlineDropdown("도달시간", alarmConfig.fadeDurationSeconds,
                                    listOf(5, 10, 15, 20, 30, 45, 60, 90, 120).map { it to "${it}초" },
                                    if (compact) 66.dp else 72.dp,
                                    labelWidth = labelWidth, labelGap = labelGap,
                                    labelFontSize = labelFontSize) {
                                    updateAlarmConfig(alarmConfig.copy(fadeDurationSeconds = it))
                                }
                            }
                            if (narrow) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        fadeToggle()
                                        Spacer(Modifier.width(4.dp))
                                        fadeStart()
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) { fadeDuration() }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    fadeToggle()
                                    Spacer(Modifier.width(if (compact) 2.dp else 4.dp))
                                    fadeStart()
                                    Spacer(Modifier.width(if (compact) 2.dp else 4.dp))
                                    fadeDuration()
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start) {
                            SettingsInlineToggle("스누즈", alarmConfig.snoozeEnabled) {
                                updateAlarmConfig(alarmConfig.copy(snoozeEnabled = it))
                            }
                            Spacer(Modifier.width(6.dp))
                            SettingsInlineDropdown("재알람시간", alarmConfig.snoozeMinutes,
                                (1..30).map { it to "${it}분" }, 54.dp) {
                                updateAlarmConfig(alarmConfig.copy(snoozeMinutes = it))
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
                Text("재생목록 표기방식", style = MaterialTheme.typography.titleMedium)
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = AppCardStyle.horizontalPadding,
                        vertical = AppCardStyle.verticalPadding), verticalAlignment = Alignment.CenterVertically) {
                        songDisplayConfig.fields.forEachIndexed { index, field ->
                            if (index > 0) Text(" - ", fontSize = 12.sp)
                            val options = SongDisplayField.entries.filter { candidate ->
                                candidate == SongDisplayField.NONE || candidate == field ||
                                    candidate !in songDisplayConfig.fields
                            }.map { it to it.label }
                            SettingsDropdownButton(field, options,
                                Modifier.weight(1f).semantics {
                                    contentDescription = "${index + 1}번째 표기 항목"
                                }) { selected ->
                                val next = songDisplayConfig.fields.toMutableList()
                                next[index] = selected
                                val config = SongDisplayConfig(next).normalized()
                                songDisplayConfig = config
                                SongDisplaySettings.save(context, config)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                AppCard(Modifier.fillMaxWidth().clickable { page = SettingsPage.ABOUT }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = AppCardStyle.horizontalPadding,
                        vertical = AppCardStyle.verticalPadding), verticalAlignment = Alignment.CenterVertically) {
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
