package com.turni.calendario.export

import androidx.compose.runtime.mutableStateListOf
import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DatabaseRestore
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PdfExportPlanTest {
    @Test fun monthUsesEntireMonthEvenWithStaleEndDate() {
        val options = PdfExportOptions(mode = PdfExportMode.MESE, startDate = LocalDate.of(2028, 2, 18), endDate = LocalDate.of(2026, 1, 1))
        val dates = PdfExportPlan.dates(options)
        assertEquals(29, dates.size)
        assertEquals(LocalDate.of(2028, 2, 1), dates.first())
        assertEquals(LocalDate.of(2028, 2, 29), dates.last())
    }

    @Test fun yearUsesSelectedYearAndIncludesLeapDay() {
        val dates = PdfExportPlan.dates(PdfExportOptions(mode = PdfExportMode.ANNO, selectedYear = 2028))
        assertEquals(366, dates.size)
        assertEquals(LocalDate.of(2028, 12, 31), dates.last())
    }

    @Test fun customRangeAcrossYearKeepsBothEndpoints() {
        val options = PdfExportOptions(mode = PdfExportMode.PERSONALIZZATO, startDate = LocalDate.of(2026, 12, 30), endDate = LocalDate.of(2027, 1, 2))
        assertEquals(4, PdfExportPlan.dates(options).size)
        assertEquals(2, PdfExportPlan.months(options).size)
    }

    @Test fun singleDayIsNotEmpty() {
        val day = LocalDate.of(2026, 10, 25)
        assertEquals(listOf(day), PdfExportPlan.dates(PdfExportOptions(startDate = day, endDate = day)))
    }

    @Test(expected = IllegalArgumentException::class) fun reversedRangeIsRejected() {
        PdfExportPlan.dates(PdfExportOptions(startDate = LocalDate.of(2026, 11, 2), endDate = LocalDate.of(2026, 11, 1)))
    }

    @Test(expected = IllegalArgumentException::class) fun unboundedExportIsRejectedBeforeAllocation() {
        PdfExportPlan.dates(PdfExportOptions(startDate = LocalDate.of(2000, 1, 1), endDate = LocalDate.of(2100, 1, 1)))
    }

    @Test fun customPeriodAlwaysUsesOverviewAcrossAllSpans() {
        val oneDay = PdfExportOptions(
            mode = PdfExportMode.PERSONALIZZATO,
            startDate = LocalDate.of(2026, 10, 15),
            endDate = LocalDate.of(2026, 10, 15),
        )
        val fourteenDaysCrossMonth = oneDay.copy(
            startDate = LocalDate.of(2026, 12, 25),
            endDate = LocalDate.of(2027, 1, 7),
        )
        val twoMonths = oneDay.copy(
            startDate = LocalDate.of(2026, 11, 1),
            endDate = LocalDate.of(2026, 12, 31),
        )
        val twelveMonths = oneDay.copy(
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 12, 31),
        )
        assertEquals(PdfPresentation.OVERVIEW, PdfExportPlan.presentation(oneDay))
        assertEquals(PdfPresentation.OVERVIEW, PdfExportPlan.presentation(fourteenDaysCrossMonth))
        assertEquals(PdfPresentation.OVERVIEW, PdfExportPlan.presentation(twoMonths))
        assertEquals(PdfPresentation.OVERVIEW, PdfExportPlan.presentation(twelveMonths))
    }

    @Test fun explicitModesRetainTheirLayouts() {
        val base = PdfExportOptions(startDate = LocalDate.of(2026, 10, 1), endDate = LocalDate.of(2026, 10, 14))
        assertEquals(PdfPresentation.LIST, PdfExportPlan.presentation(base.copy(mode = PdfExportMode.ELENCO)))
        assertEquals(PdfPresentation.MONTHS, PdfExportPlan.presentation(base.copy(mode = PdfExportMode.MESE)))
        assertEquals(PdfPresentation.OVERVIEW, PdfExportPlan.presentation(base.copy(mode = PdfExportMode.ANNO)))
    }

    @Test fun validateForExportEnforcesTwelveMonthLimitOnlyForOverview() {
        val twelveMonths = PdfExportOptions(
            mode = PdfExportMode.PERSONALIZZATO,
            startDate = LocalDate.of(2026, 1, 15),
            endDate = LocalDate.of(2026, 12, 10),
        )
        assertEquals(12, PdfExportPlan.months(twelveMonths).size)
        assertTrue(PdfExportPlan.validateForExport(twelveMonths).isNotEmpty())

        val thirteenMonths = twelveMonths.copy(endDate = LocalDate.of(2027, 1, 10))
        assertEquals(13, PdfExportPlan.months(thirteenMonths).size)
        assertTrue(PdfExportPlan.validateForExport(thirteenMonths.copy(mode = PdfExportMode.ELENCO)).isNotEmpty())
        val error = assertThrows(IllegalArgumentException::class.java) {
            PdfExportPlan.validateForExport(thirteenMonths)
        }
        assertEquals(PdfExportPlan.OVERVIEW_MAX_MONTHS_ERROR, error.message)
    }

    @Test fun overviewPaperSizesAdaptToMonthCount() {
        assertEquals(listOf(595 to 842, 842 to 1191), overviewPaperSizes(1))
        assertEquals(listOf(595 to 842, 842 to 595, 1191 to 842), overviewPaperSizes(2))
        assertEquals(listOf(595 to 842, 842 to 595, 1191 to 842), overviewPaperSizes(3))
        assertEquals(listOf(842 to 595, 1191 to 842, 1684 to 1191), overviewPaperSizes(4))
        assertEquals(listOf(842 to 595, 1191 to 842, 1684 to 1191), overviewPaperSizes(6))
        assertEquals(listOf(1191 to 842, 1684 to 1191, 2384 to 1684), overviewPaperSizes(7))
        assertEquals(listOf(1191 to 842, 1684 to 1191, 2384 to 1684), overviewPaperSizes(12))
    }

    @Test fun overviewFontCandidatesAndHeadersAdaptToMonthCount() {
        assertEquals(listOf(10f, 9.5f, 9f, 8.5f, 8f, 7.5f, 7f, 6.5f), overviewFontCandidates(1))
        assertEquals(listOf(10f, 9.5f, 9f, 8.5f, 8f, 7.5f, 7f, 6.5f), overviewFontCandidates(2))
        assertEquals(listOf(9f, 8.5f, 8f, 7.5f, 7f, 6.5f), overviewFontCandidates(3))
        assertEquals(listOf(9f, 8.5f, 8f, 7.5f, 7f, 6.5f), overviewFontCandidates(5))
        assertEquals(listOf(8f, 7.5f, 7f, 6.5f), overviewFontCandidates(6))
        assertEquals(listOf(8f, 7.5f, 7f, 6.5f), overviewFontCandidates(12))

        val nov = YearMonth.of(2026, 11)
        assertEquals("Novembre 2026", overviewColumnHeaderText(nov, PdfExportMode.PERSONALIZZATO, 2, fitsFullMonthYear = true))
        assertEquals("Nov 2026", overviewColumnHeaderText(nov, PdfExportMode.PERSONALIZZATO, 2, fitsFullMonthYear = false))
        assertEquals("Nov 2026", overviewColumnHeaderText(nov, PdfExportMode.PERSONALIZZATO, 3, fitsFullMonthYear = true))
        assertEquals("Nov", overviewColumnHeaderText(nov, PdfExportMode.ANNO, 12, fitsFullMonthYear = true))
    }

    @Test fun overviewCaseASingleMonthRendersOnlySelectedDatesWithCappedRowHeights() {
        val options = PdfExportOptions(
            mode = PdfExportMode.PERSONALIZZATO,
            startDate = LocalDate.of(2026, 10, 12),
            endDate = LocalDate.of(2026, 10, 18),
        )
        val dates = PdfExportPlan.dates(options)
        val months = PdfExportPlan.months(options)
        val plans = planOverviewColumns(
            months = months,
            dates = dates,
            mode = options.mode,
            gridTop = 120f,
            availableHeight = 680f,
            baseRowHeight = 18.5f,
            leading = 12.2f,
            lineCounts = dates.associateWith { 1 },
        )!!
        val col = plans.single()
        assertEquals(7, col.slots.size)
        assertEquals((0..6).toList(), col.slots.map { it.rowIndex })
        assertEquals(dates, col.slots.map { it.date })
        assertTrue(col.rowHeights.all { it in 18.5f..38f })
    }

    @Test fun overviewCaseBCrossMonthShortRangeStacksCompactlyFromTop() {
        val options = PdfExportOptions(
            mode = PdfExportMode.PERSONALIZZATO,
            startDate = LocalDate.of(2026, 11, 25),
            endDate = LocalDate.of(2026, 12, 8),
        )
        val dates = PdfExportPlan.dates(options)
        val months = PdfExportPlan.months(options)
        val plans = planOverviewColumns(
            months = months,
            dates = dates,
            mode = options.mode,
            gridTop = 120f,
            availableHeight = 680f,
            baseRowHeight = 18.5f,
            leading = 12.2f,
            lineCounts = dates.associateWith { 1 },
        )!!
        assertEquals(2, plans.size)
        val nov = plans[0]
        val dec = plans[1]
        assertEquals((0..5).toList(), nov.slots.map { it.rowIndex })
        assertEquals(LocalDate.of(2026, 11, 25), nov.slots.first().date)
        assertEquals(LocalDate.of(2026, 11, 30), nov.slots.last().date)
        assertEquals(120f, nov.rowTops.first(), 0.001f)

        assertEquals((0..7).toList(), dec.slots.map { it.rowIndex })
        assertEquals(LocalDate.of(2026, 12, 1), dec.slots.first().date)
        assertEquals(LocalDate.of(2026, 12, 8), dec.slots.last().date)
        assertEquals(120f, dec.rowTops.first(), 0.001f)
    }

    @Test fun overviewCaseCAlignedMatrixLeavesOutOfRangeCellsBlank() {
        val options = PdfExportOptions(
            mode = PdfExportMode.PERSONALIZZATO,
            startDate = LocalDate.of(2026, 5, 15),
            endDate = LocalDate.of(2026, 7, 15),
        )
        val dates = PdfExportPlan.dates(options)
        val months = PdfExportPlan.months(options)
        val plans = planOverviewColumns(
            months = months,
            dates = dates,
            mode = options.mode,
            gridTop = 120f,
            availableHeight = 680f,
            baseRowHeight = 18.5f,
            leading = 11.2f,
            lineCounts = dates.associateWith { 1 },
        )!!
        assertEquals(3, plans.size)
        assertEquals(14, plans[0].slots.first().rowIndex)
        assertEquals(LocalDate.of(2026, 5, 15), plans[0].slots.first().date)
        assertEquals(30, plans[0].slots.last().rowIndex)

        assertEquals(0, plans[1].slots.first().rowIndex)
        assertEquals(29, plans[1].slots.last().rowIndex)

        assertEquals(0, plans[2].slots.first().rowIndex)
        assertEquals(14, plans[2].slots.last().rowIndex)
        assertEquals(LocalDate.of(2026, 7, 15), plans[2].slots.last().date)

        assertEquals(plans[0].rowTops[14], plans[1].rowTops[14], 0.001f)
        assertEquals(plans[1].rowTops[14], plans[2].rowTops[14], 0.001f)
    }

    @Test fun dynamicCustomPeriodLayoutAdaptsByDayAndMonthSpan() {
        assertEquals(CustomPeriodLayout.SHORT_DETAILED, resolveCustomPeriodLayout(dayCount = 1, monthCount = 1))
        assertEquals(CustomPeriodLayout.SHORT_DETAILED, resolveCustomPeriodLayout(dayCount = 14, monthCount = 2))
        assertEquals(CustomPeriodLayout.MEDIUM_TWO_COLUMN, resolveCustomPeriodLayout(dayCount = 15, monthCount = 1))
        assertEquals(CustomPeriodLayout.MEDIUM_TWO_COLUMN, resolveCustomPeriodLayout(dayCount = 31, monthCount = 1))
        assertEquals(CustomPeriodLayout.MEDIUM_TWO_COLUMN, resolveCustomPeriodLayout(dayCount = 61, monthCount = 2))
        assertEquals(CustomPeriodLayout.EXTENDED_OVERVIEW, resolveCustomPeriodLayout(dayCount = 90, monthCount = 3))
        assertEquals(CustomPeriodLayout.EXTENDED_OVERVIEW, resolveCustomPeriodLayout(dayCount = 365, monthCount = 12))
    }

    @Test fun listLayoutPrefersTwoColumnsForSixteenOrMoreDaysOrTwoMonths() {
        assertEquals(listOf(1, 2, 3), preferredListColumns(dayCount = 15, monthCount = 1))
        assertEquals(listOf(2, 3, 1), preferredListColumns(dayCount = 16, monthCount = 1))
        assertEquals(listOf(2, 3, 1), preferredListColumns(dayCount = 10, monthCount = 2))

        val nov = (1..15).map { LocalDate.of(2026, 11, it) }
        val dec = (1..15).map { LocalDate.of(2026, 12, it) }
        val dates = nov + dec
        val heights = List(dates.size) { 20f }
        val cols = planListColumns(dates, heights, columns = 2, available = 400f)!!
        assertEquals(2, cols.size)
        assertEquals((0..14).toList(), cols[0])
        assertEquals((15..29).toList(), cols[1])
    }

    @Test fun smontoNotteProducesSingleUnifiedCalendarBadge() {
        val smonto = PdfShiftRow(
            code = "SN",
            name = "Smonto Notte",
            color = 0xFF5B8DEF.toInt(),
            time = "22:00",
            details = listOf("22:00 - 06:00", "Durata prevista: 8 h 0 min"),
        )
        val day = PdfDayRow(
            date = LocalDate.of(2026, 11, 10),
            shifts = listOf(smonto),
            details = emptyList(),
            onCall = true,
        )

        val fullElements = buildCalendarCellElements(
            row = day,
            compact = false,
            includeHeaderOnCall = true,
            wrapBadgeTitle = { title -> title.split(' ') },
            wrapDetail = { listOf(it) },
        )
        val badges = fullElements.filterIsInstance<CalendarCellElement.ShiftBadge>()
        assertEquals(1, badges.size)
        assertEquals(listOf("Smonto", "Notte"), badges.single().lines)
        assertTrue(fullElements.none { it is CalendarCellElement.OnCallPill })
        assertTrue(fullElements.filterIsInstance<CalendarCellElement.DetailLine>().any { it.text == "22:00–06:00" })
        assertTrue(fullElements.filterIsInstance<CalendarCellElement.DetailLine>().any { it.text == "Dur. 8h" })

        val compactElements = buildCalendarCellElements(
            row = day,
            compact = true,
            includeHeaderOnCall = false,
            wrapBadgeTitle = { listOf(it) },
            wrapDetail = { listOf(it) },
        )
        val compactBadges = compactElements.filterIsInstance<CalendarCellElement.ShiftBadge>()
        assertEquals(1, compactBadges.size)
        assertEquals(listOf("SN"), compactBadges.single().lines)
        assertEquals(1, compactElements.filterIsInstance<CalendarCellElement.OnCallPill>().size)
    }

    @Test fun conciseDetailFormattingCompactsAllOptionsCleanly() {
        assertEquals("06:00–14:00", formatPdfDetail("06:00 - 14:00", compact = false))
        assertEquals("06–14", formatPdfDetail("06:00 - 14:00", compact = true))
        assertEquals("Tutto il giorno", formatPdfDetail("Tutto il giorno", compact = false))
        assertEquals("TG", formatPdfDetail("Tutto il giorno", compact = true))
        assertEquals("Attacco 05:30", formatPdfDetail("Ingresso effettivo: 05:30", compact = false))
        assertEquals("Stacco 14:30", formatPdfDetail("Stacco effettivo: 14:30", compact = false))
        assertEquals("Dur. 8h", formatPdfDetail("Durata prevista: 8 h 0 min", compact = false))
        assertEquals("Dur. 7h30", formatPdfDetail("Durata prevista: 7 h 30 min", compact = false))
        assertEquals("Pausa 20m (02:00)", formatPdfDetail("Pausa: 20 min dalle 02:00", compact = false))
        assertEquals("P. 30m", formatPdfDetail("Pausa: 30 min", compact = true))
        assertEquals("+1h30", formatPdfDetail("Straordinario: 1 h 30 min", compact = false))
        assertEquals("−1h", formatPdfDetail("Permesso: −1 h 0 min", compact = false))
        assertEquals("Trasferta", formatPdfDetail("Trasferta", compact = false))
        assertEquals("TR", formatPdfDetail("Trasferta", compact = true))
    }

    @Test fun captureAndLegendSortShiftsChronologicallyWithAllDayAlwaysLast() {
        val before = DatabaseSnapshot.capture()
        try {
            val profileId = ShiftRepository.activeWorkProfileId
            val day = LocalDate.of(2026, 11, 12)
            val ferie = ShiftDefinition(
                id = "pdf-sort-ferie",
                name = "Ferie",
                shortCode = "FE",
                startTime = "00:00",
                endTime = "23:59",
                isAllDay = true,
                colorHex = 0xFFAAAAAA,
                workProfileId = profileId,
            )
            val notte = ShiftDefinition(
                id = "pdf-sort-notte",
                name = "Notte",
                shortCode = "N",
                startTime = "22:00",
                endTime = "06:00",
                colorHex = 0xFF334455,
                workProfileId = profileId,
            )
            val mattina = ShiftDefinition(
                id = "pdf-sort-mattina",
                name = "Mattina",
                shortCode = "M",
                startTime = "06:00",
                endTime = "14:00",
                colorHex = 0xFF55AA77,
                workProfileId = profileId,
            )
            val mattinaLunga = ShiftDefinition(
                id = "pdf-sort-mattina-l",
                name = "Mattina Lunga",
                shortCode = "ML",
                startTime = "06:00",
                endTime = "16:00",
                colorHex = 0xFF66BB88,
                workProfileId = profileId,
            )
            ShiftRepository.shiftDefinitions.addAll(listOf(ferie, notte, mattinaLunga, mattina))
            ShiftRepository.calendarShifts[day] = mutableStateListOf(
                AssignedShift("a-ferie", day, ferie.id),
                AssignedShift("a-notte", day, notte.id),
                AssignedShift("a-ml", day, mattinaLunga.id),
                AssignedShift("a-m", day, mattina.id),
            )
            ShiftRepository.setDayOnCallManual(day, true)

            listOf(
                PdfExportMode.ELENCO,
                PdfExportMode.MESE,
                PdfExportMode.ANNO,
                PdfExportMode.PERSONALIZZATO,
            ).forEach { mode ->
                val snapshot = PdfGenerator.capture(
                    PdfExportOptions(
                        mode = mode,
                        startDate = day,
                        endDate = day,
                        selectedYear = 2026,
                    ),
                )
                val dayRow = snapshot.days.first { it.date == day }
                assertEquals(listOf("M", "ML", "N", "FE"), dayRow.shifts.map { it.code })
                assertTrue(dayRow.onCall)
                assertFalse(dayRow.details.contains("Reperibilità"))

                val legendSorted = sortShiftRowsChronologically(dayRow.shifts.reversed())
                assertEquals(listOf("M", "ML", "N", "FE"), legendSorted.map { it.code })
            }
        } finally {
            DatabaseRestore.apply(before)
        }
    }
}
