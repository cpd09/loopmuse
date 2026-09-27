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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        window.statusBarColor = android.graphics.Color.rgb(11, 16, 27)
        window.navigationBarColor = android.graphics.Color.rgb(11, 16, 27)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        readAlarm(intent)
        isChaseMode = savedInstanceState?.getBoolean("CHASE_MODE") ?: false
        setContent {
            MaterialTheme {
                AlarmRingingScreen(
                    time = String.format(Locale.KOREA, "%02d:%02d", alarmHour, alarmMinute),
                    songTitle = songTitle,
                    isChaseMode = isChaseMode,
                    actionPending = actionPending,
                    onSnooze = { sendAction(AlarmPlaybackService.ACTION_STOP_ALARM) },
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
        val ringingNow = AlarmPlaybackService.activeAlarmId == alarmId
        if (alarmId == 0 || (!ringingNow && !(isChaseMode && AlarmScheduler(this).hasSnooze(alarmId)))) finish()
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

private val nightBackground = Color(0xFF0B101B)
private val lavender = Color(0xFFB9A9F5)
private val softWhite = Color(0xFFF7F3FF)

@Composable
private fun AlarmRingingScreen(
    time: String,
    songTitle: String?,
    isChaseMode: Boolean,
    actionPending: Boolean,
    onSnooze: () -> Unit,
    onComplete: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = nightBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("LOOPMUSE  ·  ALARM", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp, color = lavender)
            Spacer(Modifier.height(10.dp))
            Text(time, fontSize = 68.sp, fontWeight = FontWeight.Bold, color = softWhite)
            Text(songTitle?.takeIf { it.isNotBlank() } ?: "알람음",
                fontSize = 16.sp, color = softWhite.copy(alpha = 0.72f),
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            RabbitRunway(Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(24.dp))
            Text(if (isChaseMode) "5분 뒤 다시 울립니다" else "알람이 울리고 있습니다",
                fontSize = 17.sp, fontWeight = FontWeight.Medium, color = softWhite)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onComplete,
                enabled = !actionPending,
                modifier = Modifier.fillMaxWidth().height(62.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = lavender, contentColor = nightBackground)
            ) { Text("완전히 종료", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSnooze,
                enabled = !actionPending && !isChaseMode,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, lavender.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = softWhite,
                    disabledContentColor = softWhite.copy(alpha = 0.62f))
            ) { Text(if (isChaseMode) "재알람 대기 중" else "5분 뒤 다시 울림", fontSize = 16.sp) }
        }
    }
}

@Composable
private fun RabbitRunway(modifier: Modifier = Modifier) {
    val movement = rememberInfiniteTransition(label = "토끼 달리기")
    val cycle by movement.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(7600, easing = LinearEasing), RepeatMode.Restart),
        label = "좌우 이동"
    )
    val hop by movement.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 760
            0f at 0
            1f at 240
            0f at 520
            0f at 760
        }),
        label = "빠른 점프"
    )
    Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = Color(0xFF151D30),
        border = BorderStroke(1.dp, Color(0xFF35405A))) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stars = listOf(
                    Offset(0.08f, 0.20f), Offset(0.25f, 0.45f), Offset(0.43f, 0.15f),
                    Offset(0.60f, 0.32f), Offset(0.81f, 0.16f), Offset(0.93f, 0.50f),
                    Offset(0.12f, 0.77f), Offset(0.52f, 0.82f), Offset(0.78f, 0.72f)
                )
                stars.forEachIndexed { index, star ->
                    drawCircle(
                        color = lavender.copy(alpha = if (index % 3 == 0) 0.34f else 0.16f),
                        radius = if (index % 3 == 0) 3.dp.toPx() else 2.dp.toPx(),
                        center = Offset(size.width * star.x, size.height * star.y)
                    )
                }
            }
            val rabbitWidth = 120.dp
            val travel = (maxWidth - rabbitWidth).coerceAtLeast(0.dp)
            val goingRight = cycle < 1f
            val progress = if (goingRight) cycle else 2f - cycle
            RabbitIllustration(
                Modifier.width(rabbitWidth).height(100.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = travel * progress, y = -24.dp * hop)
                    .graphicsLayer { scaleX = if (goingRight) 1f else -1f }
                    .semantics { contentDescription = "좌우로 뛰어다니는 토끼" }
            )
        }
    }
}

@Composable
private fun RabbitIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val fur = softWhite
        val shadow = Color(0xFFD7CFF2)
        val innerEar = Color(0xFFDBA9C8)
        drawOval(shadow, topLeft = Offset(w * 0.30f, h * 0.77f), size = Size(w * 0.23f, h * 0.10f))
        drawOval(shadow, topLeft = Offset(w * 0.65f, h * 0.77f), size = Size(w * 0.23f, h * 0.10f))
        drawCircle(fur, radius = w * 0.095f, center = Offset(w * 0.23f, h * 0.64f))
        drawOval(fur, topLeft = Offset(w * 0.23f, h * 0.47f), size = Size(w * 0.55f, h * 0.34f))
        rotate(-15f, pivot = Offset(w * 0.74f, h * 0.45f)) {
            drawOval(fur, topLeft = Offset(w * 0.70f, h * 0.06f), size = Size(w * 0.12f, h * 0.43f))
            drawOval(innerEar, topLeft = Offset(w * 0.735f, h * 0.12f), size = Size(w * 0.05f, h * 0.30f))
        }
        rotate(13f, pivot = Offset(w * 0.85f, h * 0.44f)) {
            drawOval(fur, topLeft = Offset(w * 0.82f, h * 0.03f), size = Size(w * 0.12f, h * 0.45f))
            drawOval(innerEar, topLeft = Offset(w * 0.855f, h * 0.10f), size = Size(w * 0.05f, h * 0.30f))
        }
        drawCircle(fur, radius = w * 0.17f, center = Offset(w * 0.79f, h * 0.56f))
        drawCircle(Color(0xFF20243A), radius = w * 0.018f, center = Offset(w * 0.85f, h * 0.50f))
        drawCircle(innerEar, radius = w * 0.025f, center = Offset(w * 0.95f, h * 0.59f))
    }
}
