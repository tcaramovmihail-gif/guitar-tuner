package ru.eurovafel.guitartuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StageColors = darkColorScheme(
    primary = Color(0xFF8AE9C1),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFFB3CCC1),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFDDE5E0),
    surface = Color(0xFF121A16),
    onSurface = Color(0xFFDDE5E0),
    surfaceVariant = Color(0xFF2A3530),
    onSurfaceVariant = Color(0xFFBBC9C1),
)

@Composable
fun GuitarTunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StageColors, content = content)
}
