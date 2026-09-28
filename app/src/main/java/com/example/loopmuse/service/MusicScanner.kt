package com.example.loopmuse.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import com.example.loopmuse.data.MusicFile
import com.example.loopmuse.data.SelectionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import java.io.File

class MusicScanner(private val context: Context) {
    
    private val supportedFormats = listOf("mp3", "m4a", "wav", "flac", "ogg")
    private val metadataPrefs = context.getSharedPreferences("song_tag_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    private data class CachedTags(
        val size: Long,
        val modified: Long,
        val title: String?,
        val artist: String?,
        val album: String?,
        val genre: String?,
        val durationMs: Long
    )
    
    suspend fun scanMusicFiles(selectedItems: List<SelectionItem> = emptyList()): List<MusicFile> = withContext(Dispatchers.IO) {
        val musicFiles = mutableListOf<MusicFile>()
        val newCacheEntries = mutableMapOf<String, String>()
        val addFile: (File) -> Unit = { file -> musicFiles.add(readMusicFile(file, newCacheEntries)) }
        
        if (selectedItems.isEmpty()) {
            getDefaultMusicDirectories().forEach { folder ->
                scanDirectory(folder, true, addFile)
            }
        } else {
            selectedItems.forEach { item ->
                val file = File(item.path)
                if (file.exists()) {
                    if (item.isFolder && file.isDirectory) {
                        scanDirectory(file, item.includeSubfolders, addFile)
                    } else if (!item.isFolder && file.isFile && isSupportedAudioFile(file)) {
                        addFile(file)
                    }
                }
            }
        }
        if (newCacheEntries.isNotEmpty()) {
            metadataPrefs.edit().apply {
                newCacheEntries.forEach { (path, json) -> putString(path, json) }
            }.apply()
        }
        
        // Remove duplicates if any (e.g. file selected both individually and via folder)
        musicFiles.distinctBy { it.path }
    }
    
    private fun readMusicFile(file: File, newCacheEntries: MutableMap<String, String>): MusicFile {
        val path = file.absolutePath
        val size = file.length()
        val modified = file.lastModified()
        val cached = runCatching {
            metadataPrefs.getString(path, null)?.let { gson.fromJson(it, CachedTags::class.java) }
        }.getOrNull()
        val tags = if (cached?.size == size && cached.modified == modified) cached else {
            val retriever = MediaMetadataRetriever()
            val read = try {
                retriever.setDataSource(path)
                CachedTags(size, modified,
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
            } catch (_: Exception) {
                CachedTags(size, modified, null, null, null, null, 0L)
            } finally {
                retriever.release()
            }
            newCacheEntries[path] = gson.toJson(read)
            read
        }
        return MusicFile.fromFile(file, tags.title, tags.artist, tags.album, tags.genre, tags.durationMs)
    }

    private fun scanDirectory(directory: File, includeSubfolders: Boolean, addFile: (File) -> Unit) {
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory && includeSubfolders -> scanDirectory(file, true, addFile)
                file.isFile && isSupportedAudioFile(file) -> {
                    addFile(file)
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
