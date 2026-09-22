package com.example.loopmuse.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.loopmuse.data.db.SongMetaDao
import com.example.loopmuse.data.db.SongMetaEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BackupManager(private val context: Context, private val dao: SongMetaDao) {

    private val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val BACKUP_FILE_NAME = "loopmuse_backup.json"

    suspend fun createBackup(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // Take persistable permission so we can write again later if needed
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)

            val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            var backupFile = documentFile.findFile(BACKUP_FILE_NAME)
            
            if (backupFile == null) {
                backupFile = documentFile.createFile("application/json", BACKUP_FILE_NAME)
            }
            
            backupFile?.let { file ->
                val allData = dao.getAllMetadata().first()
                val json = gson.toJson(allData)
                
                context.contentResolver.openOutputStream(file.uri, "wt")?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }

    suspend fun restoreDatabase(fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                val json = inputStream.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<SongMetaEntity>>() {}.type
                val backupData: List<SongMetaEntity> = gson.fromJson(json, type) ?: return@withContext false
                
                // --- Merge Logic ---
                backupData.forEach { backupMeta ->
                    val currentMeta = dao.getMetadataById(backupMeta.fingerprintId)
                    if (currentMeta == null) {
                        // Not in current DB, insert it
                        dao.insertOrUpdate(backupMeta)
                    } else {
                        // Conflict: Choose the one that was updated more recently
                        if (backupMeta.lastUpdated > currentMeta.lastUpdated) {
                            dao.insertOrUpdate(backupMeta)
                        }
                    }
                }
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }
}