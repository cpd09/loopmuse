package com.example.loopmuse.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.example.loopmuse.data.SelectionItem
import com.example.loopmuse.data.db.AlarmEntity
import com.example.loopmuse.data.db.AppDatabase
import com.example.loopmuse.data.db.SongMetaEntity
import com.example.loopmuse.service.alarm.AlarmScheduler
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class RestoreMode { REPLACE, MERGE }
enum class BackupKind { CURRENT, OLDER, RECOVERY, PENDING, LEGACY }

data class BackupInfo(
    val uri: Uri,
    val name: String,
    val createdAt: Long,
    val songCount: Int,
    val alarmCount: Int,
    val playlistCount: Int,
    val legacy: Boolean,
    val kind: BackupKind,
    val valid: Boolean = true
)

data class LocalDataInfo(val songs: Int, val playlists: Int, val alarms: Int)

/** Internal Room remains the live store. This class exports verified, versioned snapshots. */
class BackupManager(private val context: Context, private val database: AppDatabase = AppDatabase.getDatabase(context)) {
    companion object {
        private val operationMutex = Mutex()
        const val CURRENT_FILE = "loopmuse_current.json"
        const val RECOVERY_FOLDER = "LoopMuse_Recovery"
        private const val PREVIOUS_FILE = "loopmuse_previous.json"
    }

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    private val mutex = operationMutex
    private var watcherJobs = listOf<Job>()
    private var listeners = listOf<Pair<SharedPreferences, SharedPreferences.OnSharedPreferenceChangeListener>>()

    private data class Payload(
        val songs: List<SongMetaEntity>,
        val alarms: List<AlarmEntity>,
        val selectedItems: List<SelectionItem>,
        val playlistsJson: String,
        val currentPlaylistId: String,
        val playedSongIds: List<String>,
        val lastPlayed: Map<String, Long>
    )

    private data class Envelope(val format: String, val version: Int, val createdAt: Long, val payload: String, val sha256: String)
    private data class Decoded(val payload: Payload, val createdAt: Long, val legacy: Boolean)

    fun hasBackupFolder(): Boolean = savedFolderUri() != null
    fun backupFolderPath(): String? = prefs.getString("folder_label", null)
        ?: prefs.getString("folder_uri", null)?.let { displayFolderPath(Uri.parse(it)) }
    suspend fun listConfiguredBackups(): List<BackupInfo> = savedFolderUri()?.let { listBackups(it) } ?: emptyList()
    fun lastBackupTime(): Long = prefs.getLong("last_success", 0L)
    fun lastError(): String? = prefs.getString("last_error", null)
    fun lastAlarmWarning(): String? = prefs.getString("alarm_warning", null)
    suspend fun localSummary(): LocalDataInfo = withContext(Dispatchers.IO) {
        LocalDataInfo(database.songMetaDao().getAllMetadata().first().size,
            playlistObject(context.getSharedPreferences("playback_queue_v5", Context.MODE_PRIVATE)
                .getString("playlists", "{}") ?: "{}").entrySet().size,
            database.alarmDao().getAllAlarmsOnce().size)
    }

    private fun savedFolderUri(): Uri? {
        val uri = prefs.getString("folder_uri", null)?.let(Uri::parse) ?: return null
        return uri.takeIf { candidate -> context.contentResolver.persistedUriPermissions.any {
            it.uri == candidate && it.isReadPermission && it.isWritePermission
        } }
    }

    fun displayFolderPath(uri: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        if (uri.authority == "com.android.externalstorage.documents" && documentId != null) {
            val volume = documentId.substringBefore(':')
            val relative = documentId.substringAfter(':', "").trim('/')
            val root = if (volume == "primary") "내부 저장소" else "외장 저장소 ($volume)"
            return if (relative.isEmpty()) root else "$root/$relative"
        }
        val provider = runCatching {
            val info = context.packageManager.resolveContentProvider(uri.authority ?: "", 0)
            info?.loadLabel(context.packageManager)?.toString()
        }.getOrNull() ?: "선택한 저장소"
        val folderName = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        return if (folderName.isNullOrBlank()) provider else "$provider / $folderName"
    }

