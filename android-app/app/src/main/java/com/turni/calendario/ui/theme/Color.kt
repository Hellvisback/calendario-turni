package com.turni.calendario.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object TurniThemeState {
    private var applicationContext:Context?=null
    fun restoreStoredMode(value:String?) {
        if(value==null) {
            applicationContext?.let { com.turni.calendario.repository.ShiftRepository.appPreferences=com.turni.calendario.repository.ShiftRepository.appPreferences.copy(themeMode=mode.name) }
            return
        }
        val restored=ThemeMode.values().firstOrNull{it.name==value}?:return
        applicationContext?.let{setMode(it,restored)}
    }
    var mode by mutableStateOf(ThemeMode.AUTOMATIC)
        private set
    var style by mutableStateOf(PaletteStyle.GRAPHITE)
        private set
    private var systemDark by mutableStateOf(true)
    val dark: Boolean get() = mode.isDark(systemDark)

    fun storedMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences("turnisync_theme", 0)
        return resolveThemeMode(prefs.getString("mode", null), if (prefs.contains("dark")) prefs.getBoolean("dark", true) else null)
    }
    fun isDark(context: Context): Boolean = storedMode(context).isDark(
        context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
    fun storedStyle(context: Context): PaletteStyle = resolvePaletteStyle(
        context.getSharedPreferences("turnisync_theme", 0).getString("palette_style", null)
    )
    fun initialize(context: Context) {
        applicationContext=context.applicationContext
        mode = storedMode(context)
        style = storedStyle(context)
        // Retire the experimental palette keys without touching the user's theme mode or shift data.
        val prefs = context.getSharedPreferences("turnisync_theme", 0)
        if (prefs.getString("palette_style", null) != style.name) {
            prefs.edit().putString("palette_style", style.name).apply()
        }
        refreshSystem(context)
    }
    fun refreshSystem(context: Context) {
        systemDark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    fun followSystem(value: Boolean) { systemDark = value }
    fun setMode(context: Context, value: ThemeMode) {
        refreshSystem(context)
        mode = value
        com.turni.calendario.repository.ShiftRepository.appPreferences=com.turni.calendario.repository.ShiftRepository.appPreferences.copy(themeMode=value.name)
        context.getSharedPreferences("turnisync_theme", 0).edit().putString("mode", value.name).putBoolean("dark", dark).apply()
        com.turni.calendario.widget.WidgetUpdateManager.notifyWidgetsChanged(context)
    }
    fun setDark(context: Context, value: Boolean) = setMode(context, if (value) ThemeMode.DARK else ThemeMode.LIGHT)
    fun setStyle(context: Context, value: PaletteStyle) {
        if (style == value) return
        style = value
        context.getSharedPreferences("turnisync_theme", 0).edit().putString("palette_style", value.name).apply()
        com.turni.calendario.widget.WidgetUpdateManager.notifyWidgetsChanged(context)
    }
}
val ObsidianBackground get() = Color(currentTurniPalette.background)
val ObsidianSurface get() = Color(currentTurniPalette.surface)
val ObsidianSurfaceContainer get() = Color(currentTurniPalette.container)
val ObsidianSurfaceHigh get() = Color(currentTurniPalette.surfaceHigh)
val ObsidianSurfaceHighest get() = Color(currentTurniPalette.surfaceHighest)
val ObsidianBorder get() = Color(currentTurniPalette.border)
val ObsidianBorderSubtle get() = Color(currentTurniPalette.divider)
val ObsidianBorderActive get() = BrandInk
val ObsidianPrimary get() = Color(currentTurniPalette.primary)
// Standalone labels/icons need a lighter blue on dark surfaces than filled actions.
val BrandInk get() = Color(currentTurniPalette.primaryInk)
val ObsidianPrimaryContainer get() = Color(currentTurniPalette.primaryContainer)
val ObsidianPrimarySubtle get() = Color(currentTurniPalette.primarySubtle)
val ObsidianSecondary get() = Color(currentTurniPalette.secondary)
val ObsidianTertiary get() = Color(currentTurniPalette.tertiary)
val TextPrimary get() = Color(currentTurniPalette.text)
val TextSecondary get() = Color(currentTurniPalette.secondaryText)
val TextMuted get() = Color(currentTurniPalette.mutedText)
val CalendarSurface get() = Color(currentTurniPalette.calendar)
val CalendarWeekend get() = Color(currentTurniPalette.calendarWeekend)
val CalendarToday get() = Color(currentTurniPalette.calendarToday)
val DockBody get() = Color(currentTurniPalette.dock)
val DockInk get() = Color(currentTurniPalette.dockInk)
val DockSelected get() = Color(currentTurniPalette.dockSelected)
val DockOnSelected get() = Color(currentTurniPalette.dockOnSelected)
val OnBrand get() = Color(currentTurniPalette.onPrimary)
val PrimaryGradient get() = Brush.horizontalGradient(listOf(ObsidianPrimary, Color(currentTurniPalette.primaryInk)))
val AmberGradient = Brush.horizontalGradient(listOf(Color(0xFFD97706), Color(0xFFF2C46D)))
val EmeraldGradient = Brush.horizontalGradient(listOf(Color(0xFF166B56), Color(0xFF83CDB7)))
val PurpleGradient = Brush.horizontalGradient(listOf(Color(0xFF655195), Color(0xFFBAAEF6)))
val RoseGradient = Brush.horizontalGradient(listOf(Color(0xFFB94059), Color(0xFFEE9C9C)))
val ColorMattina = Color(0xFFF2C46D)
val ColorPomeriggio = Color(0xFFEE9C9C)
val ColorNotte = Color(0xFFBAAEF6)
val ColorRiposo = Color(0xFFAAB9CE)
val ColorFerie = Color(0xFF94C4EF)
val ColorSmontoNotte = Color(0xFF83CDB7)
val ColorGiornaliero = Color(0xFF83CDB7)
val ColorMatDom = Color(0xFFF2C46D)
val ColorUffici = Color(0xFFAAB9CE)
val ColorDonazione = Color(0xFFEE9C9C)
val ColorMalattia = Color(0xFFB4D995)
val ColorReperibilita get() = Color(currentTurniPalette.onCall.ink)
val ColorTrasferta get() = Color(currentTurniPalette.travel.ink)

val ColorNegative get() = Color(currentTurniPalette.danger.ink)
