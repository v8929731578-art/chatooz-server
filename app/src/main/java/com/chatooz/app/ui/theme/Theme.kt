package com.chatooz.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary          = IndigoPrimary,
    onPrimary        = Color.White,
    primaryContainer = IndigoDark,
    secondary        = VioletAccent,
    onSecondary      = Color.White,
    tertiary         = EmeraldAccent,
    background       = DarkBg,
    surface          = DarkSurface,
    surfaceVariant   = DarkCard,
    onBackground     = TextPrimDark,
    onSurface        = TextPrimDark,
    onSurfaceVariant = TextSecDark,
    outline          = DarkDivider,
    error            = RoseAccent
)

private val LightColorScheme = lightColorScheme(
    primary          = IndigoPrimary,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    secondary        = VioletAccent,
    onSecondary      = Color.White,
    tertiary         = EmeraldAccent,
    background       = LightBg,
    surface          = LightSurface,
    surfaceVariant   = LightCard,
    onBackground     = TextPrimLight,
    onSurface        = TextPrimLight,
    onSurfaceVariant = TextSecLight,
    outline          = LightDivider,
    error            = RoseAccent
)

@Composable
fun ChatoozTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = (if (darkTheme) DarkBg else LightBg).toArgb()
            window.navigationBarColor = (if (darkTheme) DarkSurface else LightSurface).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
