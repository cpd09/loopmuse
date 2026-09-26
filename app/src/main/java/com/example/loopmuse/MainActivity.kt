package com.example.loopmuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.loopmuse.service.BackupManager
import com.example.loopmuse.ui.HomeScreen
import com.example.loopmuse.ui.DataManagementDialog
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val marker = remember { File(noBackupFilesDir, "data_choice_v1") }
            var needsDataChoice by remember { mutableStateOf(!marker.exists()) }
            if (needsDataChoice) {
                DataManagementDialog(
                    firstRun = true,
                    onFinish = {
                        marker.writeText("chosen")
                        needsDataChoice = false
                    },
                    onRestore = { uri, mode -> BackupManager(applicationContext).restore(uri, mode) },
                    onFresh = { BackupManager(applicationContext).startFresh() }
                )
            } else {
                HomeScreen()
            }
        }
    }
}
