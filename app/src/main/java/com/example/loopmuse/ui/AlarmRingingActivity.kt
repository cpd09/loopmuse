package com.example.loopmuse.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.loopmuse.service.alarm.AlarmPlaybackService
import com.example.loopmuse.service.alarm.AlarmScheduler
import java.util.Locale

class AlarmRingingActivity : ComponentActivity() {
    companion object {
        @Volatile var isResumed = false
            private set
    }

    private var alarmId by mutableStateOf(0)
    private var alarmHour by mutableStateOf(0)
    private var alarmMinute by mutableStateOf(0)
    private var songTitle by mutableStateOf<String?>(null)
    private var actionPending by mutableStateOf(false)
    private var isSnoozeRing by mutableStateOf(false)
    private var isChaseMode by mutableStateOf(false)

    private val alarmFinishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra("ALARM_ID", 0) != alarmId) return
            when (intent.action) {
                AlarmPlaybackService.ACTION_ALARM_FINISHED -> finish()
                AlarmPlaybackService.ACTION_ALARM_SILENCED -> {
                    isChaseMode = true
                    actionPending = false
                }
                AlarmPlaybackService.ACTION_SNOOZE_FAILED -> {
                    actionPending = false
                    Toast.makeText(this@AlarmRingingActivity, "5분 후 다시 울림을 설정할 수 없습니다.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        readAlarm(intent)
        isChaseMode = savedInstanceState?.getBoolean("CHASE_MODE") ?: false
        setContent {
            MaterialTheme {
                AlarmRingingScreen(
                    time = String.format(Locale.KOREA, "%02d:%02d", alarmHour, alarmMinute),
                    songTitle = songTitle,
                    isSnoozeRing = isSnoozeRing,
                    isChaseMode = isChaseMode,
                    actionPending = actionPending,
                    onSilence = { sendAction(AlarmPlaybackService.ACTION_STOP_ALARM) },
                    onComplete = { sendAction(AlarmPlaybackService.ACTION_DISMISS_SNOOZE) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readAlarm(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("CHASE_MODE", isChaseMode)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, alarmFinishedReceiver,
            IntentFilter().apply {
                addAction(AlarmPlaybackService.ACTION_ALARM_FINISHED)
                addAction(AlarmPlaybackService.ACTION_ALARM_SILENCED)
                addAction(AlarmPlaybackService.ACTION_SNOOZE_FAILED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (alarmId == 0 || (isChaseMode && !AlarmScheduler(this).hasSnooze(alarmId)) ||
            (!isChaseMode && AlarmPlaybackService.activeAlarmId != alarmId)) finish()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    override fun onStop() {
        unregisterReceiver(alarmFinishedReceiver)
        super.onStop()
    }

    private fun readAlarm(intent: Intent) {
        alarmId = intent.getIntExtra("ALARM_ID", 0)
        alarmHour = intent.getIntExtra("ALARM_HOUR", 0)
        alarmMinute = intent.getIntExtra("ALARM_MINUTE", 0)
        songTitle = intent.getStringExtra("SONG_TITLE")
        isSnoozeRing = intent.getBooleanExtra("IS_SNOOZE", false)
        isChaseMode = false
        actionPending = false
    }

    private fun sendAction(action: String) {
        if (actionPending || alarmId == 0) return
        actionPending = true
        try {
            startService(Intent(this, AlarmPlaybackService::class.java).apply {
                this.action = action
                putExtra("ALARM_ID", alarmId)
            })
        } catch (e: Exception) {
            actionPending = false
            Toast.makeText(this, "알람을 처리할 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun AlarmRingingScreen(
    time: String,
    songTitle: String?,
    isSnoozeRing: Boolean,
    isChaseMode: Boolean,
    actionPending: Boolean,
    onSilence: () -> Unit,
    onComplete: () -> Unit
) {
    val background = Color(0xFF17122A)
    val accent = Color(0xFFB9A3FF)
    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 58.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⏰", fontSize = 54.sp)
                Spacer(Modifier.height(24.dp))
                Text(if (isChaseMode) "재알람이 잠시 멈췄습니다" else if (isSnoozeRing) "LoopMuse 재알람" else "LoopMuse 알람",
                    fontSize = 22.sp, color = accent, fontWeight = FontWeight.Bold)
                Text(time, fontSize = 72.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text(songTitle?.takeIf { it.isNotBlank() } ?: "알람음",
                    fontSize = 18.sp, color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (isChaseMode) {
                    Text("5분 뒤 다시 울립니다.\n토끼를 눌러 완전히 종료하세요.",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    val transition = rememberInfiniteTransition(label = "토끼 이동")
                    val position by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(animation = tween(9000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                        label = "좌우 이동"
                    )
                    val hop by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(animation = keyframes {
                            durationMillis = 1400
                            0f at 0
                            1f at 300
                            0f at 650
                            0f at 1400
                        }),
                        label = "토끼 점프"
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                        val rabbitWidth = 150.dp
                        val travel = (maxWidth - rabbitWidth).coerceAtLeast(0.dp)
                        Column(
                            modifier = Modifier.width(rabbitWidth).height(100.dp)
                                .offset(x = travel * position, y = 24.dp - 22.dp * hop)
                                .clickable(enabled = !actionPending, role = Role.Button, onClick = onComplete),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🐇", fontSize = 50.sp)
                            Text("완전히 종료", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                color = Color.White)
                        }
                    }
                } else {
                    Text("끄더라도 5분 뒤 다시 울립니다.",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onSilence,
                        enabled = !actionPending,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = background)
                    ) { Text("알람 끄기", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
