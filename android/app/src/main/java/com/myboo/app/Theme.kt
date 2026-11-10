package com.myboo.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Pastel notebook palette -------------------------------------------------

/** Extra, non-Material colours for the notebook look. */
data class Notebook(
    val ink: Color,
    val paper: Color,
    val ruled: Color,     // horizontal lines
    val margin: Color,    // red-ish margin rule
    val pink: Color,
    val peach: Color,
    val butter: Color,
    val mint: Color,
    val sky: Color,
    val lavender: Color,
    val dark: Boolean,
)

private val LightNotebook = Notebook(
    ink = Color(0xFF3B3A4A),
    paper = Color(0xFFFFFAF0),
    ruled = Color(0xFFD3E6F8),
    margin = Color(0xFFF7A8B8),
    pink = Color(0xFFFFD1DC),
    peach = Color(0xFFFFE0C7),
    butter = Color(0xFFFFF3B0),
    mint = Color(0xFFCDF0DE),
    sky = Color(0xFFCFE6FF),
    lavender = Color(0xFFE2D8FF),
    dark = false,
)

private val DarkNotebook = Notebook(
    ink = Color(0xFFF1ECF7),
    paper = Color(0xFF25222D),
    ruled = Color(0xFF353246),
    margin = Color(0xFF8C4C5E),
    pink = Color(0xFF6B4455),
    peach = Color(0xFF6A5144),
    butter = Color(0xFF6A5F2E),
    mint = Color(0xFF3B6454),
    sky = Color(0xFF38567A),
    lavender = Color(0xFF51467A),
    dark = true,
)

val LocalNotebook = staticCompositionLocalOf { LightNotebook }

val MaterialTheme.notebook: Notebook
    @Composable get() = LocalNotebook.current

private val LightScheme = lightColorScheme(
    primary = Color(0xFFFFB3C6), onPrimary = Color(0xFF3B3A4A),
    secondary = Color(0xFFBDEBD3), onSecondary = Color(0xFF3B3A4A),
    tertiary = Color(0xFFD6C8FF), onTertiary = Color(0xFF3B3A4A),
    background = LightNotebook.paper, onBackground = LightNotebook.ink,
    surface = Color(0xFFFFFEFA), onSurface = LightNotebook.ink,
    surfaceVariant = Color(0xFFF6EEE0), onSurfaceVariant = Color(0xFF7A7787),
    outline = LightNotebook.ink, outlineVariant = Color(0xFFD9D2C6),
    error = Color(0xFFD9566B), onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB3C6), onPrimary = Color(0xFF2D1B22),
    secondary = Color(0xFFA6E3C5), onSecondary = Color(0xFF12281F),
    tertiary = Color(0xFFCBBBFF), onTertiary = Color(0xFF1F1538),
    background = DarkNotebook.paper, onBackground = DarkNotebook.ink,
    surface = Color(0xFF2E2B38), onSurface = DarkNotebook.ink,
    surfaceVariant = Color(0xFF38344A), onSurfaceVariant = Color(0xFFA9A4B8),
    outline = DarkNotebook.ink, outlineVariant = Color(0xFF45415A),
    error = Color(0xFFFF8FA3), onError = Color(0xFF3A0A14),
)

// ---- Typography: handwritten notebook ---------------------------------------

private val Hand = FontFamily(Font(R.font.patrick_hand, FontWeight.Normal))
@OptIn(ExperimentalTextApi::class)
private val Marker = FontFamily(
    Font(R.font.caveat, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.caveat, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Marker, fontSize = 56.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = Marker, fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = Marker, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = Marker, fontSize = 27.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = Hand, fontSize = 20.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Hand, fontSize = 19.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Hand, fontSize = 17.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Hand, fontSize = 15.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Hand, fontSize = 20.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontFamily = Hand, fontSize = 15.sp, lineHeight = 18.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun MyBooTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNotebook provides if (dark) DarkNotebook else LightNotebook) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Pastel avatar fills; each circle member gets a distinct one by position. */
val AvatarPastels = listOf(
    Color(0xFFFFB3C6), Color(0xFFBDEBD3), Color(0xFFBFDFFF), Color(0xFFFFE08A),
    Color(0xFFD6C8FF), Color(0xFFFFCBA4), Color(0xFFB8F0F0), Color(0xFFE6C7F0),
    Color(0xFFD4EBA0), Color(0xFFFFC9C9),
)

/** Set by the circle screen so members get distinct colours (first 10 are guaranteed unique). */
object AvatarPalette { var order: List<String> = emptyList() }

fun avatarColor(id: String): Color {
    val idx = AvatarPalette.order.indexOf(id)
    if (idx >= 0) return AvatarPastels[idx % AvatarPastels.size]
    return AvatarPastels[(id.fold(7) { h, c -> h * 31 + c.code } and 0x7fffffff) % AvatarPastels.size]
}
