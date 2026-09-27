package com.example.loopmuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.loopmuse.service.alarm.AlarmPlaybackService
import com.example.loopmuse.ui.HomeScreen

class MainActivity : ComponentActivity() {
    companion object {
        @Volatile var isResumed = false
            private set
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        AlarmPlaybackService.activeScreenIntent(this)?.let(::startActivity)
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HomeScreen() }
    }
}
