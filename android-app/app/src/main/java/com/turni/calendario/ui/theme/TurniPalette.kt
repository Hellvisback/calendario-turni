package com.turni.calendario.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver

enum class PaletteStyle(val label: String) {
    GRAPHITE("Grafite e cobalto")
}

/** Earlier studies used IRIS/ELECTRIC; both upgrade to the approved design. */
internal fun resolvePaletteStyle(stored: String?): PaletteStyle =
    PaletteStyle.values().firstOrNull { it.name == stored } ?: PaletteStyle.GRAPHITE

internal data class SemanticTone(val fill: Long, val ink: Long, val edge: Long)

/** The same roles drive Compose, the calendar canvas and launcher widgets. */
internal data class TurniPalette(
    val background: Long, val surface: Long, val container: Long,
    val surfaceHigh: Long, val surfaceHighest: Long,
    val border: Long, val divider: Long, val outline: Long,
    val primary: Long, val onPrimary: Long, val primaryInk: Long, val primaryContainer: Long,
    val onPrimaryContainer: Long, val primarySubtle: Long,
    val secondary: Long, val onSecondary: Long, val secondaryContainer: Long, val onSecondaryContainer: Long,
    val tertiary: Long, val onTertiary: Long, val tertiaryContainer: Long, val onTertiaryContainer: Long,
    val text: Long, val secondaryText: Long, val mutedText: Long,
    val calendar: Long, val calendarWeekend: Long, val calendarToday: Long,
    val dock: Long, val dockInk: Long, val dockSelected: Long, val dockOnSelected: Long,
    val onCall: SemanticTone, val overtime: SemanticTone, val travel: SemanticTone,
    val warning: SemanticTone, val success: SemanticTone, val danger: SemanticTone,
    val destructive: Long, val onDestructive: Long
)

// One accent family drives actions throughout the app, including the dock and today.
private const val CobaltNight = 0xFF4668EC
private const val CobaltDay = 0xFF3456D5
private val GraphiteNight = TurniPalette(
    background = 0xFF10161B, surface = 0xFF192229, container = 0xFF25313A,
    surfaceHigh = 0xFF25313A, surfaceHighest = 0xFF303E48,
    border = 0xFF35424B, divider = 0xFF35424B, outline = 0xFF8496A4,
    primary = CobaltNight, onPrimary = 0xFFFFFFFF, primaryInk = 0xFFA8BBFF, primaryContainer = 0xFF293B78,
    onPrimaryContainer = 0xFFE3EAFF, primarySubtle = 0xFF202E55,
    secondary = 0xFFE3D4B3, onSecondary = 0xFF33291A,
    secondaryContainer = 0xFF3E372B, onSecondaryContainer = 0xFFF4E7CF,
    tertiary = 0xFFC9B9F2, onTertiary = 0xFF2E2049,
    tertiaryContainer = 0xFF372F49, onTertiaryContainer = 0xFFEDE5FF,
    text = 0xFFF3F6F8, secondaryText = 0xFFB2C0CB, mutedText = 0xFFB2C0CB,
    calendar = 0xFF192229, calendarWeekend = 0xFF202A32, calendarToday = 0xFF233666,
    dock = 0xFF222A32, dockInk = 0xFFE4EDF4, dockSelected = CobaltNight, dockOnSelected = 0xFFFFFFFF,
    onCall = SemanticTone(0xFF3C3425, 0xFFF1D08A, 0xFFF1D08A),
    overtime = SemanticTone(0xFF263E4C, 0xFF9DD7F1, 0xFF9DD7F1),
    travel = SemanticTone(0xFFBAA4E8, 0xFF231338, 0xFFD1BFF5),
    warning = SemanticTone(0xFF3C3425, 0xFFF1D08A, 0xFFF1D08A),
    success = SemanticTone(0xFF253E36, 0xFFB9DACA, 0xFF97C6BF),
    danger = SemanticTone(0xFF482930, 0xFFFF9DA5, 0xFFFF9DA5),
    destructive = 0xFFFF9DA5, onDestructive = 0xFF491A24
)

