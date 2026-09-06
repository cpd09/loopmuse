package com.example.loopmuse.service

import android.content.Context
import android.os.Environment
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.SelectionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MusicScanner(private val context: Context) {
    
    private val supportedFormats = listOf("mp3", "m4a", "wav", "flac", "ogg")
    
    suspend fun scanMusicFiles(selectedItems: List<SelectionItem> = emptyList()): List<MusicFile> = withContext(Dispatchers.IO) {
        val musicFiles = mutableListOf<MusicFile>()
        
        if (selectedItems.isEmpty()) {
            getDefaultMusicDirectories().forEach { folder ->
                scanDirectory(folder, musicFiles, true)
            }
        } else {
            selectedItems.forEach { item ->
                val file = File(item.path)
                if (file.exists()) {
                    if (item.isFolder && file.isDirectory) {
                        scanDirectory(file, musicFiles, item.includeSubfolders)
                    } else if (!item.isFolder && file.isFile && isSupportedAudioFile(file)) {
                        musicFiles.add(MusicFile.fromFile(file))
                    }
                }
            }
        }
        
        // Remove duplicates if any (e.g. file selected both individually and via folder)
        musicFiles.distinctBy { it.path }
    }
    
    private fun scanDirectory(directory: File, musicFiles: MutableList<MusicFile>, includeSubfolders: Boolean) {
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory && includeSubfolders -> scanDirectory(file, musicFiles, true)
                file.isFile && isSupportedAudioFile(file) -> {
                    musicFiles.add(MusicFile.fromFile(file))
                }
            }
        }
    }
    
    private fun isSupportedAudioFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return supportedFormats.contains(extension)
    }
    
    private fun getDefaultMusicDirectories(): List<File> {
        val directories = mutableListOf<File>()
        
        // External storage music directory
        val externalMusicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (externalMusicDir.exists()) {
            directories.add(externalMusicDir)
        }
        
        // Downloads directory (common place for music files)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir.exists()) {
            directories.add(downloadsDir)
        }
        
        return directories
    }
    
    fun getRecommendedFolders(): List<File> {
        return getAvailableFolders()
    }

    fun getRootDirectories(): List<File> {
        return getDefaultMusicDirectories()
    }

    fun getAvailableFolders(): List<File> {
        val folders = mutableListOf<File>()
        val rootDirs = getDefaultMusicDirectories()
        
        rootDirs.forEach { rootDir ->
            collectMusicFolders(rootDir, folders)
        }
        
        return folders
    }
    
    private fun collectMusicFolders(directory: File, folders: MutableList<File>) {
        if (!directory.exists() || !directory.isDirectory) return
        
        var hasAudioFiles = false
        val subDirectories = mutableListOf<File>()
        
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> subDirectories.add(file)
                file.isFile && isSupportedAudioFile(file) -> hasAudioFiles = true
            }
        }
        
        if (hasAudioFiles) {
            folders.add(directory)
        }
        
        subDirectories.forEach { subDir ->
            collectMusicFolders(subDir, folders)
        }
    }
}