package com.example.loopmuse.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.loopmuse.service.BackupInfo
import com.example.loopmuse.service.BackupKind
import com.example.loopmuse.service.BackupManager
import com.example.loopmuse.service.RestoreMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun DataManagementDialog(
    firstRun: Boolean,
    onFinish: () -> Unit,
    onRestore: suspend (Uri, RestoreMode) -> Unit,
    onFresh: suspend () -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { BackupManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var chosenFolder by remember { mutableStateOf<Uri?>(null) }
    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<BackupInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingMode by remember { mutableStateOf<RestoreMode?>(null) }
    var confirmFresh by remember { mutableStateOf(false) }
    var showOlder by remember { mutableStateOf(false) }
    var pendingFolderStart by remember { mutableStateOf<Boolean?>(null) }
    var backupStatus by remember { mutableStateOf(statusText(manager)) }
    var localStatus by remember { mutableStateOf("현재 정보 확인 중...") }

    LaunchedEffect(Unit) {
        try {
            val local = manager.localSummary()
            localStatus = "현재 기기 정보: 곡 ${local.songs}개 · 재생목록 ${local.playlists}개 · 알람 ${local.alarms}개"
            backups = manager.listConfiguredBackups() + manager.listConfiguredRecoveryBackups() + manager.listLocalSafetyCopies()
            selected = backups.firstOrNull { it.kind == BackupKind.CURRENT && it.valid } ?: backups.firstOrNull { it.valid }
        } catch (e: Exception) { localStatus = "현재 정보를 확인하지 못했습니다."; message = e.message ?: "백업 목록을 읽지 못했습니다." }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val found = manager.listBackups(uri) + manager.listRecoveryBackups(uri) + manager.listLocalSafetyCopies()
                chosenFolder = uri
                backups = found
                selected = found.firstOrNull { it.kind == BackupKind.CURRENT && it.valid } ?: found.firstOrNull { it.valid }
                message = if (found.none { it.valid }) "이 폴더에 복원 가능한 백업이 없습니다." else "복원할 백업을 확인해 주세요."
            } catch (e: Exception) { message = e.message ?: "폴더를 읽지 못했습니다." }
            finally { busy = false }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val info = manager.inspect(uri)
                selected = info
                backups = (listOf(info) + backups).distinctBy { it.uri }
                message = "백업 파일을 확인했습니다."
            } catch (e: Exception) { message = e.message ?: "백업 파일을 읽지 못했습니다." }
            finally { busy = false }
        }
    }

    fun startChosenFolder(finishAfter: Boolean) {
        val uri = chosenFolder ?: return
        scope.launch {
            busy = true
            try {
                val info = manager.selectFolderAndBackup(uri)
                chosenFolder = null
                backupStatus = statusText(manager)
                if (finishAfter) onFinish() else {
                    val listError = runCatching {
                        backups = manager.listConfiguredBackups() + manager.listConfiguredRecoveryBackups() + manager.listLocalSafetyCopies()
                    }.exceptionOrNull()
                    selected = info
                    message = if (listError == null) "자동 백업을 설정하고 현재 정보를 저장했습니다."
                        else "자동 백업을 설정하고 현재 정보를 저장했습니다. 목록 갱신 실패: ${listError.message ?: "알 수 없는 오류"}"
                }
            } catch (e: Exception) { message = e.message ?: "백업에 실패했습니다." }
            finally { busy = false }
        }
    }

    fun requestFolderStart(finishAfter: Boolean) {
        if (chosenFolder != null && backups.any { it.kind == BackupKind.CURRENT }) pendingFolderStart = finishAfter
        else startChosenFolder(finishAfter)
    }

    val current = backups.firstOrNull { it.kind == BackupKind.CURRENT && it.valid }
    val older = backups.filter { it.kind != BackupKind.CURRENT }
    val path = chosenFolder?.let(manager::displayFolderPath) ?: manager.backupFolderPath()

    Dialog(onDismissRequest = { if (!firstRun && !busy) onFinish() },
        properties = DialogProperties(dismissOnBackPress = !firstRun, dismissOnClickOutside = !firstRun)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (firstRun) "LoopMuse 데이터 선택" else "데이터 보관 및 복원", style = MaterialTheme.typography.titleLarge)
                Text(if (firstRun) "현재 정보를 계속 쓰거나, 이전 백업을 복원·병합하거나, 새로 시작할 수 있습니다."
                    else "앱 안의 정보는 자동 백업 폴더를 지정하면 별도 파일로 보관됩니다.")
                Text(localStatus, style = MaterialTheme.typography.bodyMedium)
                Text(backupStatus, style = MaterialTheme.typography.bodySmall)
                path?.let { folderPath ->
                    Text("백업 파일 위치", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text("$folderPath/${BackupManager.CURRENT_FILE}", style = MaterialTheme.typography.bodySmall) }
                }
                Text("권장 위치: 내부 저장소 > Documents > LoopMuse. 처음 한 번 선택하면 사용 중 변경 사항을 자동 보관합니다. 재설치 후에는 이 폴더를 다시 선택해 주세요.",
                    style = MaterialTheme.typography.bodySmall)
                manager.lastError()?.let { Text("최근 자동 백업 오류: $it", color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { folderPicker.launch(null) }) { Text("백업 폴더 선택") }
                    OutlinedButton(enabled = !busy, onClick = { filePicker.launch(arrayOf("application/json", "*/*")) }) { Text("백업 파일 선택") }
                }
                if (chosenFolder != null) {
                    Button(enabled = !busy, onClick = {
                        requestFolderStart(false)
                    }) { Text("이 폴더에 자동 백업 시작") }
                } else if (manager.hasBackupFolder()) {
                    OutlinedButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val info = manager.backupNow()
                                val listError = runCatching {
                                    backups = manager.listConfiguredBackups() + manager.listConfiguredRecoveryBackups() + manager.listLocalSafetyCopies()
                                }.exceptionOrNull()
                                selected = info
                                backupStatus = statusText(manager)
                                message = if (listError == null) "백업 완료: ${info.name}"
                                    else "백업 완료: ${info.name}. 목록 갱신 실패: ${listError.message ?: "알 수 없는 오류"}"
                            } catch (e: Exception) { message = e.message ?: "백업에 실패했습니다." }
                            finally { busy = false }
                        }
                    }) { Text("지금 백업") }
                }
                if (backups.isNotEmpty()) {
                    HorizontalDivider()
                    Text("복원 가능한 백업", style = MaterialTheme.typography.titleMedium)
                    selected?.let { info ->
                        Text("선택한 파일: ${info.name}", style = MaterialTheme.typography.titleSmall)
                        Text(backupDescription(info), style = MaterialTheme.typography.bodySmall)
                    }
                    if (current != null && selected?.uri != current.uri) {
                        TextButton(enabled = !busy, onClick = { selected = current }) { Text("현재 자동 백업 선택") }
                    }
                    if (older.isNotEmpty()) {
                        TextButton(onClick = { showOlder = !showOlder }) {
                            Text(if (showOlder) "이전 파일 접기" else "이전 파일 ${older.size}개 보기")
                        }
                    }
                    if (showOlder) {
                        older.forEach { info ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected?.uri == info.uri, onClick = { selected = info }, enabled = !busy && info.valid)
                                TextButton(onClick = { selected = info }, enabled = !busy && info.valid) {
                                    Text("${info.name}\n${backupDescription(info)}")
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(enabled = !busy && selected?.valid == true && selected?.legacy == false,
                            onClick = { pendingMode = RestoreMode.REPLACE }) { Text("교체 복원") }
                        OutlinedButton(enabled = !busy && selected?.valid == true,
                            onClick = { pendingMode = RestoreMode.MERGE }) { Text("병합") }
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (busy) CircularProgressIndicator()
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(enabled = !busy, onClick = {
                        if (!firstRun || chosenFolder == null) onFinish()
                        else requestFolderStart(true)
                    }) { Text(if (firstRun) "현재 정보로 계속" else "닫기") }
                    TextButton(enabled = !busy, onClick = { confirmFresh = true }) { Text("새로 시작") }
                }
                Spacer(Modifier.height(2.dp))
                Text("음악 파일은 백업에 포함되지 않습니다. 기기를 바꾸면 음악 폴더를 다시 확인해 주세요.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    pendingFolderStart?.let { finishAfter ->
        AlertDialog(onDismissRequest = { pendingFolderStart = null },
            title = { Text("기존 자동 백업을 바꿀까요?") },
            text = { Text("선택한 폴더에 이미 ${BackupManager.CURRENT_FILE} 파일이 있습니다. 현재 앱 정보를 저장하면 이 파일이 바뀝니다. 정상적인 기존 파일은 LoopMuse_Recovery 폴더에 보관합니다. 이전 정보를 먼저 쓰려면 취소하고 교체 복원 또는 병합을 선택하세요.") },
            confirmButton = { TextButton(onClick = { pendingFolderStart = null; startChosenFolder(finishAfter) }) { Text("현재 정보 저장") } },
            dismissButton = { TextButton(onClick = { pendingFolderStart = null }) { Text("취소") } })
    }

    pendingMode?.let { mode ->
        AlertDialog(onDismissRequest = { pendingMode = null },
            title = { Text(if (mode == RestoreMode.REPLACE) "기존 정보를 교체할까요?" else "백업 정보를 병합할까요?") },
            text = { Text(if (mode == RestoreMode.REPLACE)
                "현재 곡 정보·재생목록·알람·선택 폴더·재생 기록이 백업 내용으로 바뀝니다. 변경 전 안전 복사본을 먼저 저장합니다."
                else "곡 정보는 최근 수정본을 우선하고, 재생목록과 선택 폴더는 합칩니다. 기존 알람은 유지하며 중복 알람은 추가하지 않습니다.") },
            confirmButton = { TextButton(onClick = {
                val target = selected ?: return@TextButton
                pendingMode = null
                scope.launch {
                    busy = true
                    try {
                        onRestore(target.uri, mode)
                        val folderError = chosenFolder?.let { folder ->
                            runCatching { manager.selectFolderAndBackup(folder) }.exceptionOrNull()
                                .also { if (it == null) chosenFolder = null }
                        }
                        val alarmWarning = manager.lastAlarmWarning()
                        if (folderError == null && alarmWarning == null) onFinish()
                        else message = buildString {
                            append("복원이 완료되었습니다.")
                            alarmWarning?.let { append(" $it") }
                            folderError?.let { append(" 자동 백업 폴더 설정 실패: ${it.message ?: "알 수 없는 오류"}") }
                        }
                    } catch (e: Exception) { message = e.message ?: "복원에 실패했습니다." }
                    finally { busy = false }
                }
            }) { Text(if (mode == RestoreMode.REPLACE) "교체" else "병합") } },
            dismissButton = { TextButton(onClick = { pendingMode = null }) { Text("취소") } })
    }

    if (confirmFresh) {
        AlertDialog(onDismissRequest = { confirmFresh = false },
            title = { Text("새로 시작할까요?") },
            text = { Text("현재 앱의 곡 정보·재생목록·알람·선택 폴더·재생 기록을 비웁니다. 가능한 안전 복사본을 먼저 저장합니다. 외부 백업 파일은 지우지 않습니다.") },
            confirmButton = { TextButton(onClick = {
                confirmFresh = false
                scope.launch {
                    busy = true
                    try {
                        onFresh()
                        val folderError = chosenFolder?.let { folder ->
                            runCatching { manager.selectFolderAndBackup(folder) }.exceptionOrNull()
                                .also { if (it == null) chosenFolder = null }
                        }
                        if (folderError == null) onFinish()
                        else message = "현재 정보를 비웠습니다. 자동 백업 폴더 설정 실패: ${folderError.message ?: "알 수 없는 오류"}"
                    }
                    catch (e: Exception) { message = e.message ?: "초기화에 실패했습니다." }
                    finally { busy = false }
                }
            }) { Text("새로 시작") } },
            dismissButton = { TextButton(onClick = { confirmFresh = false }) { Text("취소") } })
    }
}

private fun statusText(manager: BackupManager): String {
    val time = manager.lastBackupTime()
    val date = if (time > 0) SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(time)) else "없음"
    return "자동 백업 폴더: ${if (manager.hasBackupFolder()) "연결됨" else "선택 필요"} · 마지막 백업: $date"
}

private fun backupDescription(info: BackupInfo): String {
    if (!info.valid) return "손상되었거나 저장이 중단된 파일 · 복원 불가"
    val date = if (info.createdAt > 0) SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(info.createdAt)) else "예전 형식"
    return "$date · 곡 정보 ${info.songCount}개 · 재생목록 ${info.playlistCount}개 · 알람 ${info.alarmCount}개"
}
