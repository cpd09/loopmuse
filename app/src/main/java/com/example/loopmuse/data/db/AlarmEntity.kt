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
    val repeatDays: String = "", // e.g., "1,2,3,4,5" for Mon-Fri
    val isOneTime: Boolean = false,
    val songFingerprintId: String? = null,
    val songTitle: String? = null,
    val songPath: String? = null,
    val startPositionMs: Long = 0L,
    val targetVolume: Float = 0.5f, // 0.0 to 1.0
    val useFadeIn: Boolean = true
)