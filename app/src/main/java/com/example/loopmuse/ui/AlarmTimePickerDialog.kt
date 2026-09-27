package com.example.loopmuse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AlarmTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    var selectingHour by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth(0.94f)) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("알람 시간", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { selectingHour = true }) {
                        Text(String.format(Locale.KOREA, "%02d", hour), fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectingHour) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(":", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { selectingHour = false }) {
                        Text(String.format(Locale.KOREA, "%02d", minute), fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!selectingHour) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(if (selectingHour) "안쪽 1~12시 · 바깥쪽 13~24시" else "분을 고른 뒤 ±1분으로 조절",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    AlarmClockDial(
                        dialSize = minOf(maxWidth, 286.dp),
                        hourMode = selectingHour,
                        hour = hour,
                        minute = minute,
                        onHourSelected = { hour = it; selectingHour = false },
                        onMinuteSelected = { minute = it }
                    )
                }
                if (!selectingHour) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { minute = (minute + 59) % 60 }) { Text("−1분") }
                        Text(String.format(Locale.KOREA, "%02d분", minute),
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        TextButton(onClick = { minute = (minute + 1) % 60 }) { Text("+1분") }
                    }
                } else {
                    Text("24시는 자정(00시)입니다", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(onClick = { onConfirm(hour, minute) }) { Text("확인") }
                }
            }
        }
    }
}

@Composable
private fun AlarmClockDial(
    dialSize: Dp,
    hourMode: Boolean,
    hour: Int,
    minute: Int,
    onHourSelected: (Int) -> Unit,
    onMinuteSelected: (Int) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.outlineVariant
    val center = dialSize.value / 2f
    val outerRadius = center - 30f
    val innerRadius = outerRadius * 0.61f
    val labelSize = 36.dp

    Box(modifier = Modifier.size(dialSize).background(MaterialTheme.colorScheme.surfaceVariant,
        CircleShape), contentAlignment = Alignment.TopStart) {
        Canvas(Modifier.size(dialSize)) {
            val centerPx = Offset(size.width / 2f, size.height / 2f)
            drawCircle(ring, radius = outerRadius.dp.toPx(), center = centerPx,
                style = Stroke(width = 1.dp.toPx()))
            if (hourMode) drawCircle(ring, radius = innerRadius.dp.toPx(), center = centerPx,
                style = Stroke(width = 1.dp.toPx()))
            val angle = (if (hourMode) (hour % 12).toDouble() else minute / 5.0) * PI / 6 - PI / 2
            val radiusPx = (if (hourMode && hour in 1..12) innerRadius else outerRadius).dp.toPx()
            val end = Offset(centerPx.x + (cos(angle) * radiusPx).toFloat(),
                centerPx.y + (sin(angle) * radiusPx).toFloat())
            drawLine(primary, centerPx, end, strokeWidth = 2.dp.toPx())
            drawCircle(primary, radius = 4.dp.toPx(), center = centerPx)
        }

        fun hourLabel(index: Int, outer: Boolean): Int = when {
            outer && index == 0 -> 0
            outer -> index + 12
            index == 0 -> 12
            else -> index
        }

        val radii = if (hourMode) listOf(outerRadius to true, innerRadius to false)
            else listOf(outerRadius to true)
        radii.forEach { (radius, outer) ->
            repeat(12) { index ->
                val angle = index * PI / 6 - PI / 2
                val x = center + radius * cos(angle).toFloat() - 18f
                val y = center + radius * sin(angle).toFloat() - 18f
                val value = if (hourMode) hourLabel(index, outer) else index * 5
                val selected = if (hourMode) hour == value else minute == value
                val label = when {
                    hourMode && outer && index == 0 -> "24"
                    hourMode -> value.toString()
                    else -> String.format(Locale.KOREA, "%02d", value)
                }
                Box(modifier = Modifier.offset(x.dp, y.dp).size(labelSize)
                    .background(if (selected) primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape)
                    .clickable { if (hourMode) onHourSelected(value) else onMinuteSelected(value) }
                    .semantics { contentDescription = if (hourMode) "$label 시" else "$label 분" },
                    contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) onPrimary else onSurface)
                }
            }
        }
    }
}
