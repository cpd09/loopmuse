package com.example.loopmuse.ui

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.loopmuse.service.BackupFolderCandidate
import com.example.loopmuse.service.BackupKind
import com.example.loopmuse.service.BackupManager
import com.example.loopmuse.service.RestoreMode
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
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var unreadableFolder by remember { mutableStateOf<BackupFolderCandidate?>(null) }

    fun leave() {
        manager.cancelBackupFolderSetup()
        onLater()
    }

    fun activate(folder: BackupFolderCandidate) {
        scope.launch {
            busy = true
            error = null
            try {
                manager.activateBackupFolder(folder)
                onFinish()
            } catch (e: Exception) {
                error = e.message ?: "자동 보관을 시작하지 못했습니다."
            } finally {
                busy = false
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            busy = false
            error = "자동 보관을 시작하려면 다음 화면에서 '이 폴더 사용'을 눌러주세요."
        } else scope.launch {
            busy = true
            error = null
            unreadableFolder = null
            try {
                val folder = manager.prepareBackupFolder(uri, recommended = true)
                val valid = folder.backups.filter { it.valid }
                if (valid.isEmpty() && folder.backups.isNotEmpty()) {
                    unreadableFolder = folder
                    error = "이전 보관 파일을 읽을 수 없습니다. 파일은 그대로 두었습니다."
                } else {
                    val previous = valid.filter { it.kind == BackupKind.CURRENT }.maxByOrNull { it.createdAt }
                        ?: valid.maxByOrNull { it.createdAt }
                    previous?.let { backup ->
                        manager.recommendedRestoreMode(backup)?.let { mode -> onRestore(backup.uri, mode) }
                    }
                    manager.activateBackupFolder(folder)
                    onFinish()
                }
            } catch (e: Exception) {
                error = e.message ?: "자동 보관을 시작하지 못했습니다."
            } finally {
                busy = false
            }
        }
    }

    fun openRecommendedFolder() {
        busy = true
        error = null
        unreadableFolder = null
        Toast.makeText(context, "화면 아래 '이 폴더 사용'을 눌러주세요.", Toast.LENGTH_LONG).show()
        val initial = runCatching {
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
        }.getOrNull()
        runCatching { folderPicker.launch(initial) }.onFailure {
            busy = false
            error = "폴더 허용 화면을 열지 못했습니다. 다시 시도해 주세요."
        }
    }

    LaunchedEffect(Unit) { openRecommendedFolder() }

    Dialog(onDismissRequest = { if (!busy) leave() }) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("데이터 자동 보관", style = MaterialTheme.typography.titleLarge)
                Text("다음 화면에서 '이 폴더 사용'을 눌러주세요. 이전 내용이 있으면 앱이 자동으로 가져옵니다.")
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!busy) {
                    unreadableFolder?.let { folder ->
                        TextButton(onClick = { activate(folder) }) { Text("새 데이터로 시작") }
                    }
                    AppButton(onClick = ::openRecommendedFolder) { Text("다시 허용") }
                    TextButton(onClick = ::leave) { Text("나중에") }
                }
            }
        }
    }
}
