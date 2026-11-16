package com.medireport.portal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Teal700 = Color(0xFF0F766E)
val Teal600 = Color(0xFF0D9488)
val Teal400 = Color(0xFF2DD4BF)
val Teal100 = Color(0xFFCCFBF1)
val Indigo600 = Color(0xFF4F46E5)
val Amber500 = Color(0xFFF59E0B)
val Rose500 = Color(0xFFF43F5E)
val Sky500 = Color(0xFF0EA5E9)
val Emerald500 = Color(0xFF10B981)

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Indigo600,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Amber500,
    background = Color(0xFFF4F8F8),
    onBackground = Color(0xFF0F1C1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1C1C),
    surfaceVariant = Color(0xFFE6EFEE),
    onSurfaceVariant = Color(0xFF44585A),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F6F6),
    outline = Color(0xFF7A9092),
    outlineVariant = Color(0xFFD3E0E0),
    error = Color(0xFFDC2626)
)

private val DarkColors = darkColorScheme(
    primary = Teal400,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Teal100,
    secondary = Color(0xFFA5B4FC),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF3730A3),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0B1415),
    onBackground = Color(0xFFE2EEEE),
    surface = Color(0xFF121E1F),
    onSurface = Color(0xFFE2EEEE),
    surfaceVariant = Color(0xFF1E2E2F),
    onSurfaceVariant = Color(0xFFA9C0C2),
    surfaceContainer = Color(0xFF162526),
    surfaceContainerHigh = Color(0xFF1B2D2E),
    outline = Color(0xFF6F8C8E),
    outlineVariant = Color(0xFF2B4243),
    error = Color(0xFFF87171)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun MediReportTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

fun heroBrush(dark: Boolean): Brush =
    if (dark) Brush.linearGradient(listOf(Color(0xFF0B3B3A), Color(0xFF134E4A), Color(0xFF1E3A8A)))
    else Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF0D9488), Color(0xFF4F46E5)))

@Composable
fun isAppDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
