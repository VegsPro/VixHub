package com.vixhub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF00D4FF),
    onPrimary = Color(0xFF003344),
    primaryContainer = Color(0xFF004D66),
    onPrimaryContainer = Color(0xFFB3EFFF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00332E),
    secondaryContainer = Color(0xFF004D47),
    onSecondaryContainer = Color(0xFFA7F0E8),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF0A1628),
    onBackground = Color(0xFFE0E7FF),
    surface = Color(0xFF111C2E),
    onSurface = Color(0xFFE0E7FF),
    surfaceVariant = Color(0xFF1A2740),
    onSurfaceVariant = Color(0xFFB0BEC5),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000),
    outline = Color(0xFF4A5A70)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A8E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3E5FC),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF00695C),
    background = Color(0xFFF5F9FC),
    onBackground = Color(0xFF0A1628),
    surface = Color.White,
    onSurface = Color(0xFF0A1628),
    surfaceVariant = Color(0xFFE8EEF5),
    onSurfaceVariant = Color(0xFF4A5A70),
    error = Color(0xFFB00020),
    outline = Color(0xFF90A4AE)
)

@Composable
fun VixHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