    suspend fun listBackups(treeUri: Uri): List<BackupInfo> = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, treeUri) ?: error("선택한 폴더를 열 수 없습니다.")
        folder.listFiles().asSequence()
            .filter { it.isFile && (it.name?.startsWith("loopmuse-") == true || it.name?.startsWith("loopmuse_pending-") == true || it.name == "loopmuse_backup.json" || it.name == CURRENT_FILE) && it.name?.endsWith(".json") == true }
            .map { file -> inspectOrInvalid(file) }
            .sortedWith(compareByDescending<BackupInfo> { it.kind == BackupKind.CURRENT }.thenByDescending { it.createdAt }).toList()
    }

    suspend fun listRecoveryBackups(treeUri: Uri): List<BackupInfo> = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        folder.findFile(RECOVERY_FOLDER)?.takeIf { it.isDirectory }?.listFiles().orEmpty().asSequence()
            .filter { it.isFile && it.name?.endsWith(".json") == true }
            .map { file -> inspectOrInvalid(file).copy(kind = BackupKind.RECOVERY) }
            .sortedByDescending { it.createdAt }.toList()
    }

    suspend fun listConfiguredRecoveryBackups(): List<BackupInfo> = savedFolderUri()?.let { listRecoveryBackups(it) } ?: emptyList()

    suspend fun inspect(uri: Uri): BackupInfo = withContext(Dispatchers.IO) {
        inspectInternal(uri, DocumentFile.fromSingleUri(context, uri)?.name ?: "백업 파일")
    }

    suspend fun listLocalSafetyCopies(): List<BackupInfo> = withContext(Dispatchers.IO) {
        context.filesDir.listFiles().orEmpty().asSequence()
            .filter { it.name.startsWith("loopmuse-before-") && it.name.endsWith(".json") }
            .map { file -> runCatching { inspectInternal(Uri.fromFile(file), file.name) }.getOrElse {
                BackupInfo(Uri.fromFile(file), file.name, file.lastModified(), 0, 0, 0, false, BackupKind.RECOVERY, false)
            } }
            .sortedByDescending { it.createdAt }.toList()
    }

    suspend fun deleteBackup(info: BackupInfo, treeUri: Uri? = null): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(info.kind != BackupKind.CURRENT && info.name != CURRENT_FILE) { "현재 자동 백업 파일은 삭제할 수 없습니다." }
            if (info.uri.scheme == "file") {
                val file = File(info.uri.path ?: "")
                require(file.parentFile?.canonicalPath == context.filesDir.canonicalPath &&
                    file.name.startsWith("loopmuse-before-") && file.name.endsWith(".json")) { "앱 안전 복사본이 아닙니다." }
                return@withLock file.delete()
            }
            val folderUri = treeUri ?: savedFolderUri() ?: error("백업 폴더를 다시 선택해 주세요.")
            val folder = DocumentFile.fromTreeUri(context, folderUri) ?: error("백업 폴더를 열 수 없습니다.")
            val inRoot = folder.listFiles().firstOrNull { it.isFile && it.uri == info.uri &&
                it.name != CURRENT_FILE && it.name?.endsWith(".json") == true &&
                (it.name?.startsWith("loopmuse-") == true || it.name?.startsWith("loopmuse_pending-") == true || it.name == "loopmuse_backup.json") }
            val inRecovery = folder.findFile(RECOVERY_FOLDER)?.takeIf { it.isDirectory }?.listFiles()?.firstOrNull {
                it.isFile && it.uri == info.uri && it.name?.endsWith(".json") == true
            }
            val file = inRoot ?: inRecovery ?: error("선택한 폴더의 백업 파일이 아닙니다.")
            file.delete()
        }
    }

    private fun inspectOrInvalid(file: DocumentFile): BackupInfo =
        runCatching { inspectInternal(file.uri, file.name ?: "백업") }.getOrElse {
            val name = file.name ?: "백업"
            BackupInfo(file.uri, name, file.lastModified(), 0, 0, 0, false, kindForName(name), false)
        }

    private fun inspectInternal(uri: Uri, name: String): BackupInfo {
        val decoded = decode(readText(uri))
        val kind = if (decoded.legacy) BackupKind.LEGACY else kindForName(name)
        return BackupInfo(uri, name, decoded.createdAt, decoded.payload.songs.size,
            decoded.payload.alarms.size, playlistObject(decoded.payload.playlistsJson).entrySet().size, decoded.legacy, kind)
    }

    private fun kindForName(name: String): BackupKind = when {
        name == CURRENT_FILE -> BackupKind.CURRENT
        name == PREVIOUS_FILE || name.startsWith("loopmuse-before-") -> BackupKind.RECOVERY
        name.startsWith("loopmuse_pending-") -> BackupKind.PENDING
        name == "loopmuse_backup.json" -> BackupKind.LEGACY
        else -> BackupKind.OLDER
    }

    suspend fun selectFolderAndBackup(uri: Uri): BackupInfo = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val info = mutex.withLock { writeBackup(uri, "loopmuse", true) }
        check(prefs.edit().putString("folder_uri", uri.toString())
            .putString("folder_label", displayFolderPath(uri)).commit()) { "백업 폴더 설정을 저장하지 못했습니다." }
        info
    }

    suspend fun backupNow(): BackupInfo = withContext(Dispatchers.IO) {
        val uri = savedFolderUri() ?: error("백업 폴더를 다시 선택해 주세요.")
        mutex.withLock { writeBackup(uri, "loopmuse", true) }
    }

    private suspend fun writeBackup(treeUri: Uri, prefix: String, force: Boolean): BackupInfo {
        val folder = DocumentFile.fromTreeUri(context, treeUri) ?: error("백업 폴더를 열 수 없습니다.")
        val payloadJson = gson.toJson(capture())
        val digest = sha256(payloadJson)
        val time = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(time))
        val envelopeText = gson.toJson(Envelope("LoopMuse", 1, time, payloadJson, digest))
        if (prefix != "loopmuse") {
            val recovery = recoveryFolder(folder)
            val file = recovery.createFile("application/json", "$prefix-$stamp-${UUID.randomUUID().toString().take(8)}.json")
                ?: error("안전 복사본을 만들지 못했습니다.")
            return try { writeAndVerify(file, envelopeText, time) }
            catch (e: Exception) { file.delete(); throw e }
        }
        val existing = folder.findFile(CURRENT_FILE)?.takeIf { it.isFile }
        if (!force && digest == prefs.getString("last_digest", null) && existing != null) {
            runCatching { inspectInternal(existing.uri, CURRENT_FILE) }.getOrNull()?.let { return it }
        }

        // Verify a new copy before touching the currently usable backup.
        val pending = folder.createFile("application/json", "loopmuse_pending-$stamp-${UUID.randomUUID().toString().take(8)}.json")
            ?: error("임시 백업 파일을 만들지 못했습니다.")
        try { writeAndVerify(pending, envelopeText, time) }
        catch (e: Exception) { pending.delete(); throw e }

        // Keep one previous version in a separate folder while replacing the active file.
        existing?.let { current ->
            val old = runCatching { inspectInternal(current.uri, CURRENT_FILE) }.getOrNull()
            if (old != null) {
                val previous = recoveryFolder(folder).findFile(PREVIOUS_FILE)
                    ?: recoveryFolder(folder).createFile("application/json", PREVIOUS_FILE)
                    ?: error("이전 백업 보관 파일을 만들지 못했습니다.")
                writeAndVerify(previous, readText(current.uri), old.createdAt)
            }
        }

        val current = existing ?: folder.createFile("application/json", CURRENT_FILE)
            ?: error("현재 백업 파일을 만들지 못했습니다.")
        val result = writeAndVerify(current, envelopeText, time)
        pending.delete()
        check(prefs.edit().putLong("last_success", time).putString("last_digest", digest)
            .remove("last_error").commit()) { "백업 상태를 저장하지 못했습니다. 백업 파일은 보관됩니다." }
        return result
    }

    private fun recoveryFolder(parent: DocumentFile): DocumentFile {
        return parent.findFile(RECOVERY_FOLDER)?.takeIf { it.isDirectory }
            ?: parent.createDirectory(RECOVERY_FOLDER)
            ?: error("복구본 폴더를 만들지 못했습니다.")
    }

    private fun writeAndVerify(file: DocumentFile, value: String, createdAt: Long): BackupInfo {
        val stream = context.contentResolver.openOutputStream(file.uri, "wt") ?: error("백업 파일에 쓸 수 없습니다.")
        stream.use { it.write(value.toByteArray(Charsets.UTF_8)); it.flush() }
        val info = inspectInternal(file.uri, file.name ?: "백업")
        check(info.createdAt == createdAt) { "백업 파일 검증에 실패했습니다." }
        return info
    }

    suspend fun restore(uri: Uri, mode: RestoreMode) = withContext(Dispatchers.IO + NonCancellable) {
        mutex.withLock {
            val decoded = decode(readText(uri))
            require(!(decoded.legacy && mode == RestoreMode.REPLACE)) { "예전 형식 백업은 곡 정보 병합만 가능합니다." }
            val previous = capture()
            saveSafetyCopy("loopmuse-before-restore")
            try {
                if (mode == RestoreMode.REPLACE) replace(decoded.payload) else merge(decoded.payload, decoded.legacy)
                rescheduleAlarms(previous.alarms)
                prefs.edit().remove("last_digest").commit()
            } catch (failure: Exception) {
                try {
                    val changedAlarms = database.alarmDao().getAllAlarmsOnce()
                    replace(previous)
                    rescheduleAlarms(changedAlarms)
                }
                catch (rollback: Exception) { failure.addSuppressed(rollback) }
                throw failure
            }
        }
    }

    suspend fun startFresh() = withContext(Dispatchers.IO + NonCancellable) {
        mutex.withLock {
            val previous = capture()
            saveSafetyCopy("loopmuse-before-reset")
            try {
                database.withTransaction { database.songMetaDao().deleteAll(); database.alarmDao().deleteAll() }
                check(context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE).edit().clear().commit())
                check(context.getSharedPreferences("playback_queue_v5", Context.MODE_PRIVATE).edit().clear().commit())
                check(context.getSharedPreferences("playback_history", Context.MODE_PRIVATE).edit().clear().commit())
                rescheduleAlarms(previous.alarms)
                prefs.edit().remove("last_digest").commit()
            } catch (failure: Exception) {
                try { replace(previous); rescheduleAlarms(emptyList()) }
                catch (rollback: Exception) { failure.addSuppressed(rollback) }
                throw failure
            }
        }
    }

    private suspend fun saveSafetyCopy(prefix: String) {
        val folder = savedFolderUri()
        if (folder != null) {
            writeBackup(folder, prefix, true)
        } else {
            val payloadJson = gson.toJson(capture())
            val now = System.currentTimeMillis()
            val file = File(context.filesDir, "$prefix-$now.json")
            val temporary = File(context.filesDir, "${file.name}.tmp")
            temporary.writeText(gson.toJson(Envelope("LoopMuse", 1, now, payloadJson, sha256(payloadJson))), Charsets.UTF_8)
            check(temporary.renameTo(file)) { "안전 복사본을 저장하지 못했습니다." }
        }
    }

    private suspend fun capture(): Payload {
        val musicPrefs = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
        val queuePrefs = context.getSharedPreferences("playback_queue_v5", Context.MODE_PRIVATE)
        val historyPrefs = context.getSharedPreferences("playback_history", Context.MODE_PRIVATE)
        val selected: List<SelectionItem> = musicPrefs.getString("selected_items", null)?.let {
            gson.fromJson(it, object : TypeToken<List<SelectionItem>>() {}.type)
        } ?: emptyList()
        val lastPlayed = historyPrefs.all.mapNotNull { (key, value) ->
            if (key.startsWith("last_played_") && value is Long) key to value else null
        }.toMap()
        return Payload(database.songMetaDao().getAllMetadata().first(), database.alarmDao().getAllAlarms().first(),
            selected, queuePrefs.getString("playlists", "{}") ?: "{}",
            queuePrefs.getString("current_id", "ALL") ?: "ALL",
            historyPrefs.getStringSet("played_songs", emptySet())?.toList() ?: emptyList(), lastPlayed)
    }

    private fun decode(json: String): Decoded {
        val element = JsonParser.parseString(json)
        if (element.isJsonArray) {
            require(element.asJsonArray.all(::validSongJson)) { "곡 정보가 잘못되었습니다." }
            val songs: List<SongMetaEntity> = gson.fromJson(element, object : TypeToken<List<SongMetaEntity>>() {}.type)
            validateSongs(songs)
            return Decoded(Payload(songs, emptyList(), emptyList(), "{}", "ALL", emptyList(), emptyMap()), 0L, true)
        }
        require(element.isJsonObject) { "LoopMuse 백업 파일이 아닙니다." }
        val root = element.asJsonObject
        require(hasString(root, "format") && hasString(root, "payload") && hasString(root, "sha256") &&
            root.get("version")?.isJsonPrimitive == true && root.get("createdAt")?.isJsonPrimitive == true) {
            "지원하지 않는 백업 형식입니다."
        }
        val envelope = gson.fromJson(element, Envelope::class.java)
        require(envelope.format == "LoopMuse" && envelope.version == 1 && envelope.createdAt > 0) { "지원하지 않는 백업 형식입니다." }
        require(sha256(envelope.payload) == envelope.sha256) { "백업 파일이 손상되었습니다." }
        val content = JsonParser.parseString(envelope.payload)
        require(content.isJsonObject) { "백업 내용이 완전하지 않습니다." }
        val fields = content.asJsonObject
        require(fields.get("songs")?.isJsonArray == true && fields.get("alarms")?.isJsonArray == true &&
            fields.get("selectedItems")?.isJsonArray == true && hasString(fields, "playlistsJson") &&
            hasString(fields, "currentPlaylistId") && fields.get("playedSongIds")?.isJsonArray == true &&
            fields.get("lastPlayed")?.isJsonObject == true && fields.getAsJsonArray("songs").all(::validSongJson)) {
            "백업 내용이 완전하지 않습니다."
        }
        val payload = gson.fromJson(envelope.payload, Payload::class.java)
        validateSongs(payload.songs)
        require(payload.alarms.all { it.hour in 0..23 && it.minute in 0..59 && it.id > 0 && it.targetVolume in 0f..1f } &&
            payload.alarms.map { it.id }.toSet().size == payload.alarms.size) { "알람 정보가 잘못되었습니다." }
        require(payload.selectedItems.all { it.path.isNotBlank() } && payload.currentPlaylistId.isNotBlank()) { "음악 폴더 정보가 잘못되었습니다." }
        playlistObject(payload.playlistsJson)
        return Decoded(payload, envelope.createdAt, false)
    }

    private fun hasString(obj: JsonObject, key: String): Boolean = obj.get(key)?.let {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    } == true

    private fun validSongJson(element: JsonElement): Boolean = element.isJsonObject &&
        hasString(element.asJsonObject, "fingerprintId") &&
        hasString(element.asJsonObject, "title") && hasString(element.asJsonObject, "artist")

    private fun validateSongs(songs: List<SongMetaEntity>) {
        require(songs.all { it.fingerprintId.isNotBlank() }) { "곡 정보가 잘못되었습니다." }
        require(songs.map { it.fingerprintId }.toSet().size == songs.size) { "중복된 곡 정보가 있습니다." }
    }

    private fun playlistObject(json: String): JsonObject {
        val element = JsonParser.parseString(json)
        require(element.isJsonObject) { "재생목록 정보가 잘못되었습니다." }
        require(element.asJsonObject.entrySet().all { it.value.isJsonObject }) { "재생목록 정보가 잘못되었습니다." }
        return element.asJsonObject
    }

    private suspend fun replace(data: Payload) {
        database.withTransaction {
            database.songMetaDao().deleteAll(); database.alarmDao().deleteAll()
            data.songs.forEach { database.songMetaDao().insertOrUpdate(it) }
            data.alarms.forEach { database.alarmDao().insertAlarm(it) }
        }
        check(context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE).edit()
            .putString("selected_items", gson.toJson(data.selectedItems)).commit()) { "음악 폴더 정보를 저장하지 못했습니다." }
        check(context.getSharedPreferences("playback_queue_v5", Context.MODE_PRIVATE).edit()
            .putString("playlists", data.playlistsJson).putString("current_id", data.currentPlaylistId).commit()) { "재생목록을 저장하지 못했습니다." }
        writeHistory(data.playedSongIds.toSet(), data.lastPlayed)
    }

    private suspend fun merge(data: Payload, legacy: Boolean) {
        database.withTransaction {
            data.songs.forEach { incoming ->
                val current = database.songMetaDao().getMetadataById(incoming.fingerprintId)
                if (current == null || incoming.lastUpdated > current.lastUpdated) database.songMetaDao().insertOrUpdate(incoming)
            }
            if (!legacy) {
                val currentAlarms = database.alarmDao().getAllAlarmsOnce()
                val signatures = currentAlarms.map(::alarmSignature).toMutableSet()
                val ids = currentAlarms.map { it.id }.toMutableSet()
                data.alarms.forEach { alarm ->
                    if (signatures.add(alarmSignature(alarm))) {
                        val copy = if (alarm.id in ids) alarm.copy(id = 0) else alarm
                        ids.add(database.alarmDao().insertAlarm(copy).toInt())
                    }
                }
            }
        }
        if (legacy) return
        val musicPrefs = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
        val currentItems: List<SelectionItem> = musicPrefs.getString("selected_items", null)?.let {
            gson.fromJson(it, object : TypeToken<List<SelectionItem>>() {}.type)
        } ?: emptyList()
        check(musicPrefs.edit().putString("selected_items", gson.toJson((currentItems + data.selectedItems).distinct())).commit()) {
            "음악 폴더 정보를 저장하지 못했습니다."
        }
        val queuePrefs = context.getSharedPreferences("playback_queue_v5", Context.MODE_PRIVATE)
        val playlists = playlistObject(queuePrefs.getString("playlists", "{}") ?: "{}")
        val incoming = playlistObject(data.playlistsJson)
        incoming.entrySet().forEach { (id, item) ->
            if (!playlists.has(id)) playlists.add(id, item.deepCopy())
            else if (item.isJsonObject && playlists.get(id).isJsonObject) {
                val local = playlists.getAsJsonObject(id)
                val remote = item.asJsonObject
                listOf("queue", "originalQueue", "history").forEach { field ->
                    if (id != "ALL" || field == "history") local.add(field, unionJsonArray(local.get(field), remote.get(field)))
                }
            }
        }
        val currentId = queuePrefs.getString("current_id", "ALL") ?: "ALL"
        check(queuePrefs.edit().putString("playlists", gson.toJson(playlists))
            .putString("current_id", if (playlists.has(currentId)) currentId else data.currentPlaylistId).commit()) {
            "재생목록을 저장하지 못했습니다."
        }
        val historyPrefs = context.getSharedPreferences("playback_history", Context.MODE_PRIVATE)
        val played = (historyPrefs.getStringSet("played_songs", emptySet()) ?: emptySet()) + data.playedSongIds
        val lastPlayed = historyPrefs.all.mapNotNull { (key, value) ->
            if (key.startsWith("last_played_") && value is Long) key to value else null
        }.toMap().toMutableMap()
        data.lastPlayed.forEach { (key, time) -> lastPlayed[key] = maxOf(lastPlayed[key] ?: 0L, time) }
        writeHistory(played, lastPlayed)
    }

    private fun unionJsonArray(a: JsonElement?, b: JsonElement?): JsonArray {
        val result = JsonArray()
        val seen = mutableSetOf<String>()
        listOf(a, b).forEach { element -> if (element?.isJsonArray == true) element.asJsonArray.forEach { value ->
            if (value.isJsonPrimitive && seen.add(value.asString)) result.add(value.asString)
        } }
        return result
    }

    private fun writeHistory(played: Set<String>, lastPlayed: Map<String, Long>) {
        val editor = context.getSharedPreferences("playback_history", Context.MODE_PRIVATE).edit().clear().putStringSet("played_songs", played)
        lastPlayed.forEach { (key, value) -> if (key.startsWith("last_played_")) editor.putLong(key, value) }
        check(editor.commit()) { "재생 기록을 저장하지 못했습니다." }
    }

    private fun alarmSignature(alarm: AlarmEntity): String = gson.toJson(alarm.copy(id = 0))

    private suspend fun rescheduleAlarms(old: List<AlarmEntity>) {
        val scheduler = AlarmScheduler(context)
        old.forEach { scheduler.cancelAlarm(it.id) }
        val failures = database.alarmDao().getAllAlarmsOnce().filter { it.isEnabled }
            .count { alarm -> runCatching { scheduler.scheduleAlarm(alarm) }.isFailure }
        prefs.edit().apply {
            if (failures > 0) putString("alarm_warning", "알람 ${failures}개를 예약하지 못했습니다. 정확한 알람 권한을 확인해 주세요.")
            else remove("alarm_warning")
        }.commit()
    }

    private fun readText(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: error("백업 파일을 열 수 없습니다.")
        return stream.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var size = 0
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                size += count; require(size <= 32 * 1024 * 1024) { "백업 파일이 너무 큽니다." }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** A later app launch catches changes that occurred just before process death. */
    fun startAutoBackup(scope: CoroutineScope) {
        if (watcherJobs.isNotEmpty()) return
        val signal = Channel<Unit>(Channel.CONFLATED)
        listeners = listOf("music_prefs", "playback_queue_v5", "playback_history").map { name ->
            val source = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> signal.trySend(Unit) }
            source.registerOnSharedPreferenceChangeListener(listener)
            source to listener
        }
        watcherJobs = listOf(
            scope.launch { database.songMetaDao().getAllMetadata().collect { signal.trySend(Unit) } },
            scope.launch { database.alarmDao().getAllAlarms().collect { signal.trySend(Unit) } },
            scope.launch(Dispatchers.IO) {
                signal.trySend(Unit)
                for (ignored in signal) {
                    delay(2500)
                    while (signal.tryReceive().isSuccess) { /* collapse bursts */ }
                    val folder = savedFolderUri() ?: continue
                    runCatching { mutex.withLock { writeBackup(folder, "loopmuse", false) } }
                        .onFailure { prefs.edit().putString("last_error", it.message ?: "자동 백업 실패").commit() }
                }
            }
        )
    }

    fun stopAutoBackup() {
        watcherJobs.forEach { it.cancel() }; watcherJobs = emptyList()
        listeners.forEach { (source, listener) -> source.unregisterOnSharedPreferenceChangeListener(listener) }; listeners = emptyList()
    }
}