private val GraphiteDay = TurniPalette(
    background = 0xFFF3F6F8, surface = 0xFFFFFFFF, container = 0xFFE7EDF1,
    surfaceHigh = 0xFFE7EDF1, surfaceHighest = 0xFFDCE5EB,
    border = 0xFFD4DEE5, divider = 0xFFD4DEE5, outline = 0xFF718592,
    primary = CobaltDay, onPrimary = 0xFFFFFFFF, primaryInk = 0xFF2C4BC3, primaryContainer = 0xFFDFE6FF,
    onPrimaryContainer = 0xFF173585, primarySubtle = 0xFFEDF1FF,
    secondary = 0xFF6C5227, onSecondary = 0xFFFFFFFF,
    secondaryContainer = 0xFFF2E7CF, onSecondaryContainer = 0xFF49361A,
    tertiary = 0xFF695195, onTertiary = 0xFFFFFFFF,
    tertiaryContainer = 0xFFECE4F6, onTertiaryContainer = 0xFF49366C,
    text = 0xFF182D3A, secondaryText = 0xFF455A6A, mutedText = 0xFF455A6A,
    calendar = 0xFFFFFFFF, calendarWeekend = 0xFFE2E9EF, calendarToday = 0xFFC6D5FF,
    dock = 0xFFDCE4EA, dockInk = 0xFF263743, dockSelected = CobaltDay, dockOnSelected = 0xFFFFFFFF,
    onCall = SemanticTone(0xFFFFF0D4, 0xFF9E5405, 0xFFD98218),
    overtime = SemanticTone(0xFFDCEEF6, 0xFF1D617D, 0xFF1D617D),
    travel = SemanticTone(0xFF63438E, 0xFFFFFFFF, 0xFF63438E),
    warning = SemanticTone(0xFFF5E7C9, 0xFF795312, 0xFF795312),
    success = SemanticTone(0xFFDCECE3, 0xFF2F654E, 0xFF397F70),
    danger = SemanticTone(0xFFFBE5E8, 0xFFB82B42, 0xFFB82B42),
    destructive = 0xFFB82B42, onDestructive = 0xFFFFFFFF
)

internal fun turniPalette(dark: Boolean, style: PaletteStyle = TurniThemeState.style): TurniPalette =
    when (style) {
        PaletteStyle.GRAPHITE -> if (dark) GraphiteNight else GraphiteDay
    }
internal val currentTurniPalette get() = turniPalette(TurniThemeState.dark)

internal fun contrastRatio(a: Color, b: Color): Float {
    val first = a.luminance()
    val second = b.luminance()
    return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
}

internal fun readableInk(background: Color, preferred: Color): Color =
    if (contrastRatio(background, preferred) >= 4.5f) preferred
    else if (contrastRatio(background, Color.Black) >= contrastRatio(background, Color.White)) Color.Black
    else Color.White

internal fun readableIndicator(background: Color, preferred: Color): Color =
    if (contrastRatio(background, preferred) >= 3f) preferred else readableInk(background, preferred)

/** Adjust the value of a category marker while keeping its hue identifiable. */
internal fun readablePigment(background: Color, preferred: Color): Color {
    if (contrastRatio(background, preferred) >= 3f) return preferred
    val target = readableInk(background, preferred)
    var low = 0f
    var high = 1f
    repeat(8) {
        val middle = (low + high) / 2f
        if (contrastRatio(background, lerp(preferred, target, middle)) >= 3.04f) high = middle
        else low = middle
    }
    return lerp(preferred, target, high)
}

/** Decorative chart tints stay behind labels without reducing their contrast. */
internal fun readableChartTint(backdrop: Color, tint: Color, labelInk: Color, maxOpacity: Float): Color {
    fun blend(opacity: Float) = tint.copy(alpha = opacity).compositeOver(backdrop)
    val full = blend(maxOpacity)
    if (contrastRatio(full, labelInk) >= 4.5f) return full
    var low = 0f
    var high = maxOpacity
    repeat(8) {
        val middle = (low + high) / 2f
        if (contrastRatio(blend(middle), labelInk) >= 4.6f) low = middle else high = middle
    }
    return blend(low)
}

internal data class ShiftColorStyle(
    val codeFill: Color, val codeInk: Color, val timeFill: Color, val timeInk: Color, val accent: Color
)

/** Palette roles are fixed, not inferred from a user's shift name or approximate hue. */
private data class ShiftTone(
    val nightFill: Long, val nightInk: Long,
    val dayFill: Long, val dayInk: Long, val dayChart: Long
) {
    fun fill(dark: Boolean) = Color(if (dark) nightFill else dayFill)
    fun ink(dark: Boolean) = Color(if (dark) nightInk else dayInk)
    fun chart(dark: Boolean) = Color(if (dark) nightFill else dayChart)
}

private val MorningTone = ShiftTone(0xFFDBC38E, 0xFF302611, 0xFFF2D18C, 0xFF513B12, 0xFF8B651D)
private val AfternoonTone = ShiftTone(0xFFD9AE9B, 0xFF38241B, 0xFFEDB8A8, 0xFF5F3928, 0xFFA16346)
private val EveningTone = ShiftTone(0xFFC6AAC4, 0xFF2E2131, 0xFFDAB8D5, 0xFF533B57, 0xFF8A5F88)
private val NightTone = ShiftTone(0xFFAEBCE4, 0xFF202A43, 0xFFBFCDF0, 0xFF35466D, 0xFF5E70A5)
private val RecoveryTone = ShiftTone(0xFF97C6BF, 0xFF16322E, 0xFFABD7CA, 0xFF215043, 0xFF397F70)
private val RestTone = ShiftTone(0xFFB8C1C8, 0xFF28313A, 0xFFCCD8E1, 0xFF3A4B58, 0xFF5E7686)
private val LeaveTone = ShiftTone(0xFFB9CB9A, 0xFF27351A, 0xFFCDDEA5, 0xFF3A5120, 0xFF607C33)
private val DaytimeTone = ShiftTone(0xFF9EC4DE, 0xFF163247, 0xFFAED5EC, 0xFF224C68, 0xFF3D7A9C)

