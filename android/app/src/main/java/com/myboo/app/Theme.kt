package com.myboo.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Coral = Color(0xFFFF6B57)
val Mint = Color(0xFF3DDBB4)
val Ink = Color(0xFF0C0F14)

private val DarkColors = darkColorScheme(
    primary = Coral,
    onPrimary = Color(0xFF200A06),
    secondary = Mint,
    onSecondary = Color(0xFF00201A),
    background = Ink,
    onBackground = Color(0xFFF1F3F6),
    surface = Color(0xFF141922),
    onSurface = Color(0xFFF1F3F6),
    surfaceVariant = Color(0xFF1C232E),
    onSurfaceVariant = Color(0xFF8E99AA),
    outline = Color(0xFF2B3442),
    outlineVariant = Color(0xFF222A36),
    error = Color(0xFFFF8F85),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFE8472F),
    onPrimary = Color.White,
    secondary = Color(0xFF0E9F82),
    onSecondary = Color.White,
    background = Color(0xFFFAF7F4),
    onBackground = Color(0xFF14171C),
    surface = Color.White,
    onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFF1ECE7),
    onSurfaceVariant = Color(0xFF6B7380),
    outline = Color(0xFFDCD5CE),
    outlineVariant = Color(0xFFE9E3DD),
    error = Color(0xFFC62828),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun MyBooTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Stable per-person avatar colours. */
val AvatarColors = listOf(
    Color(0xFFFF6B57), Color(0xFF3DDBB4), Color(0xFF5B9DFF),
    Color(0xFFFFC145), Color(0xFFB583FF), Color(0xFFFF7FB6),
    Color(0xFF8BD450), Color(0xFFFF9F43), Color(0xFF4DD0E1), Color(0xFFE57373),
)

/** Set by the circle screen so members get distinct colours (first 10 are guaranteed unique). */
object AvatarPalette { var order: List<String> = emptyList() }

fun avatarColor(id: String): Color {
    val idx = AvatarPalette.order.indexOf(id)
    if (idx >= 0) return AvatarColors[idx % AvatarColors.size]
    return AvatarColors[(id.fold(7) { h, c -> h * 31 + c.code } and 0x7fffffff) % AvatarColors.size]
}
