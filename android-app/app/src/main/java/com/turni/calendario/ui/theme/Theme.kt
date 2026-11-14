package com.turni.calendario.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

@Composable
fun CalendarioTurniTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    SideEffect { TurniThemeState.followSystem(systemDark) }
    val dark = TurniThemeState.dark
    val style = TurniThemeState.style
    val scheme = remember(dark, style) {
        val p = turniPalette(dark, style)
        val inverse = turniPalette(!dark, style)
        (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = Color(p.primary), onPrimary = Color(p.onPrimary),
            primaryContainer = Color(p.primaryContainer), onPrimaryContainer = Color(p.onPrimaryContainer),
            inversePrimary = Color(inverse.primary),
            secondary = Color(p.secondary), onSecondary = Color(p.onSecondary),
            secondaryContainer = Color(p.secondaryContainer), onSecondaryContainer = Color(p.onSecondaryContainer),
            tertiary = Color(p.tertiary), onTertiary = Color(p.onTertiary),
            tertiaryContainer = Color(p.tertiaryContainer), onTertiaryContainer = Color(p.onTertiaryContainer),
            background = Color(p.background), onBackground = Color(p.text),
            surface = Color(p.surface), onSurface = Color(p.text),
            surfaceVariant = Color(p.container), onSurfaceVariant = Color(p.secondaryText),
            surfaceTint = Color(p.primary),
            surfaceBright = Color(p.surfaceHigh), surfaceDim = Color(p.background),
            surfaceContainerLowest = Color(p.background), surfaceContainerLow = Color(p.surface),
            surfaceContainer = Color(p.container), surfaceContainerHigh = Color(p.surfaceHigh),
            surfaceContainerHighest = Color(p.surfaceHighest), outlineVariant = Color(p.border),
            inverseSurface = Color(inverse.surface), inverseOnSurface = Color(inverse.text),
            error = Color(p.destructive), onError = Color(p.onDestructive),
            errorContainer = Color(p.danger.fill), onErrorContainer = Color(p.danger.ink),
            outline = Color(p.outline)
        )
    }
    // Update window chrome only when its appearance changes, not on unrelated recompositions.
    DisposableEffect(context, dark, scheme.background) {
        (context as? Activity)?.window?.let { window ->
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            window.decorView.setBackgroundColor(scheme.background.toArgb())
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        onDispose { }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
