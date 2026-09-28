package com.oscarriva.solomoderoller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SoloModeColorScheme = darkColorScheme(
    primary = NeonAmber,
    onPrimary = Color.Black,
    background = NoirBackground,
    onBackground = NoirOnBackground,
    surface = NoirSurface,
    onSurface = NoirOnBackground,
    surfaceVariant = NoirSurfaceVariant,
    onSurfaceVariant = NoirOnSurfaceMuted
)

@Composable
fun SoloModeRollerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoloModeColorScheme,
        content = content
    )
}
