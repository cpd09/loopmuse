package com.example.loopmuse.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.loopmuse.service.BackupInfo
import com.example.loopmuse.service.BackupKind
import com.example.loopmuse.service.BackupManager
import com.example.loopmuse.service.RestoreMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SimpleBackupPage(
    modifier: Modifier,
    manager: BackupManager,
    onRestore: suspend (Uri, RestoreMode) -> Unit
) {
    val scope = rememberCoroutineScope()
    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var pickedBackups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var path by remember { mutableStateOf(manager.backupFolderPath()) }
    var lastBackup by remember { mutableStateOf(manager.lastBackupTime()) }
    var lastError by remember { mutableStateOf(manager.lastError()) }
    var showLocationEditor by remember { mutableStateOf(false) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<BackupInfo, RestoreMode>?>(null) }
    var pendingDelete by remember { mutableStateOf<BackupInfo?>(null) }
    var notice by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        backups = manager.listConfiguredBackups() + manager.listConfiguredRecoveryBackups()
        pickedBackups = pickedBackups.filterNot { picked ->
            backups.any { manager.displayFilePath(it.uri) == manager.displayFilePath(picked.uri) }
        }
        path = manager.backupFolderPath()
        lastBackup = manager.lastBackupTime()
        lastError = manager.lastError()
    }

    fun runTask(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = null
            try { block() } catch (e: Exception) { message = e.message ?: "작업을 완료하지 못했습니다." }
            finally { busy = false }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { refresh() }.onFailure { message = it.message ?: "백업을 확인하지 못했습니다." }
        loading = false
    }

    fun requestRestore(info: BackupInfo, mode: RestoreMode) {
        runTask {
            if (runCatching { manager.matchesCurrentData(info.uri) }.getOrNull() == true) {
                notice = "현재 앱 내용과 같습니다" to "이 백업으로 복원하거나 병합할 필요가 없습니다."
            } else {
                pendingRestore = info to mode
            }
        }
    }

    fun addPickedBackup(uri: Uri) {
        showFileBrowser = false
        runTask {
            val info = manager.inspect(uri)
            val active = backups.firstOrNull { it.kind == BackupKind.CURRENT && it.name == BackupManager.CURRENT_FILE }
            val activePath = path?.let { "$it/${BackupManager.CURRENT_FILE}" }
            if ((active != null && manager.sameBackupFile(active.uri, info.uri)) ||
                (activePath != null && manager.displayFilePath(info.uri) == activePath)) {
                message = "현재 사용 중인 백업파일은 위에 표시되어 있습니다."
            } else if (backups.any { manager.sameBackupFile(it.uri, info.uri) }) {
                message = "이미 목록에 있는 백업파일입니다."
            } else {
                pickedBackups = listOf(info) + pickedBackups.filterNot { it.uri == uri }
                message = "선택한 백업파일을 목록에 추가했습니다."
            }
        }
    }
    val activeBackup = backups.firstOrNull { it.kind == BackupKind.CURRENT && it.name == BackupManager.CURRENT_FILE }
    val activeUri = activeBackup?.uri
    val activePath = path?.let { "$it/${BackupManager.CURRENT_FILE}" }
    val allBackups = (backups + pickedBackups)
        .filterNot { info ->
            (activeUri != null && manager.sameBackupFile(activeUri, info.uri)) ||
                (activePath != null && manager.displayFilePath(info.uri) == activePath)
        }
        .distinctBy { manager.displayFilePath(it.uri) }
        .sortedByDescending { it.createdAt }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("백업파일 경로:", style = MaterialTheme.typography.titleMedium)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(BackupManager.CURRENT_FILE, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(activeBackup?.let { backupDateLabel(it.createdAt) } ?: "파일 없음",
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            Text(activeBackup?.let { manager.displayFilePath(it.uri) }
                                ?: path?.let { "$it/${BackupManager.CURRENT_FILE}" }
                                ?: "백업 경로를 선택해 주세요", style = MaterialTheme.typography.bodySmall)
                            Text(when {
                                activeBackup?.valid == true ->
                                    "곡 ${activeBackup.songCount}개 · 재생목록 ${activeBackup.playlistCount}개 · 알람 ${activeBackup.alarmCount}개"
                                activeBackup != null -> "읽을 수 없는 파일"
                                else -> "현재 백업파일이 없습니다."
                            }, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Spacer(Modifier.height(32.dp))
                            TextButton(enabled = !busy, onClick = { showLocationEditor = true },
                                contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Text("수정")
                            }
                        }
                    }
                }
                Text("마지막 자동 백업: ${backupDateLabel(lastBackup)}", style = MaterialTheme.typography.bodySmall)
                if (path != null && !manager.hasBackupFolder()) {
                    Text("폴더 접근을 다시 허용해야 자동 백업이 계속됩니다.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                lastError?.let { Text("최근 저장 오류: $it", color = MaterialTheme.colorScheme.error) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("백업파일 목록", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(enabled = !busy,
                        onClick = { showFileBrowser = true }) {
                        Text("백업파일 찾아보기", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (allBackups.isEmpty()) {
                    Text(if (loading) "백업파일 확인 중..." else "다른 백업파일이 없습니다.")
                }
                allBackups.forEach { info ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(info.name, modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(backupDateLabel(info.createdAt),
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                                Text(manager.displayFilePath(info.uri),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(if (info.valid)
                                    "곡 ${info.songCount}개 · 재생목록 ${info.playlistCount}개 · 알람 ${info.alarmCount}개"
                                    else "읽을 수 없는 파일", style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton(modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    enabled = !busy && info.valid && !info.legacy,
                                    onClick = { requestRestore(info, RestoreMode.REPLACE) }) { Text("복원") }
                                TextButton(modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    enabled = !busy && info.valid,
                                    onClick = { requestRestore(info, RestoreMode.MERGE) }) { Text("병합") }
                                TextButton(modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    enabled = !busy, onClick = { pendingDelete = info }) { Text("삭제") }
                            }
                        }
                    }
                }
            }
        }
        if (busy) CircularProgressIndicator()
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(8.dp))
    }

    if (showLocationEditor) BackupLocationDialog(
        manager = manager,
        onSaved = { showLocationEditor = false; scope.launch { refresh() } },
        onCancel = { showLocationEditor = false }
    )

    if (showFileBrowser) BackupFileBrowserDialog(
        manager = manager,
        onSelected = ::addPickedBackup,
        onClose = { showFileBrowser = false }
    )

    notice?.let { (title, detail) ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text(title) },
            text = { Text(detail) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("확인") } }
        )
    }

    pendingRestore?.let { (info, mode) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(if (mode == RestoreMode.REPLACE) "이 백업으로 복원할까요?" else "이 백업을 병합할까요?") },
            text = { Text("${manager.displayFilePath(info.uri)}\n\n${backupDateLabel(info.createdAt)} 백업입니다. " +
                if (mode == RestoreMode.REPLACE) "현재 앱 정보는 안전 복사본을 남긴 뒤 교체됩니다."
                else "현재 앱 정보와 합칩니다.") },
            confirmButton = { TextButton(onClick = {
                pendingRestore = null
                runTask {
                    onRestore(info.uri, mode)
                    if (manager.hasBackupFolder()) manager.backupNow()
                    refresh()
                    message = if (mode == RestoreMode.REPLACE) "복원을 완료했습니다." else "병합을 완료했습니다."
                }
            }) { Text(if (mode == RestoreMode.REPLACE) "복원" else "병합") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("취소") } }
        )
    }

    pendingDelete?.let { info ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("백업파일을 삭제할까요?") },
            text = { Text("${manager.displayFilePath(info.uri)}\n\n삭제하면 이 파일로 복원할 수 없습니다.") },
            confirmButton = { TextButton(onClick = {
                pendingDelete = null
                runTask {
                    val picked = pickedBackups.any { it.uri == info.uri } && backups.none { it.uri == info.uri }
                    val deleted = if (picked) manager.deletePickedBackup(info) else manager.deleteBackup(info)
                    check(deleted) { "백업파일을 삭제하지 못했습니다." }
                    pickedBackups = pickedBackups.filterNot { it.uri == info.uri }
                    refresh()
                    message = "백업파일을 삭제했습니다."
                }
            }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } }
        )
    }
}

private fun backupDateLabel(time: Long): String = if (time <= 0) "날짜 정보 없음"
    else SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(time))
