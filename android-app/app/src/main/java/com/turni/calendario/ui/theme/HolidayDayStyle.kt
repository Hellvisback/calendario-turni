package com.turni.calendario.ui.theme

/** Shared holiday outline color for Compose and native RemoteViews rendering. */
fun holidayDayColor(dark: Boolean, style: PaletteStyle = TurniThemeState.style): Long =
    turniPalette(dark, style).danger.edge
