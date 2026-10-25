package com.turni.calendario.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

@Composable
fun CalendarioTurniTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    remember(context) { TurniThemeState.initialize(context); true }
    val dark = TurniThemeState.dark
    val scheme = if (dark) darkColorScheme(
        primary = ObsidianPrimary, onPrimary = Color(0xFF24213F),
        primaryContainer = ObsidianPrimaryContainer, onPrimaryContainer = TextPrimary,
        secondary = ObsidianSecondary, tertiary = ObsidianTertiary,
        background = ObsidianBackground, onBackground = TextPrimary,
        surface = ObsidianSurface, onSurface = TextPrimary,
        surfaceVariant = ObsidianSurfaceContainer, onSurfaceVariant = TextSecondary,
        outline = ObsidianBorder
    ) else lightColorScheme(
        primary = ObsidianPrimary, onPrimary = Color.White,
        primaryContainer = ObsidianPrimaryContainer, onPrimaryContainer = Color(0xFF18357A),
        secondary = ObsidianSecondary, tertiary = ObsidianTertiary,
        background = ObsidianBackground, onBackground = TextPrimary,
        surface = ObsidianSurface, onSurface = TextPrimary,
        surfaceVariant = ObsidianSurfaceContainer, onSurfaceVariant = TextSecondary,
        outline = ObsidianBorder
    )
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            window.decorView.setBackgroundColor(scheme.background.toArgb())
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
