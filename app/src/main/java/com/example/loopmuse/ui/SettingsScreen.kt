package com.example.loopmuse.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.loopmuse.service.BackupInfo
import com.example.loopmuse.service.BackupKind
import com.example.loopmuse.service.BackupManager
import com.example.loopmuse.service.RestoreMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class SettingsPage { MENU, DATA, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRestore: suspend (Uri, RestoreMode) -> Unit,
    onFresh: suspend () -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { BackupManager(context.applicationContext) }
    var page by remember { mutableStateOf(SettingsPage.MENU) }
    val goBack = { if (page == SettingsPage.MENU) onBack() else page = SettingsPage.MENU }
    BackHandler(onBack = goBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(when (page) {
                    SettingsPage.MENU -> "설정"
                    SettingsPage.DATA -> "데이터 보관·복원"
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
                Text("데이터", style = MaterialTheme.typography.titleMedium)
                Card(Modifier.fillMaxWidth().clickable { page = SettingsPage.DATA }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text("보관 및 복원", style = MaterialTheme.typography.titleMedium)
                            Text(if (manager.hasBackupFolder()) "자동 백업 사용 중" else "백업 폴더 설정 필요",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
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
            SettingsPage.DATA -> DataBackupPage(Modifier.padding(padding), manager, onRestore, onFresh)
            SettingsPage.ABOUT -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val version = remember(context) {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
                }
                Text("LoopMuse", style = MaterialTheme.typography.headlineSmall)
                Text("버전 $version")
                HorizontalDivider()
                Text("실행 중인 정보는 앱 내부 데이터베이스에 저장합니다. 선택한 공유 폴더에는 복원용 백업 파일을 별도로 보관합니다.")
            }
        }
    }
}

@Composable
private fun DataBackupPage(
    modifier: Modifier,
    manager: BackupManager,
    onRestore: suspend (Uri, RestoreMode) -> Unit,
    onFresh: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    var chosenFolder by remember { mutableStateOf<Uri?>(null) }
    var rootBackups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var recoveryBackups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<BackupInfo?>(null) }
    var showOlder by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<RestoreMode?>(null) }
    var pendingDelete by remember { mutableStateOf<BackupInfo?>(null) }
    var pendingBulkDelete by remember { mutableStateOf(false) }
    var pendingFresh by remember { mutableStateOf(false) }
    var pendingUseFolder by remember { mutableStateOf(false) }
    var lastBackup by remember { mutableStateOf(manager.lastBackupTime()) }
    var lastError by remember { mutableStateOf(manager.lastError()) }

    suspend fun refresh(folder: Uri? = chosenFolder) {
        val root = if (folder != null) manager.listBackups(folder) else manager.listConfiguredBackups()
        val recovery = (if (folder != null) manager.listRecoveryBackups(folder) else manager.listConfiguredRecoveryBackups()) +
            manager.listLocalSafetyCopies()
        rootBackups = root
        recoveryBackups = recovery
        selected = (root + recovery).firstOrNull { it.uri == selected?.uri && it.valid }
            ?: root.firstOrNull { it.kind == BackupKind.CURRENT && it.valid }
            ?: (root + recovery).firstOrNull { it.valid }
            ?: root.firstOrNull { it.kind == BackupKind.CURRENT }
        lastBackup = manager.lastBackupTime()
        lastError = manager.lastError()
    }

    fun runTask(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try { block() }
            catch (e: Exception) { message = e.message ?: "작업을 완료하지 못했습니다." }
            finally { busy = false }
        }
    }

    fun useChosenFolder() {
        val uri = chosenFolder ?: return
        runTask {
            val info = manager.selectFolderAndBackup(uri)
            chosenFolder = null
            val listError = runCatching { refresh() }.exceptionOrNull()
            selected = info
            message = if (listError == null) "자동 백업 위치를 설정했습니다."
                else "자동 백업 위치를 설정했습니다. 목록 갱신 실패: ${listError.message ?: "알 수 없는 오류"}"
        }
    }

    suspend fun finishDataChange(success: String) {
        val folderError = chosenFolder?.let { folder ->
            runCatching { manager.selectFolderAndBackup(folder) }.exceptionOrNull()
                .also { if (it == null) chosenFolder = null }
        }
        val listError = runCatching { refresh() }.exceptionOrNull()
        message = buildString {
            append(success)
            manager.lastAlarmWarning()?.let { append(" $it") }
            folderError?.let { append(" 자동 백업 폴더 설정 실패: ${it.message ?: "알 수 없는 오류"}") }
            listError?.let { append(" 백업 목록 갱신 실패: ${it.message ?: "알 수 없는 오류"}") }
        }
    }

    LaunchedEffect(Unit) { runCatching { refresh() }.onFailure { message = it.message ?: "백업 목록을 읽지 못했습니다." } }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runTask {
            refresh(uri)
            chosenFolder = uri
            message = "폴더를 확인했습니다. 복원하거나 자동 백업 위치로 사용할 수 있습니다."
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runTask {
            selected = manager.inspect(uri)
            message = "선택한 백업 파일을 확인했습니다."
        }
    }

    val active = rootBackups.firstOrNull { it.kind == BackupKind.CURRENT }
    val older = rootBackups.filter { it.kind != BackupKind.CURRENT && it.kind != BackupKind.RECOVERY }
    val allRecovery = rootBackups.filter { it.kind == BackupKind.RECOVERY } + recoveryBackups
    val bulkCandidates = older.filter { it.kind == BackupKind.OLDER || it.kind == BackupKind.PENDING }
    val path = chosenFolder?.let(manager::displayFolderPath) ?: manager.backupFolderPath()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("자동 백업", style = MaterialTheme.typography.titleLarge)
                }
                Text(when {
                    chosenFolder != null -> "새 폴더 선택 중"
                    manager.hasBackupFolder() -> "사용 중 · 변경 사항 자동 저장"
                    else -> "폴더를 선택하면 자동 백업을 시작합니다"
                }, color = MaterialTheme.colorScheme.primary)
                if (path != null) {
                    Text("백업 파일 위치", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text("$path/${BackupManager.CURRENT_FILE}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!manager.hasBackupFolder() && chosenFolder == null) {
                        Text("폴더 접근을 다시 허용해야 합니다.", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text("권장 폴더: 내부 저장소/Documents/LoopMuse", style = MaterialTheme.typography.bodySmall)
                }
                Text("마지막 성공: ${formatBackupDate(lastBackup)}", style = MaterialTheme.typography.bodySmall)
                lastError?.let { Text("최근 오류: $it", color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { folderPicker.launch(null) }) {
                        Text(if (!manager.hasBackupFolder()) "폴더 선택" else "폴더 변경")
                    }
                    if (chosenFolder != null) {
                        Button(enabled = !busy, onClick = {
                            if (active != null) pendingUseFolder = true else useChosenFolder()
                        }) { Text("이 폴더 사용") }
                    } else if (manager.hasBackupFolder()) {
                        Button(enabled = !busy, onClick = { runTask {
                            val info = manager.backupNow()
                            val listError = runCatching { refresh() }.exceptionOrNull()
                            selected = info
                            message = if (listError == null) "현재 정보를 백업했습니다."
                                else "현재 정보를 백업했습니다. 목록 갱신 실패: ${listError.message ?: "알 수 없는 오류"}"
                        } }) { Text("지금 백업") }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("백업에서 복원", style = MaterialTheme.typography.titleLarge)
                if (active != null) {
                    Text(if (active.valid) "현재 자동 백업" else "현재 자동 백업 파일이 손상됨",
                        style = MaterialTheme.typography.labelLarge)
                    BackupSummary(active)
                    TextButton(onClick = { selected = active }, enabled = !busy) { Text("이 백업 선택") }
                } else Text("선택한 폴더에 현재 자동 백업 파일이 없습니다.")
                OutlinedButton(enabled = !busy, onClick = { filePicker.launch(arrayOf("application/json", "*/*")) }) {
                    Text("다른 백업 파일 선택")
                }
                selected?.let { info ->
                    HorizontalDivider()
                    Text("선택한 파일: ${info.name}", style = MaterialTheme.typography.titleSmall)
                    BackupSummary(info)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && info.valid && !info.legacy, onClick = { pendingRestore = RestoreMode.REPLACE }) {
                            Text("교체 복원")
                        }
                        OutlinedButton(enabled = !busy && info.valid, onClick = { pendingRestore = RestoreMode.MERGE }) {
                            Text("병합")
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("이전 백업 정리", style = MaterialTheme.typography.titleLarge)
                }
                Text("이전·중단된 파일 ${older.size}개 · 안전 복사본 ${allRecovery.size}개")
                Text("현재 자동 백업 파일은 정리 대상에서 제외됩니다. 삭제할 파일을 확인한 뒤 정리하세요.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showOlder = !showOlder }) {
                    Text(if (showOlder) "목록 접기" else "파일 목록 보기")
                }
                if (showOlder) {
                    if (bulkCandidates.isNotEmpty()) {
                        OutlinedButton(enabled = !busy, onClick = { pendingBulkDelete = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Text("이전·중단 파일 ${bulkCandidates.size}개 정리")
                        }
                    }
                    (older + allRecovery).forEach { info ->
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable(enabled = !busy) { selected = info }.padding(vertical = 6.dp)) {
                                Text(info.name, style = MaterialTheme.typography.bodyMedium)
                                Text("${if (info.valid) kindLabel(info.kind) else "손상·미완료"} · ${formatBackupDate(info.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(enabled = !busy, onClick = { pendingDelete = info }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "${info.name} 삭제")
                            }
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("새로 시작", style = MaterialTheme.typography.titleMedium)
                Text("현재 앱 정보만 비웁니다. 외부 백업은 보관됩니다.", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = { pendingFresh = true }) { Text("현재 정보 초기화") }
            }
        }
        if (busy) CircularProgressIndicator()
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(12.dp))
    }

    if (pendingUseFolder) {
        AlertDialog(onDismissRequest = { pendingUseFolder = false },
            title = { Text("이 폴더를 자동 백업에 사용할까요?") },
            text = { Text("이 폴더에 이미 ${BackupManager.CURRENT_FILE} 파일이 있습니다. 현재 앱 정보를 저장하면 이 파일이 바뀝니다. 정상적인 기존 파일은 LoopMuse_Recovery 폴더에 한 번 더 보관합니다. 먼저 이전 정보를 사용하려면 취소하고 교체 복원 또는 병합을 선택하세요.") },
            confirmButton = { TextButton(onClick = { pendingUseFolder = false; useChosenFolder() }) { Text("현재 정보 저장") } },
            dismissButton = { TextButton(onClick = { pendingUseFolder = false }) { Text("취소") } })
    }

    pendingRestore?.let { mode ->
        AlertDialog(onDismissRequest = { pendingRestore = null },
            title = { Text(if (mode == RestoreMode.REPLACE) "현재 정보를 교체할까요?" else "백업을 병합할까요?") },
            text = { Text(if (mode == RestoreMode.REPLACE)
                "복원 전에 현재 정보의 안전 복사본을 보관합니다. 이후 백업 파일의 내용으로 교체합니다."
                else "곡 정보는 최근 수정본을 우선하고 재생목록·선택 폴더는 합칩니다.") },
            confirmButton = { TextButton(onClick = {
                val target = selected ?: return@TextButton
                pendingRestore = null
                runTask {
                    onRestore(target.uri, mode)
                    finishDataChange("복원이 완료되었습니다.")
                }
            }) { Text(if (mode == RestoreMode.REPLACE) "교체" else "병합") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("취소") } })
    }

    pendingDelete?.let { info ->
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text("백업 파일을 삭제할까요?") },
            text = { Text("${info.name}\n삭제한 백업은 복원할 수 없습니다.") },
            confirmButton = { TextButton(onClick = {
                pendingDelete = null
                runTask {
                    check(manager.deleteBackup(info, chosenFolder)) { "파일을 삭제하지 못했습니다." }
                    refresh()
                    message = "백업 파일을 삭제했습니다."
                }
            }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } })
    }

    if (pendingBulkDelete) {
        AlertDialog(onDismissRequest = { pendingBulkDelete = false },
            title = { Text("이전·중단 파일을 정리할까요?") },
            text = { Text("이전 자동 백업과 중단된 백업 ${bulkCandidates.size}개를 삭제합니다. 현재 자동 백업과 안전 복사본은 유지합니다.") },
            confirmButton = { TextButton(onClick = {
                pendingBulkDelete = false
                runTask {
                    val deleted = bulkCandidates.count { manager.deleteBackup(it, chosenFolder) }
                    refresh()
                    message = "이전·중단 파일 ${deleted}개를 정리했습니다."
                }
            }) { Text("정리") } },
            dismissButton = { TextButton(onClick = { pendingBulkDelete = false }) { Text("취소") } })
    }

    if (pendingFresh) {
        AlertDialog(onDismissRequest = { pendingFresh = false },
            title = { Text("현재 정보를 비울까요?") },
            text = { Text("현재 곡 정보·재생목록·알람·선택 폴더·재생 기록을 비웁니다. 먼저 안전 복사본을 남깁니다.") },
            confirmButton = { TextButton(onClick = {
                pendingFresh = false
                runTask {
                    onFresh()
                    finishDataChange("현재 정보를 비웠습니다.")
                }
            }) { Text("새로 시작") } },
            dismissButton = { TextButton(onClick = { pendingFresh = false }) { Text("취소") } })
    }
}

@Composable
private fun BackupSummary(info: BackupInfo) {
    Text(if (info.valid) "${formatBackupDate(info.createdAt)} · 곡 ${info.songCount}개 · 재생목록 ${info.playlistCount}개 · 알람 ${info.alarmCount}개"
        else "파일을 읽거나 검증하지 못했습니다. 복원할 수 없습니다.",
        style = MaterialTheme.typography.bodySmall)
}

private fun formatBackupDate(time: Long): String = if (time <= 0) "날짜 정보 없음" else
    SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(time))

private fun kindLabel(kind: BackupKind): String = when (kind) {
    BackupKind.CURRENT -> "현재 자동 백업"
    BackupKind.OLDER -> "이전 자동 백업"
    BackupKind.RECOVERY -> "안전 복사본"
    BackupKind.PENDING -> "중단된 백업"
    BackupKind.LEGACY -> "예전 형식"
}