// Exact aliases preserve saved models and backups. Arbitrary custom RGB values are never remapped.
// Both approved fills are aliases so repeated rendering and theme switches are idempotent.
private val shiftTonesByColor: Map<Int, ShiftTone> = buildMap {
    fun register(tone: ShiftTone, vararg legacy: Long) {
        put(tone.nightFill.toInt(), tone)
        put(tone.dayFill.toInt(), tone)
        legacy.forEach { put(it.toInt(), tone) }
    }
    register(MorningTone, 0xFFF0DEB2, 0xFFF2C46D)
    register(AfternoonTone, 0xFFF1D7C9, 0xFFEE9C9C)
    register(EveningTone, 0xFFE8D8E6)
    register(NightTone, 0xFFDDE4F6, 0xFFBAAEF6)
    register(RecoveryTone, 0xFFD5EAE4, 0xFF83CDB7)
    register(RestTone, 0xFFE3E8EC, 0xFFAAB9CE)
    register(LeaveTone, 0xFFE2ECCF, 0xFFB4D995)
    register(DaytimeTone, 0xFFD6E8F4, 0xFF94C4EF)
}

private fun shiftTone(source: Color, definitionId: String? = null): ShiftTone? {
    val argb = source.toArgb() or 0xFF000000.toInt()
    // These two built-ins shared a legacy swatch with a different semantic model.
    // Only the exact original ID+color pair gets the category-specific display color.
    if (definitionId == "giornaliero" && argb == 0xFF83CDB7.toInt()) return DaytimeTone
    if (definitionId == "ferie" && argb == 0xFF94C4EF.toInt()) return LeaveTone
    return shiftTonesByColor[argb]
}

/** Display-only: never write this result back to a model's stored colorHex. */
fun shiftDisplayColor(
    source: Color, dark: Boolean = TurniThemeState.dark, definitionId: String? = null
): Color = shiftTone(source, definitionId)?.fill(dark) ?: Color(source.toArgb() or 0xFF000000.toInt())

/** Stronger markers on light surfaces; the stored/user-selected color remains unchanged. */
fun shiftChartColor(
    source: Color, dark: Boolean = TurniThemeState.dark, definitionId: String? = null
): Color = shiftTone(source, definitionId)?.chart(dark)
    ?: readablePigment(Color(turniPalette(dark).surface), Color(source.toArgb() or 0xFF000000.toInt()))

/** Resolve codes and times on the approved full-color badges, with readable preview states. */
internal fun shiftColorStyle(
    source: Color, dark: Boolean, backdrop: Color, subdued: Boolean = false,
    style: PaletteStyle = TurniThemeState.style, definitionId: String? = null
): ShiftColorStyle {
    val palette = turniPalette(dark, style)
    val tone = shiftTone(source, definitionId)
    val original = tone?.fill(dark) ?: Color(source.toArgb() or 0xFF000000.toInt())
    // Light previews retain category hue; only the fill recedes, never the text.
    val fill = if (subdued) lerp(backdrop, original, if (dark) .30f else .60f) else original
    val preferred = if (subdued && dark) Color(palette.mutedText) else tone?.ink(dark) ?: Color(palette.text)
    val ink = readableInk(fill, preferred)
    // A full-color badge needs no extra dark stripe; preserve its clean, continuous fill.
    return ShiftColorStyle(fill, ink, fill, ink, fill)
}

internal data class MetricColorStyle(val fill: Color, val ink: Color, val secondaryInk: Color)

internal fun metricColorStyle(
    source: Color, dark: Boolean, onCall: Boolean = false, style: PaletteStyle = TurniThemeState.style
): MetricColorStyle {
    val palette = turniPalette(dark, style)
    val tone = shiftTone(source)
    val raw = Color(source.toArgb() or 0xFF000000.toInt())
    val fill = when {
        onCall -> Color(palette.onCall.fill)
        tone != null -> tone.fill(dark)
        dark -> raw
        else -> lerp(raw, Color(palette.surface), .35f)
    }
    val preferred = when {
        onCall -> Color(palette.onCall.ink)
        tone != null -> tone.ink(dark)
        else -> Color(palette.text)
    }
    val ink = readableInk(fill, preferred)
    return MetricColorStyle(fill, ink, ink)
}
