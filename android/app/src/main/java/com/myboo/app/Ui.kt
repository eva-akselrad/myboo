package com.myboo.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

// ---------- small building blocks ----------

@Composable
fun GhostLogo(size: Dp, body: Color = MaterialTheme.colorScheme.primary, eyes: Color = MaterialTheme.colorScheme.background) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(0.1f * w, 0.9f * h)
            lineTo(0.1f * w, 0.45f * h)
            cubicTo(0.1f * w, 0.0f * h, 0.9f * w, 0.0f * h, 0.9f * w, 0.45f * h)
            lineTo(0.9f * w, 0.9f * h)
            quadraticTo(0.8f * w, 1.02f * h, 0.7f * w, 0.9f * h)
            quadraticTo(0.6f * w, 0.78f * h, 0.5f * w, 0.9f * h)
            quadraticTo(0.4f * w, 1.02f * h, 0.3f * w, 0.9f * h)
            quadraticTo(0.2f * w, 0.78f * h, 0.1f * w, 0.9f * h)
            close()
        }
        drawPath(p, Brush.verticalGradient(listOf(body, body.copy(alpha = 0.78f))))
        drawCircle(eyes, 0.065f * w, Offset(0.37f * w, 0.42f * h))
        drawCircle(eyes, 0.065f * w, Offset(0.63f * w, 0.42f * h))
    }
}

@Composable
fun Avatar(name: String, id: String, size: Dp = 44.dp) {
    val c = avatarColor(id)
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            color = Color(0xFF10131A),
            style = TextStyle(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50),
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun GhostButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
}

// ---------- formatting ----------

fun ago(ts: Long?, now: Long = System.currentTimeMillis()): String {
    if (ts == null) return "never"
    val s = max(0L, (now - ts) / 1000)
    return when {
        s < 45 -> "just now"
        s < 3600 -> "${max(1L, s / 60)}m ago"
        s < 86400 -> "${s / 3600}h ago"
        else -> "${s / 86400}d ago"
    }
}

fun speedLabel(mps: Double?): String? {
    if (mps == null) return null
    val us = Locale.getDefault().country == "US"
    val v = if (us) mps * 2.23694 else mps * 3.6
    return "${v.toInt()} ${if (us) "mph" else "km/h"}"
}

// ---------- radar: keyless live map of the circle ----------

@Composable
fun Radar(members: List<Member>, places: List<PlaceInfo>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "s")

    val located = members.filter { it.lat != null && it.lng != null }

    Box(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(scheme.surfaceVariant, scheme.surface)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val c = Offset(w / 2, h / 2)
            val maxR = min(w, h) / 2 * 0.92f

            // grid
            for (i in 1..3) drawCircle(scheme.outline.copy(alpha = 0.45f), maxR * i / 3f, c, style = Stroke(1.dp.toPx()))
            drawLine(scheme.outline.copy(alpha = 0.3f), Offset(c.x - maxR, c.y), Offset(c.x + maxR, c.y), 1.dp.toPx())
            drawLine(scheme.outline.copy(alpha = 0.3f), Offset(c.x, c.y - maxR), Offset(c.x, c.y + maxR), 1.dp.toPx())

            // sweep
            rotate(sweep, c) {
                drawCircle(
                    Brush.sweepGradient(
                        0f to Color.Transparent, 0.82f to Color.Transparent,
                        1f to scheme.secondary.copy(alpha = 0.18f), center = c,
                    ),
                    maxR, c,
                )
            }

            val pts = located.map { it.lat!! to it.lng!! } + places.map { it.lat to it.lng }
            if (pts.isEmpty()) {
                val msg = "Waiting for someone to share…"
                val r = measurer.measure(msg, TextStyle(color = scheme.onSurfaceVariant, fontSize = 14.sp))
                drawText(r, topLeft = Offset(c.x - r.size.width / 2f, c.y - r.size.height / 2f))
                return@Canvas
            }

            val lat0 = (pts.maxOf { it.first } + pts.minOf { it.first }) / 2
            val lng0 = (pts.maxOf { it.second } + pts.minOf { it.second }) / 2
            val mLat = 111_320.0
            val mLng = 111_320.0 * cos(Math.toRadians(lat0))
            fun dx(lng: Double) = (lng - lng0) * mLng
            fun dy(lat: Double) = -(lat - lat0) * mLat

            var extent = 250.0
            located.forEach { extent = max(extent, max(abs(dx(it.lng!!)), abs(dy(it.lat!!)))) }
            places.forEach { extent = max(extent, max(abs(dx(it.lng)), abs(dy(it.lat))) + it.radiusM) }
            val scale = (maxR / (extent * 1.25)).toFloat()
            fun pos(lat: Double, lng: Double) = Offset(c.x + (dx(lng) * scale).toFloat(), c.y + (dy(lat) * scale).toFloat())

            // places
            places.forEach { p ->
                val o = pos(p.lat, p.lng)
                val r = (p.radiusM * scale).toFloat().coerceAtLeast(10.dp.toPx())
                drawCircle(scheme.secondary.copy(alpha = 0.10f), r, o)
                drawCircle(scheme.secondary.copy(alpha = 0.55f), r, o, style = Stroke(1.5.dp.toPx()))
                val lbl = measurer.measure(p.name, TextStyle(color = scheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                drawText(lbl, topLeft = Offset(o.x - lbl.size.width / 2f, o.y + r + 3.dp.toPx()))
            }

            // people
            located.forEach { m ->
                val o = pos(m.lat!!, m.lng!!)
                val col = avatarColor(m.id)
                val rad = 15.dp.toPx()
                if (m.driving) {
                    drawCircle(col.copy(alpha = 0.35f * (1f - pulse)), rad + 22.dp.toPx() * pulse, o)
                }
                drawCircle(Color.Black.copy(alpha = 0.25f), rad + 2.dp.toPx(), o + Offset(0f, 2.dp.toPx()))
                drawCircle(col, rad, o)
                drawCircle(scheme.background, rad, o, style = Stroke(2.5.dp.toPx()))
                val ini = measurer.measure(
                    m.name.firstOrNull()?.uppercase() ?: "?",
                    TextStyle(color = Color(0xFF10131A), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
                )
                drawText(ini, topLeft = Offset(o.x - ini.size.width / 2f, o.y - ini.size.height / 2f))
            }
        }
    }
}
