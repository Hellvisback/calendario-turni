package com.turni.calendario.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ObsidianPrimary,
    onPrimary = ObsidianBackground,
    primaryContainer = ObsidianPrimaryContainer,
    onPrimaryContainer = TextPrimary,
    secondary = ObsidianSecondary,
    onSecondary = ObsidianBackground,
    tertiary = ObsidianTertiary,
    onTertiary = ObsidianBackground,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceContainer,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder
)

@Composable
fun CalendarioTurniTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
