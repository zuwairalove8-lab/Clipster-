package com.cybertrace.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Restrained palette: deep slate surfaces, teal accent.
val Teal = Color(0xFF2DD4BF)
val TealDark = Color(0xFF0F766E)
val TealDeep = Color(0xFF115E59)

val SlateBackground = Color(0xFFF4F6F5)
val SlateSurface = Color(0xFFFFFFFF)
val SlateSurfaceVariant = Color(0xFFEDF1F0)
val SlateText = Color(0xFF0F1720)
val SlateMuted = Color(0xFF5B6B7C)
val SlateOutline = Color(0xFFCBD5E1)

val NightBackground = Color(0xFF0B1220)
val NightSurface = Color(0xFF152135)
val NightSurfaceVariant = Color(0xFF1E293B)
val NightText = Color(0xFFE8EEF8)
val NightMuted = Color(0xFF93A4BD)
val NightOutline = Color(0xFF24344F)

val WarningAmber = Color(0xFFFBBF24)
val DangerRed = Color(0xFFE5484D)

private val LightColors = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Teal,
    onSecondary = Color(0xFF04211D),
    secondaryContainer = Color(0xFFD5F5F0),
    onSecondaryContainer = Color(0xFF042F2E),
    background = SlateBackground,
    onBackground = SlateText,
    surface = SlateSurface,
    onSurface = SlateText,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = SlateOutline,
    error = DangerRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF04121B),
    primaryContainer = TealDeep,
    onPrimaryContainer = Color(0xFFB8F5EA),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF04121B),
    secondaryContainer = Color(0xFF0F3B36),
    onSecondaryContainer = Color(0xFFB8F5EA),
    background = NightBackground,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightMuted,
    outline = NightOutline,
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF1A0505),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.5.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
    ),
)

@Composable
fun CyberTraceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
