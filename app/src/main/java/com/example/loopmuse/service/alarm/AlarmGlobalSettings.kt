package com.example.loopmuse.service.alarm

import android.content.Context

data class AlarmGlobalConfig(
    val volumePercent: Int = 90,
    val vibrationPercent: Int = 50,
    val fadeEnabled: Boolean = true,
    val fadeStartPercent: Int = 10,
    val fadeDurationSeconds: Int = 30,
    val blinkIntervalSeconds: Int = 2
)

/** Values shared by all alarms; only the alert mode belongs to an individual alarm. */
object AlarmGlobalSettings {
    const val PREFS = "alarm_global_settings"

    fun read(context: Context): AlarmGlobalConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val volume = prefs.getInt("volume_percent", 90).coerceIn(10, 100)
        return AlarmGlobalConfig(
            volumePercent = volume,
            vibrationPercent = prefs.getInt("vibration_percent", 50).coerceIn(10, 100),
            fadeEnabled = prefs.getBoolean("fade_enabled", true),
            fadeStartPercent = prefs.getInt("fade_start_percent", 10).coerceIn(1, volume),
            fadeDurationSeconds = prefs.getInt("fade_duration_seconds", 30).coerceIn(5, 120),
            blinkIntervalSeconds = prefs.getInt("blink_interval_seconds", 2).coerceIn(1, 5)
        )
    }

    fun save(context: Context, config: AlarmGlobalConfig) {
        val volume = config.volumePercent.coerceIn(10, 100)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("volume_percent", volume)
            .putInt("vibration_percent", config.vibrationPercent.coerceIn(10, 100))
            .putBoolean("fade_enabled", config.fadeEnabled)
            .putInt("fade_start_percent", config.fadeStartPercent.coerceIn(1, volume))
            .putInt("fade_duration_seconds", config.fadeDurationSeconds.coerceIn(5, 120))
            .putInt("blink_interval_seconds", config.blinkIntervalSeconds.coerceIn(1, 5))
            .apply()
    }
}
