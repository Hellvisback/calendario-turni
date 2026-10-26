package com.turni.calendario.ui.theme

enum class ThemeMode(val label: String, val icon: String) {
    AUTOMATIC("Automatico", "smartphone"), LIGHT("Chiaro", "sun"), DARK("Scuro", "moon");
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        AUTOMATIC -> systemDark
        DARK -> true
        LIGHT -> false
    }
}

/** Keep explicit choices from previous APKs; new installations follow Android. */
internal fun resolveThemeMode(stored: String?, legacyDark: Boolean?): ThemeMode =
    ThemeMode.values().firstOrNull { it.name == stored }
        ?: if (stored == null && legacyDark != null) { if (legacyDark) ThemeMode.DARK else ThemeMode.LIGHT } else ThemeMode.AUTOMATIC
