package com.example.loopmuse.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.loopmuse.service.alarm.AlarmGlobalSettings
import com.example.loopmuse.service.alarm.AlarmPlaybackService
import com.example.loopmuse.service.alarm.AlarmScheduler
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

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
    private var isCelebrating by mutableStateOf(false)
    private var alarmDismissed by mutableStateOf(false)
    private var snoozeEnabled by mutableStateOf(true)
    private var snoozeMinutes by mutableStateOf(5)

    private val alarmFinishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra("ALARM_ID", 0) != alarmId) return
            when (intent.action) {
                AlarmPlaybackService.ACTION_ALARM_FINISHED -> {
                    if (isCelebrating) alarmDismissed = true else finish()
                }
                AlarmPlaybackService.ACTION_ALARM_SILENCED -> {
                    isChaseMode = true
                    actionPending = false
                }
                AlarmPlaybackService.ACTION_SNOOZE_FAILED -> {
                    actionPending = false
                    Toast.makeText(this@AlarmRingingActivity,
                        "${snoozeMinutes}분 후 다시 울림을 설정할 수 없습니다.", Toast.LENGTH_LONG).show()
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
        isCelebrating = savedInstanceState?.getBoolean("CELEBRATING") ?: false
        alarmDismissed = savedInstanceState?.getBoolean("ALARM_DISMISSED") ?: false
        setContent {
            MaterialTheme {
                AlarmRingingScreen(
                    time = String.format(Locale.KOREA, "%02d:%02d", alarmHour, alarmMinute),
                    songTitle = songTitle,
                    isChaseMode = isChaseMode,
                    snoozeEnabled = snoozeEnabled,
                    snoozeMinutes = snoozeMinutes,
                    actionPending = actionPending,
                    isCelebrating = isCelebrating,
                    onSnooze = { sendAction(AlarmPlaybackService.ACTION_STOP_ALARM) },
                    onComplete = {
                        if (!actionPending) {
                            isCelebrating = true
                            sendAction(AlarmPlaybackService.ACTION_DISMISS_SNOOZE)
                        }
                    }
                )
                LaunchedEffect(isCelebrating, alarmDismissed) {
                    if (isCelebrating && alarmDismissed) {
                        delay(2200L)
                        finish()
                    } else if (isCelebrating) {
                        delay(4000L)
                        if (!alarmDismissed) {
                            isCelebrating = false
                            actionPending = false
                            Toast.makeText(this@AlarmRingingActivity,
                                "알람 종료를 확인할 수 없습니다. 다시 시도해 주세요.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
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
        outState.putBoolean("CELEBRATING", isCelebrating)
        outState.putBoolean("ALARM_DISMISSED", alarmDismissed)
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
        if (isCelebrating && !ringingNow && !AlarmScheduler(this).hasSnooze(alarmId)) {
            alarmDismissed = true
        }
        if (alarmId == 0 || (!isCelebrating && !ringingNow &&
                !(isChaseMode && AlarmScheduler(this).hasSnooze(alarmId)))) finish()
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
        AlarmGlobalSettings.read(this).let {
            snoozeEnabled = it.snoozeEnabled
            snoozeMinutes = it.snoozeMinutes
        }
        isChaseMode = false
        isCelebrating = false
        alarmDismissed = false
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
            isCelebrating = false
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
    snoozeEnabled: Boolean,
    snoozeMinutes: Int,
    actionPending: Boolean,
    isCelebrating: Boolean,
    onSnooze: () -> Unit,
    onComplete: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = nightBackground) {
      Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("LOOPMUSE  ·  ALARM", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp, color = lavender)
            Spacer(Modifier.height(10.dp))
            Text(time, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = softWhite)
            Text(songTitle?.takeIf { it.isNotBlank() } ?: "알람음",
                fontSize = 16.sp, color = softWhite.copy(alpha = 0.72f),
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            RabbitRunway(Modifier.fillMaxWidth().weight(1f),
                enabled = !actionPending, onRabbitCaught = onComplete)
            Spacer(Modifier.height(12.dp))
            Text(when {
                isChaseMode -> "${snoozeMinutes}분 뒤 다시 울립니다"
                snoozeEnabled -> "알람 멈춤 시 ${snoozeMinutes}분 뒤 다시 울립니다"
                else -> "스누즈가 꺼져 있습니다"
            },
                fontSize = 17.sp, fontWeight = FontWeight.Medium, color = softWhite)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSnooze,
                enabled = !actionPending && !isChaseMode,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = lavender, contentColor = nightBackground,
                    disabledContainerColor = lavender.copy(alpha = 0.25f),
                    disabledContentColor = softWhite.copy(alpha = 0.65f))
            ) { Text("알람 멈춤", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        if (isCelebrating) CelebrationOverlay()
      }
    }
}

@Composable
private fun CelebrationOverlay() {
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        burst.animateTo(1f, tween(durationMillis = 2000, easing = LinearEasing))
    }
    Box(Modifier.fillMaxSize().background(nightBackground.copy(alpha = 0.76f))) {
        Canvas(Modifier.fillMaxSize()) {
            val centers = listOf(
                Offset(size.width * 0.22f, size.height * 0.26f),
                Offset(size.width * 0.77f, size.height * 0.30f),
                Offset(size.width * 0.49f, size.height * 0.68f)
            )
            val colors = listOf(Color(0xFFFFD66E), Color(0xFFB9F29B),
                Color(0xFFFFA8CF), Color(0xFFB9A9F5), Color(0xFF8DDBFF))
            centers.forEachIndexed { burstIndex, center ->
                val progress = ((burst.value - burstIndex * 0.13f) / 0.72f).coerceIn(0f, 1f)
                if (progress > 0f && progress < 1f) {
                    val radius = (18.dp.toPx() + progress * 132.dp.toPx())
                    val alpha = (1f - progress).coerceIn(0f, 1f)
                    repeat(18) { spark ->
                        val angle = (spark * 2 * PI / 18 + burstIndex * 0.27).toFloat()
                        val direction = Offset(cos(angle), sin(angle))
                        val end = center + direction * radius
                        val color = colors[(spark + burstIndex) % colors.size]
                        drawLine(color.copy(alpha = alpha * 0.65f),
                            center + direction * radius * 0.72f, end,
                            strokeWidth = 2.dp.toPx())
                        drawCircle(color.copy(alpha = alpha),
                            radius = (if (spark % 3 == 0) 3.5f else 2.3f).dp.toPx(), center = end)
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.82f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF25314C),
            border = BorderStroke(1.dp, lavender.copy(alpha = 0.7f)),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎉", fontSize = 38.sp)
                Spacer(Modifier.height(10.dp))
                Text("축하합니다! 토끼를 잡았어요", fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, color = softWhite, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("알람을 종료합니다", fontSize = 16.sp,
                    color = Color(0xFFD8FFAF), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RabbitRunway(modifier: Modifier = Modifier, enabled: Boolean, onRabbitCaught: () -> Unit) {
    var hop by remember { mutableFloatStateOf(0f) }
    var stride by remember { mutableFloatStateOf(0f) }
    val hintTravel = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        hintTravel.snapTo(0f)
        while (enabled) {
            hintTravel.animateTo(1f, tween(durationMillis = 9000, easing = LinearEasing))
            hintTravel.snapTo(0f)
        }
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = Color(0xFF151D30),
        border = BorderStroke(1.dp, Color(0xFF35405A))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val rabbitWidthPx = with(density) { 64.dp.toPx() }
            val rabbitHeightPx = with(density) { 58.dp.toPx() }
            val margin = with(density) { 38.dp.toPx() }
            var rabbit by remember(widthPx, heightPx) {
                mutableStateOf(Offset(widthPx * 0.5f, heightPx * 0.54f))
            }
            var finger by remember { mutableStateOf<Offset?>(null) }
            var facingRight by remember { mutableStateOf(true) }
            var hintHeightPx by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(widthPx, heightPx, enabled) {
                if (!enabled || widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
                val random = Random(System.nanoTime())
                val danger = with(density) { 130.dp.toPx() }
                val walkingSpeed = with(density) { 95.dp.toPx() }
                val runningSpeed = with(density) { 190.dp.toPx() }
                val sprintingSpeed = with(density) { 305.dp.toPx() }
                var naturalPace = 1
                var paceFrames = 28
                var currentSpeed = runningSpeed
                var hopPhase = 0f
                var lastFrame = SystemClock.elapsedRealtime()
                fun randomTarget() = Offset(
                    (margin + random.nextFloat() * (widthPx - margin * 2).coerceAtLeast(1f))
                        .coerceIn(margin.coerceAtMost(widthPx / 2), (widthPx - margin).coerceAtLeast(widthPx / 2)),
                    (margin + random.nextFloat() * (heightPx - margin * 2).coerceAtLeast(1f))
                        .coerceIn(margin.coerceAtMost(heightPx / 2), (heightPx - margin).coerceAtLeast(heightPx / 2))
                )
                var target = randomTarget()
                var steps = 0
                while (true) {
                    val now = SystemClock.elapsedRealtime()
                    val frameMs = (now - lastFrame).coerceIn(16L, 50L).toFloat()
                    lastFrame = now
                    val touch = finger
                    var fleeing = false
                    if (touch != null) {
                        val dx = rabbit.x - touch.x
                        val dy = rabbit.y - touch.y
                        val distance = hypot(dx, dy)
                        if (distance < danger) {
                            fleeing = true
                            val awayX = if (distance > 1f) dx / distance else if (facingRight) 1f else -1f
                            val awayY = if (distance > 1f) dy / distance else 0.5f
                            target = Offset(
                                (rabbit.x + awayX * danger).coerceIn(
                                    margin.coerceAtMost(widthPx / 2), (widthPx - margin).coerceAtLeast(widthPx / 2)),
                                (rabbit.y + awayY * danger).coerceIn(
                                    margin.coerceAtMost(heightPx / 2), (heightPx - margin).coerceAtLeast(heightPx / 2))
                            )
                            steps = 0
                        }
                    }
                    if (steps++ > 23 || hypot(target.x - rabbit.x, target.y - rabbit.y) < 9f) {
                        target = randomTarget()
                        steps = 0
                    }
                    if (--paceFrames <= 0) {
                        val choices = when (naturalPace) {
                            0 -> intArrayOf(0, 1, 1, 2)
                            2 -> intArrayOf(0, 1, 1)
                            else -> intArrayOf(0, 0, 1, 2)
                        }
                        naturalPace = choices[random.nextInt(choices.size)]
                        paceFrames = when (naturalPace) {
                            0 -> random.nextInt(25, 44)
                            2 -> random.nextInt(12, 23)
                            else -> random.nextInt(20, 37)
                        }
                    }
                    val pace = if (fleeing) 2 else naturalPace
                    val desiredSpeed = when (pace) {
                        0 -> walkingSpeed
                        2 -> sprintingSpeed
                        else -> runningSpeed
                    }
                    currentSpeed += (desiredSpeed - currentSpeed) * if (fleeing) 0.32f else 0.16f
                    val hopDurationMs = when (pace) { 0 -> 670f; 2 -> 320f; else -> 440f }
                    val hopHeight = when (pace) { 0 -> 0.22f; 2 -> 1f; else -> 0.65f }
                    hopPhase = (hopPhase + frameMs / hopDurationMs) % 1f
                    stride = hopPhase
                    // A quadratic arc makes each step rise, peak, and land smoothly.
                    hop = 4f * hopPhase * (1f - hopPhase) * hopHeight
                    val dx = target.x - rabbit.x
                    val dy = target.y - rabbit.y
                    val distance = hypot(dx, dy)
                    if (distance > 0f) {
                        val step = minOf(currentSpeed * frameMs / 1000f, distance)
                        rabbit = Offset(rabbit.x + dx / distance * step, rabbit.y + dy / distance * step)
                        if (kotlin.math.abs(dx) > 2f) facingRight = dx > 0f
                    }
                    delay(33L)
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val stars = listOf(
                    Offset(0.08f, 0.20f), Offset(0.25f, 0.45f), Offset(0.43f, 0.15f),
                    Offset(0.60f, 0.32f), Offset(0.81f, 0.16f), Offset(0.93f, 0.50f),
                    Offset(0.12f, 0.77f), Offset(0.52f, 0.82f), Offset(0.78f, 0.72f)
                )
                stars.forEachIndexed { index, star ->
                    drawCircle(lavender.copy(alpha = if (index % 3 == 0) 0.34f else 0.16f),
                        radius = if (index % 3 == 0) 3.dp.toPx() else 2.dp.toPx(),
                        center = Offset(size.width * star.x, size.height * star.y))
                }
            }
            Text("토끼를 잡아야\n알람이 완전히 종료됩니다", fontSize = 18.sp,
                lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD8FFAF).copy(alpha = 0.5f),
                style = TextStyle(shadow = Shadow(Color(0xFFB6FF79).copy(alpha = 0.3f),
                    Offset.Zero, blurRadius = 16f)),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp, start = 12.dp,
                    end = 12.dp).onSizeChanged { hintHeightPx = it.height.toFloat() }
                    .graphicsLayer {
                        translationY = (heightPx - hintHeightPx - 36.dp.toPx()).coerceAtLeast(0f) *
                            hintTravel.value
                        alpha = minOf(1f, hintTravel.value * 12f,
                            (1f - hintTravel.value) * 12f)
                    })
            RabbitIllustration(hop, stride, Modifier.size(64.dp, 58.dp)
                .graphicsLayer {
                    translationX = rabbit.x - rabbitWidthPx / 2f
                    translationY = rabbit.y - rabbitHeightPx / 2f - hop * 14.dp.toPx()
                    scaleX = if (facingRight) 1f else -1f
                }
                .semantics {
                    contentDescription = "시계를 업고 뛰는 토끼"
                    onClick("토끼를 잡아 알람 종료") { if (enabled) onRabbitCaught(); enabled }
                })
            // The press is checked before the rabbit reacts, so a successful catch stays possible.
            Spacer(Modifier.fillMaxSize().pointerInput(widthPx, heightPx, enabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (enabled && hypot(down.position.x - rabbit.x, down.position.y - rabbit.y) <=
                        with(density) { 39.dp.toPx() }) {
                        onRabbitCaught()
                    } else {
                        finger = down.position
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            finger = if (change.pressed) change.position else null
                        } while (change.pressed)
                    }
                    finger = null
                }
            })
        }
    }
}

@Composable
private fun RabbitIllustration(hop: Float, stride: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val fur = softWhite
        val shade = Color(0xFFD7CFF2)
        val pink = Color(0xFFDBA9C8)
        val ink = Color(0xFF26314C)
        val pawShift = if (stride < 0.5f) 0.06f else -0.06f
        val bob = hop * h * 0.035f
        drawOval(shade, Offset(w * (0.31f + pawShift), h * 0.77f - bob), Size(w * 0.24f, h * 0.10f))
        drawOval(shade, Offset(w * (0.63f - pawShift), h * 0.75f - bob), Size(w * 0.25f, h * 0.10f))
        drawCircle(fur, w * 0.09f, Offset(w * 0.23f, h * 0.59f - bob))
        drawOval(fur, Offset(w * 0.23f, h * 0.38f - bob), Size(w * 0.56f, h * 0.39f))
        rotate(-17f + stride * 11f, Offset(w * 0.73f, h * 0.42f - bob)) {
            drawOval(fur, Offset(w * 0.69f, h * 0.03f - bob), Size(w * 0.13f, h * 0.43f))
            drawOval(pink, Offset(w * 0.725f, h * 0.09f - bob), Size(w * 0.05f, h * 0.30f))
        }
        rotate(15f - stride * 12f, Offset(w * 0.85f, h * 0.42f - bob)) {
            drawOval(fur, Offset(w * 0.81f, h * 0.01f - bob), Size(w * 0.13f, h * 0.45f))
            drawOval(pink, Offset(w * 0.845f, h * 0.07f - bob), Size(w * 0.05f, h * 0.31f))
        }
        drawCircle(fur, w * 0.17f, Offset(w * 0.79f, h * 0.52f - bob))
        drawCircle(ink, w * 0.021f, Offset(w * 0.85f, h * 0.48f - bob))
        drawCircle(pink, w * 0.024f, Offset(w * 0.96f, h * 0.58f - bob))
        // Two straps and a round clock make the clock read as a backpack.
        drawLine(Color(0xFF8C7EC6), Offset(w * 0.36f, h * 0.33f - bob),
            Offset(w * 0.34f, h * 0.69f - bob), strokeWidth = w * 0.045f)
        drawLine(Color(0xFF8C7EC6), Offset(w * 0.52f, h * 0.34f - bob),
            Offset(w * 0.59f, h * 0.68f - bob), strokeWidth = w * 0.045f)
        val clock = Offset(w * 0.45f, h * 0.42f - bob)
        drawCircle(lavender, w * 0.155f, clock)
        drawCircle(Color(0xFFFFFBEC), w * 0.12f, clock)
        drawLine(ink, clock, Offset(clock.x, clock.y - h * 0.075f), strokeWidth = w * 0.025f)
        drawLine(ink, clock, Offset(clock.x + w * 0.064f, clock.y + h * 0.023f),
            strokeWidth = w * 0.025f)
        drawCircle(ink, w * 0.017f, clock)
    }
}
