package com.myboo.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.max

// ---------- notebook paper ----------

/** Ruled paper with a margin line and binding holes. Everything draws on top of this. */
@Composable
fun NotebookBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nb = MaterialTheme.notebook
    Box(
        modifier.fillMaxSize().background(nb.paper).drawBehind {
            val step = 32.dp.toPx()
            var y = 56.dp.toPx()
            while (y < size.height) {
                drawLine(nb.ruled, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                y += step
            }
            drawLine(nb.margin.copy(alpha = 0.75f), Offset(30.dp.toPx(), 0f), Offset(30.dp.toPx(), size.height), 1.5.dp.toPx())
            var hy = 96.dp.toPx()
            while (hy < size.height) {
                drawCircle(nb.ink.copy(alpha = 0.07f), 7.dp.toPx(), Offset(12.dp.toPx(), hy))
                drawCircle(nb.ink.copy(alpha = 0.22f), 7.dp.toPx(), Offset(12.dp.toPx(), hy), style = Stroke(1.5.dp.toPx()))
                hy += 128.dp.toPx()
            }
        },
    ) { CompositionLocalProvider(LocalContentColor provides nb.ink) { content() } }
}

/** Hard offset "sticker" shadow + ink outline. */
@Composable
fun Modifier.sticker(
    fill: Color, radius: Dp = 18.dp, shadow: Dp = 4.dp, border: Dp = 2.dp,
): Modifier {
    val nb = MaterialTheme.notebook
    val shadowColor = if (nb.dark) nb.ink.copy(alpha = 0.28f) else nb.ink
    val shape = RoundedCornerShape(radius)
    return this
        .drawBehind {
            if (shadow.value > 0f) {
                drawRoundRect(shadowColor, Offset(shadow.toPx(), shadow.toPx()), size, CornerRadius(radius.toPx()))
            }
        }
        .clip(shape)
        .background(fill)
        .border(border, nb.ink, shape)
}

@Composable
fun NoteCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    tilt: Float = 0f,
    tape: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.padding(end = 5.dp, bottom = 5.dp).graphicsLayer { rotationZ = tilt }) {
        Column(Modifier.fillMaxWidth().sticker(color, radius = 20.dp).padding(18.dp), content = content)
        if (tape) TapeStrip(Modifier.align(Alignment.TopCenter).offset(y = (-9).dp))
    }
}

@Composable
fun TapeStrip(modifier: Modifier = Modifier) {
    val nb = MaterialTheme.notebook
    Box(
        modifier.rotate(-3f).size(width = 74.dp, height = 20.dp)
            .background(nb.butter.copy(alpha = 0.92f))
            .border(1.5.dp, nb.ink.copy(alpha = 0.55f)),
    )
}

// ---------- buttons, tags, avatars ----------

@Composable
fun StickerButton(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateDpAsState(if (pressed && enabled) 3.dp else 0.dp, label = "press")
    val nb = MaterialTheme.notebook
    Box(modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp)) {
        Box(
            Modifier.fillMaxWidth().offset(press, press)
                .sticker(if (enabled) fill else MaterialTheme.colorScheme.surfaceVariant, radius = 999.dp, shadow = 4.dp - press)
                .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text, style = MaterialTheme.typography.labelLarge,
                color = if (enabled) (if (nb.dark) Color(0xFF2D1B22) else nb.ink) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TextLink(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.notebook.ink.copy(alpha = 0.75f))
    }
}

