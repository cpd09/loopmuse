package com.example.loopmuse.community.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages blocked users locally for Google Play UGC compliance.
 * Blocked users' posts and comments are immediately hidden on the client.
 */
object CommunityBlockManager {
    private const val PREFS_NAME = "loopmuse_community_blocks"
    private const val KEY_BLOCKED_UIDS = "blocked_uids"

    private val _blockedUids = MutableStateFlow<Set<String>>(emptySet())
    val blockedUids: StateFlow<Set<String>> = _blockedUids.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            val prefs = getPrefs(context)
            val saved = prefs.getStringSet(KEY_BLOCKED_UIDS, emptySet())?.toSet() ?: emptySet()
            _blockedUids.value = saved
            initialized = true
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun blockUser(context: Context, uid: String) {
        if (uid.isBlank()) return
        val current = _blockedUids.value.toMutableSet()
        current.add(uid)
        _blockedUids.value = current
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_UIDS, current).apply()
    }

    fun unblockUser(context: Context, uid: String) {
        val current = _blockedUids.value.toMutableSet()
        current.remove(uid)
        _blockedUids.value = current
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_UIDS, current).apply()
    }

    fun isBlocked(uid: String): Boolean {
        return _blockedUids.value.contains(uid)
    }
}
