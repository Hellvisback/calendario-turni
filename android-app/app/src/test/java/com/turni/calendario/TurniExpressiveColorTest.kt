package com.turni.calendario

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.turni.calendario.ui.theme.PaletteStyle
import com.turni.calendario.ui.theme.metricColorStyle
import com.turni.calendario.ui.theme.readableInk
import com.turni.calendario.ui.theme.readablePigment
import com.turni.calendario.ui.theme.readableChartTint
import com.turni.calendario.ui.theme.shiftColorStyle
import com.turni.calendario.ui.theme.shiftDisplayColor
import com.turni.calendario.ui.theme.shiftChartColor
import com.turni.calendario.ui.theme.resolvePaletteStyle
import com.turni.calendario.ui.theme.turniPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class TurniExpressiveColorTest {
    @Test
    fun experimentalStylePreferencesUpgradeToApprovedPalette() {
        for (stored in listOf(null, "IRIS", "ELECTRIC", "GRAPHITE", "unknown")) {
            assertEquals(PaletteStyle.GRAPHITE, resolvePaletteStyle(stored))
        }
    }

    @Test
    fun approvedBuiltInColorsStayStableAcrossRepeatedRenderingAndThemeSwitches() {
        val sources = listOf(
            Triple(0xFFF2C46D, 0xFFDBC38E, 0xFFF2D18C),
            Triple(0xFFEE9C9C, 0xFFD9AE9B, 0xFFEDB8A8),
            Triple(0xFFBAAEF6, 0xFFAEBCE4, 0xFFBFCDF0),
            Triple(0xFF83CDB7, 0xFF97C6BF, 0xFFABD7CA),
            Triple(0xFFAAB9CE, 0xFFB8C1C8, 0xFFCCD8E1),
            Triple(0xFFB4D995, 0xFFB9CB9A, 0xFFCDDEA5),
            Triple(0xFF94C4EF, 0xFF9EC4DE, 0xFFAED5EC)
        )
        for ((source, darkFill, lightFill) in sources) {
            for (dark in listOf(false, true)) {
                val expected = Color(if (dark) darkFill else lightFill)
                assertEquals(expected, shiftDisplayColor(Color(source), dark))
                assertEquals(expected, shiftDisplayColor(expected, dark))
                val opposite = shiftDisplayColor(expected, !dark)
                assertEquals(expected, shiftDisplayColor(opposite, dark))
                assertContrast("chart alias $source/$dark", shiftChartColor(Color(source), dark),
                    Color(turniPalette(dark).surface), 3.0)
            }
        }
    }

    @Test
    fun previousLightSwatchesStillResolveToTheApprovedDarkColors() {
        val aliases = listOf(
            0xFFF0DEB2 to 0xFFDBC38E, 0xFFF1D7C9 to 0xFFD9AE9B,
            0xFFE8D8E6 to 0xFFC6AAC4, 0xFFDDE4F6 to 0xFFAEBCE4,
            0xFFD5EAE4 to 0xFF97C6BF, 0xFFE3E8EC to 0xFFB8C1C8,
            0xFFE2ECCF to 0xFFB9CB9A, 0xFFD6E8F4 to 0xFF9EC4DE
        )
        for ((oldLight, dark) in aliases) {
            assertEquals(Color(dark), shiftDisplayColor(Color(oldLight), true))
            assertEquals(shiftDisplayColor(Color(dark), false), shiftDisplayColor(Color(oldLight), false))
        }
    }

    @Test
    fun lightPreviewKeepsItsHueAndWeekendRemainsDistinct() {
        val palette = turniPalette(false)
        val weekday = Color(palette.calendar)
        val weekend = Color(palette.calendarWeekend)
        assertContrast("weekend surface separation", weekday, weekend, 1.20)
        for (source in listOf(0xFFF2C46D, 0xFFEE9C9C, 0xFFBAAEF6, 0xFF83CDB7, 0xFFAAB9CE, 0xFFB4D995, 0xFF94C4EF)) {
            for (base in listOf(weekday, weekend)) {
                val active = shiftColorStyle(Color(source), false, base)
                val preview = shiftColorStyle(Color(source), false, base, subdued=true)
                assertTrue("preview retains visible color", distanceSquared(preview.codeFill, base) > distanceSquared(active.codeFill, base) * .25)
                assertContrast("preview ink", preview.codeInk, preview.codeFill)
            }
        }
    }

    @Test
    fun sharedLegacySwatchesUseExactBuiltInIdentityWithoutRemappingCustomColors() {
        assertEquals(Color(0xFFB9CB9A), shiftDisplayColor(Color(0xFF94C4EF), true, "ferie"))
        assertEquals(Color(0xFF9EC4DE), shiftDisplayColor(Color(0xFF83CDB7), true, "giornaliero"))
        assertEquals(Color(0xFFCDDEA5), shiftDisplayColor(Color(0xFF94C4EF), false, "ferie"))
        assertEquals(Color(0xFFAED5EC), shiftDisplayColor(Color(0xFF83CDB7), false, "giornaliero"))
        for (id in listOf(null, "ferie", "giornaliero", "my-model")) {
            for (dark in listOf(false, true)) {
                // Include a color only one channel away from a preset: no approximate matching.
                for (custom in listOf(Color(0xFF94C4EE), Color(0xFF123456), Color(0xFFDA4673), Color.White, Color.Black)) {
                    assertEquals("custom color for $id/$dark", custom, shiftDisplayColor(custom, dark, id))
                }
            }
        }
    }

    @Test
    fun chartDecorationDoesNotWashOutLabelsWithExtremeCustomColors() {
        for (style in PaletteStyle.values()) for (dark in listOf(false, true)) {
            val palette = turniPalette(dark, style)
            val fill = Color(palette.secondaryContainer)
            val ink = Color(palette.onSecondaryContainer)
            for (pigment in listOf(Color.White, Color.Black, Color.Yellow, Color.Blue, Color.Magenta)) {
                val chart = readableChartTint(fill, pigment, ink, .65f)
                assertContrast("chart ${style.name} dark=$dark pigment=$pigment", ink, chart)
            }
        }
    }

    @Test
    fun lightReportLegendKeepsDistinctShiftHuesAtThreeToOneContrast() {
        val coral = Color(0xFFEE9C9C)
        val mint = Color(0xFF83CDB7)
        for (style in PaletteStyle.values()) {
            val background = Color(turniPalette(false, style).secondaryContainer)
            val coralMarker = readablePigment(background, coral)
            val mintMarker = readablePigment(background, mint)
            assertContrast("coral legend in $style", coralMarker, background, 3.0)
            assertContrast("mint legend in $style", mintMarker, background, 3.0)
            val coralChannels = channels(coralMarker)
            val mintChannels = channels(mintMarker)
            assertTrue("coral remains red-toned", coralChannels[0] > coralChannels[1] + .15)
            assertTrue("mint remains green-toned", mintChannels[1] > mintChannels[0] + .15)
        }
    }

    @Test
    fun cobaltActionsStayConsistentAndStandaloneAccentsRemainReadable() {
        for ((style, dark) in variants) {
            val palette = turniPalette(dark, style)
            assertEquals("same accent for the dock and filled actions", palette.primary, palette.dockSelected)
            assertEquals("same label ink on filled actions", palette.onPrimary, palette.dockOnSelected)
            for (surface in listOf(palette.surface, palette.surfaceHigh, palette.surfaceHighest,
                palette.primaryContainer, palette.primarySubtle, palette.calendarToday, palette.calendarWeekend)) {
                assertContrast("$style/$dark standalone accent", Color(palette.primaryInk), Color(surface))
            }
            assertTrue("today needs a visibly blue tint distinct from the weekend in $style/$dark",
                distanceSquared(Color(palette.calendarToday), Color(palette.calendarWeekend)) > .02)
            val holidayEdge = readablePigment(Color(palette.primary), Color(palette.danger.ink))
            assertContrast("holiday outline on today badge", holidayEdge, Color(palette.primary), 3.0)
            assertTrue("holiday outline keeps its red hue", channels(holidayEdge)[0] > channels(holidayEdge)[1] + .1)
            val today = channels(Color(palette.calendarToday))
            assertTrue("today tint remains blue", today[2] - today[0] > .15)
        }
    }

    @Test
    fun smallTextRemainsReadableOnPanelsAndCalendarCells() {
        for ((style, dark) in variants) {
            val palette = turniPalette(dark, style)
            val surfaces = mapOf(
                "background" to palette.background,
                "surface" to palette.surface,
                "container" to palette.container,
                "raised" to palette.surfaceHigh,
                "highest" to palette.surfaceHighest,
                "calendar" to palette.calendar,
                "weekend" to palette.calendarWeekend,
                "today" to palette.calendarToday
            )
            for ((name, fill) in surfaces) {
                assertContrast("$style/$dark $name title", Color(palette.text), Color(fill))
                assertContrast("$style/$dark $name secondary", Color(palette.secondaryText), Color(fill))
                assertContrast("$style/$dark $name muted", Color(palette.mutedText), Color(fill))
            }
            assertContrast("$style/$dark primary action", Color(palette.onPrimary), Color(palette.primary))
            assertContrast("$style/$dark selected option", Color(palette.onPrimaryContainer), Color(palette.primaryContainer))
            assertContrast("$style/$dark destructive action", Color(palette.onDestructive), Color(palette.destructive))
            assertContrast("$style/$dark dock icon", Color(palette.dockInk), Color(palette.dock), 3.0)
            assertContrast("$style/$dark dock selected icon", Color(palette.dockOnSelected), Color(palette.dockSelected))
            assertContrast("$style/$dark dock selected pill", Color(palette.dockSelected), Color(palette.dock), 3.0)
        }
    }

    @Test
    fun statusLabelsAndSelectionCuesRemainDistinctInBothThemes() {
        for ((style, dark) in variants) {
            val palette = turniPalette(dark, style)
            val tones = mapOf(
                "on call" to palette.onCall,
                "overtime" to palette.overtime,
                "travel" to palette.travel,
                "warning" to palette.warning,
                "success" to palette.success,
                "danger" to palette.danger
            )
            for ((name, tone) in tones) {
                assertContrast("$style/$dark $name label", Color(tone.ink), Color(tone.fill))
            }
            for (cell in listOf(palette.calendar, palette.calendarWeekend, palette.calendarToday)) {
                assertContrast("$style/$dark calendar selection", Color(palette.primaryInk), Color(cell), 3.0)
            }
        }
    }

    @Test
    fun customShiftsKeepCodesAndTimesLegibleAcrossAllDayBackdrops() {
        for ((paletteStyle, dark) in variants) {
            val palette = turniPalette(dark, paletteStyle)
            val backdrops = listOf(palette.calendar, palette.calendarWeekend, palette.calendarToday)
            for (source in shiftSamples) for (base in backdrops) for (subdued in listOf(false, true)) {
                val style = shiftColorStyle(source, dark, Color(base), subdued, paletteStyle)
                val label = "$paletteStyle/$dark source=$source base=$base subdued=$subdued"
                // Adjacent-month and preview states must not re-fade otherwise readable ink.
                assertEquals("$label code alpha", 1f, style.codeInk.alpha, 0f)
                assertEquals("$label time alpha", 1f, style.timeInk.alpha, 0f)
                assertContrast("$label code", style.codeInk, style.codeFill)
                assertContrast("$label time", style.timeInk, style.timeFill)
            }
        }
    }

    @Test
    fun subduedShiftsQuietTheFillWithoutObscuringTheLabels() {
        for ((style, dark) in variants) {
            val backdrop = Color(turniPalette(dark, style).calendar)
            val source = Color(0xFFF2C46D)
            val active = shiftColorStyle(source, dark, backdrop, style = style)
            val subdued = shiftColorStyle(source, dark, backdrop, subdued = true, style = style)
            assertTrue(
                "Subdued code fill should recede toward the calendar in $style/$dark",
                distanceSquared(subdued.codeFill, backdrop) < distanceSquared(active.codeFill, backdrop)
            )
            assertContrast("$style/$dark subdued amber code", subdued.codeInk, subdued.codeFill)
            assertContrast("$style/$dark subdued amber time", subdued.timeInk, subdued.timeFill)
        }
    }

    @Test
    fun metricTilesKeepBothNumbersAndCaptionsLegibleForCustomColors() {
        for ((paletteStyle, dark) in variants) {
            for (source in shiftSamples) {
                val style = metricColorStyle(source, dark, style = paletteStyle)
                assertContrast("$paletteStyle/$dark metric number on $source", style.ink, style.fill)
                assertContrast("$paletteStyle/$dark metric caption on $source", style.secondaryInk, style.fill)
            }
            val onCall = Color(turniPalette(dark, paletteStyle).onCall.ink)
            val style = metricColorStyle(onCall, dark, onCall = true, style = paletteStyle)
            assertContrast("$paletteStyle/$dark on-call metric number", style.ink, style.fill)
            assertContrast("$paletteStyle/$dark on-call metric caption", style.secondaryInk, style.fill)
        }
    }

    @Test
    fun readableInkPreservesAUsefulHueAndRescuesMidToneCollisions() {
        val preferred = Color(0xFFB7FFE0)
        assertEquals(preferred, readableInk(Color(0xFF17171A), preferred))
        // Around mid-gray, a light or very dark theme-specific ink can both be too weak.
        for (background in listOf(Color(0xFF747474), Color(0xFF777777), Color(0xFF818181))) {
            assertContrast("fallback on $background", readableInk(background, background), background)
        }
    }

    private fun assertContrast(label: String, ink: Color, fill: Color, minimum: Double = 4.5) {
        assertEquals("$label resolved fill alpha", 1f, fill.alpha, 0f)
        val bg = channels(fill)
        val fg = channels(ink)
        val alpha = ink.alpha.toDouble()
        val renderedInk = DoubleArray(3) { index -> fg[index] * alpha + bg[index] * (1 - alpha) }
        val l1 = luminance(renderedInk)
        val l2 = luminance(bg)
        val ratio = (maxOf(l1, l2) + .05) / (minOf(l1, l2) + .05)
        assertTrue("$label: $ratio:1 for $ink on $fill, expected at least $minimum:1", ratio >= minimum)
    }

    private fun channels(color: Color): DoubleArray {
        val srgb = color.convert(ColorSpaces.Srgb)
        return doubleArrayOf(srgb.red.toDouble(), srgb.green.toDouble(), srgb.blue.toDouble())
    }

    private fun luminance(srgb: DoubleArray): Double {
        val linear = srgb.map { if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
        return .2126 * linear[0] + .7152 * linear[1] + .0722 * linear[2]
    }

    private fun distanceSquared(left: Color, right: Color): Double {
        val l = channels(left)
        val r = channels(right)
        return (0..2).sumOf { index -> (l[index] - r[index]).pow(2) }
    }

    private val variants = PaletteStyle.values().flatMap { style -> listOf(style to false, style to true) }

    private val shiftSamples = listOf(
        Color(0xFFF2C46D), Color(0xFFEE9C9C), Color(0xFFBAAEF6),
        Color(0xFF83CDB7), Color(0xFF94C4EF), Color(0xFFAAB9CE),
        Color.Black, Color.White, Color(0xFF777777), Color(0xFF0000FF),
        Color(0xFFFFFF00), Color(0xFF00FF70), Color(0xFFFF005C)
    )
}
