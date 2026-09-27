package com.example.loopmuse.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.loopmuse.service.BackupFolderCandidate
import com.example.loopmuse.service.BackupInfo
import com.example.loopmuse.service.BackupKind
import com.example.loopmuse.service.BackupManager
import com.example.loopmuse.service.LocalDataInfo
import com.example.loopmuse.service.RestoreMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun BackupSetupDialog(
    onFinish: () -> Unit,
    onLater: () -> Unit,
    onRestore: suspend (Uri, RestoreMode) -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { BackupManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var candidate by remember { mutableStateOf<BackupFolderCandidate?>(null) }
    var selected by remember { mutableStateOf<BackupInfo?>(null) }
    var local by remember { mutableStateOf<LocalDataInfo?>(null) }
    var matchesCurrent by remember { mutableStateOf<Boolean?>(null) }
    var comparisonFailed by remember { mutableStateOf(false) }
    var recommendedPicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<RestoreMode?>(null) }

    LaunchedEffect(Unit) { local = runCatching { manager.localSummary() }.getOrNull() }
    LaunchedEffect(selected?.uri, local) {
        matchesCurrent = null
        comparisonFailed = false
        matchesCurrent = selected?.takeIf { it.valid && !it.legacy }?.let {
            runCatching { manager.matchesCurrentData(it.uri) }
                .onFailure { comparisonFailed = true }.getOrNull()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            try {
                val found = manager.prepareBackupFolder(uri, recommendedPicker)
                candidate = found
                selected = found.backups.firstOrNull { it.kind == BackupKind.CURRENT && it.valid }
                    ?: found.backups.filter { it.valid }.maxByOrNull { it.createdAt }
            } catch (e: Exception) {
                error = e.message ?: "폴더를 확인하지 못했습니다."
            } finally { busy = false }
        }
    }

    fun chooseFolder(recommended: Boolean) {
        recommendedPicker = recommended
        val initial = if (recommended) runCatching {
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
        }.getOrNull() else null
        folderPicker.launch(initial)
    }

    fun cancelSetup() {
        manager.cancelBackupFolderSetup()
        onLater()
    }

    fun complete(mode: RestoreMode?) {
        val folder = candidate ?: return
        val file = selected
        scope.launch {
            busy = true
            error = null
            var restored = false
            try {
                if (mode != null) {
                    require(file?.valid == true) { "복원할 백업을 선택해 주세요." }
                    onRestore(file.uri, mode)
                    restored = true
                }
                manager.activateBackupFolder(folder)
                onFinish()
            } catch (e: Exception) {
                error = if (restored) "복원은 완료됐지만 자동 백업 설정에 실패했습니다. ${e.message ?: "다시 시도해 주세요."}"
                    else e.message ?: "작업을 완료하지 못했습니다."
                local = runCatching { manager.localSummary() }.getOrNull()
            } finally { busy = false }
        }
    }

    val folder = candidate
    Dialog(onDismissRequest = { if (!busy) cancelSetup() }) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("데이터 자동 보관", style = MaterialTheme.typography.titleLarge)
                    TextButton(enabled = !busy, onClick = ::cancelSetup) { Text("나중에") }
                }
                if (folder == null) {
                    Text("앱을 삭제해도 남는 폴더에 데이터를 보관합니다. 한 번만 폴더 접근을 허용하면 이후에는 자동으로 저장합니다.")
                    Text("권장 위치: 내부 저장소/Documents/LoopMuse", style = MaterialTheme.typography.bodyMedium)
                    Button(enabled = !busy, onClick = { chooseFolder(true) }) { Text("권장 위치 선택") }
                    OutlinedButton(enabled = !busy, onClick = { chooseFolder(false) }) { Text("다른 폴더 선택") }
                    Text("음악 파일 자체는 백업되지 않습니다.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("백업 저장 위치", style = MaterialTheme.typography.titleSmall)
                    Text("${folder.path}/${BackupManager.CURRENT_FILE}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(enabled = !busy, onClick = { candidate = null; selected = null }) {
                        Text("위치 변경")
                    }
                    local?.let {
                        Text("현재 앱 정보: 곡 정보 ${it.songs}개 · 재생목록 ${it.playlists}개 · 알람 ${it.alarms}개",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    val available = folder.backups.filter { it.valid }
                    if (available.isEmpty()) {
                        Text(if (folder.backups.isEmpty()) "이 위치에는 이전 백업이 없습니다."
                            else "이 위치의 기존 파일은 읽을 수 없습니다. 파일은 별도로 보관한 뒤 새 백업을 시작합니다.")
                        Button(enabled = !busy, onClick = { complete(null) }) { Text("저장") }
                    } else {
                        Text("이전 백업을 찾았습니다", style = MaterialTheme.typography.titleSmall)
                        Text(when {
                            matchesCurrent == true -> "현재 앱과 백업 내용이 같습니다. 복원할 필요가 없습니다."
                            local != null && local!!.songs == 0 && local!!.alarms == 0 && local!!.playlists <= 1 ->
                                "현재 앱 정보가 거의 비어 있습니다. 이전 내용을 사용하려면 복원하세요."
                            matchesCurrent == false -> "현재 내용도 남기려면 병합을 선택하세요. 현재 정보로 계속해도 이전 파일은 보관됩니다."
                            comparisonFailed || selected?.legacy == true -> "내용을 자동 비교할 수 없습니다. 날짜와 개수를 확인한 뒤 선택하세요."
                            else -> "현재 앱과 백업을 비교 중입니다. 날짜와 개수를 확인한 뒤 선택하세요."
                        }, style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.fillMaxWidth().heightIn(max = 165.dp).verticalScroll(rememberScrollState())) {
                            available.forEach { info ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selected?.uri == info.uri, onClick = { selected = info }, enabled = !busy)
                                    Column {
                                        Text(backupDate(info.createdAt), style = MaterialTheme.typography.bodyMedium)
                                        Text("곡 정보 ${info.songCount}개 · 재생목록 ${info.playlistCount}개 · 알람 ${info.alarmCount}개",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(enabled = !busy && matchesCurrent != true && selected?.legacy == false,
                                onClick = { confirmRestore = RestoreMode.REPLACE }) { Text("복원") }
                            OutlinedButton(enabled = !busy && matchesCurrent != true,
                                onClick = { confirmRestore = RestoreMode.MERGE }) { Text("병합") }
                        }
                        TextButton(enabled = !busy, onClick = { complete(null) }) { Text("현재 내용 그대로 사용") }
                    }
                }
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    confirmRestore?.let { mode ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(if (mode == RestoreMode.REPLACE) "이전 내용으로 복원할까요?" else "현재 내용과 병합할까요?") },
            text = { Text(if (mode == RestoreMode.REPLACE)
                "현재 앱 정보는 안전 복사본으로 남긴 뒤 선택한 백업 내용으로 바뀝니다."
                else "현재 정보와 선택한 백업을 합칩니다. 중복 곡 정보는 더 최근에 수정한 내용을 사용합니다.") },
            confirmButton = { TextButton(onClick = { confirmRestore = null; complete(mode) }) {
                Text(if (mode == RestoreMode.REPLACE) "복원" else "병합")
            } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("취소") } }
        )
    }
}

private fun backupDate(time: Long): String = if (time <= 0) "날짜 정보 없음"
    else SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(time))
