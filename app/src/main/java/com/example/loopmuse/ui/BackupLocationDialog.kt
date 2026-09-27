package com.example.loopmuse.ui

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.example.loopmuse.service.BackupFolderCandidate
import com.example.loopmuse.service.BackupManager
import kotlinx.coroutines.launch

@Composable
fun BackupLocationDialog(manager: BackupManager, onSaved: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var candidate by remember { mutableStateOf<BackupFolderCandidate?>(null) }
    var recommended by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            try { candidate = manager.prepareBackupFolder(uri, recommended, scanBackups = false) }
            catch (e: Exception) { error = e.message ?: "경로를 선택하지 못했습니다." }
            finally { busy = false }
        }
    }

    fun chooseFolder(useRecommended: Boolean) {
        recommended = useRecommended
        val initial = if (useRecommended) runCatching {
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
        }.getOrNull() else null
        folderPicker.launch(initial)
    }

    fun cancel() {
        manager.cancelBackupFolderSetup()
        onCancel()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) cancel() },
        title = { Text("백업파일 경로 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("저장할 경로", style = MaterialTheme.typography.labelMedium)
                val location = candidate?.path ?: manager.backupFolderPath()
                Text(location?.let { "$it/${BackupManager.CURRENT_FILE}" } ?: "경로를 선택해 주세요")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { chooseFolder(true) }) { Text("권장 경로") }
                    OutlinedButton(enabled = !busy, onClick = { chooseFolder(false) }) { Text("다른 경로") }
                }
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(enabled = !busy && candidate != null, onClick = {
            val chosen = candidate ?: return@Button
            scope.launch {
                busy = true
                error = null
                try {
                    manager.saveBackupLocation(chosen)
                    onSaved()
                } catch (e: Exception) {
                    error = e.message ?: "백업 경로를 저장하지 못했습니다."
                } finally { busy = false }
            }
        }) { Text("저장") } },
        dismissButton = { TextButton(enabled = !busy, onClick = ::cancel) { Text("취소") } }
    )
}
