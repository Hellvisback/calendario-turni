package com.turni.calendario.export

import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
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
}
