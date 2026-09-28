package com.example.loopmuse.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val repeatDays: String = "", // Calendar day numbers: 1=Sun, 2=Mon, ..., 7=Sat
    val isOneTime: Boolean = false,
    val songFingerprintId: String? = null,
    val songTitle: String? = null,
    val songPath: String? = null,
    val startPositionMs: Long = 0L,
    val endPositionMs: Long = 0L, // 0 means play the whole song
    val targetVolume: Float = 0.5f, // 0.0 to 1.0
    val useFadeIn: Boolean = true,
    val respectPhoneSoundMode: Boolean = false, // Kept to interpret alarms created before sound modes.
    val label: String = "",
    val soundMode: String = "LEGACY" // SOUND, VIBRATE, LIGHT (silent screen), PHONE, or LEGACY.
)
