package com.example.loopmuse.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import com.example.loopmuse.service.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class BrowserFolder(val file: DocumentFile, val path: String)

@Composable
fun BackupFileBrowserDialog(manager: BackupManager, onSelected: (Uri) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var folders by remember { mutableStateOf<List<BrowserFolder>>(emptyList()) }
    var stack by remember { mutableStateOf<List<BrowserFolder>>(emptyList()) }
    var entries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = stack.lastOrNull()

    val phonePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onSelected(uri)
    }

    LaunchedEffect(current) {
        loading = true
        error = null
        try {
            if (current == null) {
                folders = withContext(Dispatchers.IO) {
                    context.contentResolver.persistedUriPermissions.asSequence()
                        .filter { it.isReadPermission }
                        .mapNotNull { permission ->
                            DocumentFile.fromTreeUri(context, permission.uri)?.takeIf { it.isDirectory }
                                ?.let { BrowserFolder(it, manager.displayFolderPath(permission.uri)) }
                        }
                        .distinctBy { it.file.uri }
                        .sortedBy { it.path }
                        .toList()
                }
                entries = emptyList()
            } else {
                entries = withContext(Dispatchers.IO) {
                    current.file.listFiles().asSequence()
                        .filter { it.isDirectory || it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
                        .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }
                            .thenBy { it.name?.lowercase() })
                        .toList()
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "폴더를 열 수 없습니다."
            entries = emptyList()
        } finally {
            loading = false
        }
    }

    fun goBack() {
        if (stack.isEmpty()) onClose() else stack = stack.dropLast(1)
    }

    Dialog(onDismissRequest = ::goBack) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "이전 폴더 또는 백업 및 복원으로")
                    }
                    Text("백업파일 찾아보기", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "백업 및 복원으로 돌아가기")
                    }
                }
                Text(current?.path ?: "접근 허용된 폴더", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (loading) CircularProgressIndicator()
                    else if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                    else if (current == null && folders.isEmpty()) Text("접근 허용된 폴더가 없습니다.")
                    else if (current != null && entries.isEmpty()) Text("이 폴더에 백업파일이 없습니다.")

                    if (current == null) folders.forEach { folder ->
                        BrowserEntry(Icons.Default.Folder, folder.path) { stack = listOf(folder) }
                    } else entries.forEach { file ->
                        val path = manager.displayFilePath(file.uri).takeIf { file.uri.authority ==
                            "com.android.externalstorage.documents" } ?: "${current.path}/${file.name}"
                        BrowserEntry(if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            path) {
                            if (file.isDirectory) stack = stack + BrowserFolder(file, path)
                            else onSelected(file.uri)
                        }
                    }
                }
                AppOutlinedButton(onClick = { phonePicker.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("휴대폰 파일 선택기 열기")
                }
            }
        }
    }
}

@Composable
private fun BrowserEntry(icon: androidx.compose.ui.graphics.vector.ImageVector, path: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(path, style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}
