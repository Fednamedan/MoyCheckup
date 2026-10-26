package ru.moycheckup.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.moycheckup.data.RemType

/**
 * «Ночное сияние»: глубокий тёмный фон, движущееся сияние, стеклянные карточки, неоновая шкала боли.
 * Тема только тёмная: свечение и стекло хорошо выглядят именно на тёмном.
 */
@Immutable
data class Extra(
    val s0: Color, val s1: Color, val s2: Color, val s3: Color,
    val bodyFill: Color, val bodyLine: Color,
    val glass: Color, val glassLine: Color,
    val deep: Color, val mid: Color,
    val mint: Color, val violet: Color, val pink: Color, val blue: Color,
    val pill: Color, val doc: Color, val lab: Color, val other: Color,
    val dark: Boolean,
) {
    fun sev(n: Int): Color = when {
        n <= 0 -> s0
        n <= 3 -> s1
        n <= 6 -> s2
        else -> s3
    }

    /** Цвет текста на плашке с цветом боли. */
    fun onSev(n: Int): Color = if (n >= 7) Color.White else Color(0xFF1B1206)

    fun type(t: RemType): Color = when (t) {
        RemType.PILL -> pill
        RemType.DOC -> doc
        RemType.LAB -> lab
        RemType.OTHER -> other
    }
}

val LocalExtra = staticCompositionLocalOf<Extra> { error("CheckupTheme не подключена") }

private val NightExtra = Extra(
    s0 = Color(0xFF5AE0B0), s1 = Color(0xFFFFD166), s2 = Color(0xFFFF8A3D), s3 = Color(0xFFFF3D6E),
    bodyFill = Color(0x14FFFFFF), bodyLine = Color(0x38FFFFFF),
    glass = Color(0x14FFFFFF), glassLine = Color(0x1FFFFFFF),
    deep = Color(0xFF070B16), mid = Color(0xFF0E1533),
    mint = Color(0xFF3DF2C4), violet = Color(0xFF7B5CFF), pink = Color(0xFFFF5C8A), blue = Color(0xFF2AA7FF),
    pill = Color(0xFF3DF2C4), doc = Color(0xFF5AA9FF), lab = Color(0xFFC58BFF), other = Color(0xFF9AA8C7),
    dark = true,
)

private val NightScheme = darkColorScheme(
    primary = Color(0xFF3DF2C4), onPrimary = Color(0xFF03231C),
    primaryContainer = Color(0x333DF2C4), onPrimaryContainer = Color(0xFF3DF2C4),
    secondary = Color(0xFF3DF2C4), onSecondary = Color(0xFF03231C),
    secondaryContainer = Color(0x403DF2C4), onSecondaryContainer = Color(0xFF3DF2C4),
    tertiary = Color(0xFF7B5CFF),
    background = Color(0xFF070B16), onBackground = Color(0xFFEAF2FF),
    surface = Color(0xFF111A36), onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0x24FFFFFF), onSurfaceVariant = Color(0xFF9AA8C7),
    surfaceContainerLowest = Color(0xFF0B1228), surfaceContainerLow = Color(0xFF0F1830),
    surfaceContainer = Color(0xFF121B38), surfaceContainerHigh = Color(0xFF16213F),
    surfaceContainerHighest = Color(0xFF1B2850),
    outline = Color(0x33FFFFFF), outlineVariant = Color(0x1FFFFFFF),
    error = Color(0xFFFF6B8A), onError = Color(0xFF330A15),
)

private val Typo = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun CheckupTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtra provides NightExtra) {
        MaterialTheme(
            colorScheme = NightScheme,
            typography = Typo,
            shapes = AppShapes,
            content = content,
        )
    }
}
