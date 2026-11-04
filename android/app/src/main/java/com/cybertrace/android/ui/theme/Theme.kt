package com.cybertrace.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.cybertrace.android.ui.theme.cyan800
import com.cybertrace.android.ui.theme.cyan200
import com.cybertrace.android.ui.theme.cyan500
import com.cybertrace.android.ui.theme.cyan600
import com.cybertrace.android.ui.theme.cyan800
import com.cybertrace.android.ui.theme.grey500
import com.cybertrace.android.ui.theme.grey600
import com.cybertrace.android.ui.theme.onBackground
import com.cybertrace.android.ui.theme.onPrimary
import com.cybertrace.android.ui.theme.onSurfaceVariantLight
import com.cybertrace.android.ui.theme.teal200
import com.cybertrace.android.ui.theme.teal400
import com.cybertrace.android.ui.theme.teal800
import com.cybertrace.android.ui.theme.nightBackground
import com.cybertrace.android.ui.theme.nightSurface
import com.cybertrace.android.ui.theme.nightSurfaceVariant
import com.cybertrace.android.ui.theme.surface
import com.cybertrace.android.ui.theme.surfaceVariant
import com.cybertrace.android.ui.theme.background
import com.cybertrace.android.ui.theme.teal400
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightThemeColors = lightColorScheme(
    primary = cyan800,
    onPrimary = Color.White,
    primaryContainer = cyan500,
    onPrimaryContainer = Color.Black,
    secondary = teal400,
    onSecondary = Color.Black,
    secondaryContainer = teal200,
    onSecondaryContainer = Color.Black,
    background = background,
    onBackground = Color.Black,
    surface = surface,
    onSurface = Color.Black,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    error = error,
    onError = Color.Black,
    outline = grey500
)

private val DarkThemeColors = darkColorScheme(
    primary = cyan200,
    onPrimary = Color.Black,
    primaryContainer = cyan700,
    onPrimaryContainer = Color.White,
    secondary = teal600,
    onSecondary = Color.White,
    secondaryContainer = teal800,
    onSecondaryContainer = Color.White,
    background = nightBackground,
    onBackground = Color.White,
    surface = nightSurface,
    onSurface = Color.White,
    surfaceVariant = nightSurfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    error = error,
    onError = Color.White,
    outline = grey600
)

@Composable
fun CyberTraceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkThemeColors else LightThemeColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