/** Washi-tape style status tag. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (MaterialTheme.notebook.dark) MaterialTheme.notebook.ink else MaterialTheme.notebook.ink,
        modifier = modifier.rotate(-1.5f).sticker(color, radius = 8.dp, shadow = 0.dp, border = 1.5.dp)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun Avatar(name: String, id: String, size: Dp = 46.dp) {
    val nb = MaterialTheme.notebook
    Box(
        Modifier.size(size).clip(CircleShape).background(avatarColor(id)).border(2.dp, nb.ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF3B3A4A),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val nb = MaterialTheme.notebook
    Text(
        text, style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(start = 2.dp, top = 8.dp).drawBehind {
            drawRoundRect(
                nb.butter.copy(alpha = if (nb.dark) 0.45f else 0.95f),
                Offset(-4.dp.toPx(), size.height * 0.52f),
                androidx.compose.ui.geometry.Size(size.width + 10.dp.toPx(), size.height * 0.36f),
                CornerRadius(4.dp.toPx()),
            )
        },
    )
}

/** Doodled ghost: ink outline, blush cheeks, little smile. */
@Composable
fun GhostLogo(size: Dp, fill: Color = MaterialTheme.notebook.pink.let { if (MaterialTheme.notebook.dark) MaterialTheme.colorScheme.primary else Color(0xFFFFB3C6) }) {
    val ink = MaterialTheme.notebook.ink
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(0.12f * w, 0.88f * h)
            lineTo(0.12f * w, 0.46f * h)
            cubicTo(0.12f * w, 0.0f * h, 0.88f * w, 0.0f * h, 0.88f * w, 0.46f * h)
            lineTo(0.88f * w, 0.88f * h)
            quadraticTo(0.80f * w, 1.0f * h, 0.72f * w, 0.88f * h)
            quadraticTo(0.64f * w, 0.76f * h, 0.56f * w, 0.88f * h)
            quadraticTo(0.50f * w, 1.0f * h, 0.44f * w, 0.88f * h)
            quadraticTo(0.36f * w, 0.76f * h, 0.28f * w, 0.88f * h)
            quadraticTo(0.20f * w, 1.0f * h, 0.12f * w, 0.88f * h)
            close()
        }
        drawPath(p, fill)
        drawPath(p, ink, style = Stroke(width = 0.045f * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(ink, 0.05f * w, Offset(0.38f * w, 0.42f * h))
        drawCircle(ink, 0.05f * w, Offset(0.62f * w, 0.42f * h))
        drawCircle(Color(0xFFFF8FA9).copy(alpha = 0.55f), 0.055f * w, Offset(0.30f * w, 0.53f * h))
        drawCircle(Color(0xFFFF8FA9).copy(alpha = 0.55f), 0.055f * w, Offset(0.70f * w, 0.53f * h))
        val smile = Path().apply {
            moveTo(0.44f * w, 0.53f * h)
            quadraticTo(0.50f * w, 0.60f * h, 0.56f * w, 0.53f * h)
        }
        drawPath(smile, ink, style = Stroke(width = 0.035f * w, cap = StrokeCap.Round))
    }
}

// ---------- inputs ----------

@Composable
fun NoteField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    var show by remember { mutableStateOf(false) }
    val ink = MaterialTheme.notebook.ink
    val surface = MaterialTheme.colorScheme.surface
    OutlinedTextField(
        value, onChange, modifier = modifier.fillMaxWidth(), singleLine = true,
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onGo = { onDone?.invoke() }),
        trailingIcon = if (password) {
            { TextButton(onClick = { show = !show }) { Text(if (show) "hide" else "show", color = ink.copy(alpha = 0.7f)) } }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ink, unfocusedBorderColor = ink.copy(alpha = 0.45f),
            focusedContainerColor = surface, unfocusedContainerColor = surface,
            focusedLabelColor = ink, cursorColor = ink,
        ),
    )
}

/** Six notebook cells for the emailed code. */
@Composable
fun CodeField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val nb = MaterialTheme.notebook
    val focus = remember { FocusRequester() }
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(6)) },
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = {
            Row(Modifier.fillMaxWidth().padding(end = 3.dp, bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    val ch = value.getOrNull(i)
                    Box(
                        Modifier.weight(1f).aspectRatio(0.78f)
                            .sticker(if (ch != null) nb.butter else MaterialTheme.colorScheme.surface, radius = 12.dp, shadow = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(ch?.toString() ?: "", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
fun ErrorNote(text: String?) {
    if (text != null) Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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

@Composable
fun FullScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}
