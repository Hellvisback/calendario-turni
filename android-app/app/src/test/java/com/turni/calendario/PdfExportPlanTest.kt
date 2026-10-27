package com.turni.calendario.export

import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

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
    @Test fun periodChoosesListForTwoWeeksIncludingBothEndpoints() {
        val options=PdfExportOptions(mode=PdfExportMode.PERSONALIZZATO,startDate=LocalDate.of(2026,12,25),endDate=LocalDate.of(2027,1,7))
        assertEquals(PdfPresentation.LIST,PdfExportPlan.presentation(options))
        assertEquals(PdfPresentation.MONTHS,PdfExportPlan.presentation(options.copy(endDate=LocalDate.of(2027,1,8))))
    }
    @Test fun longPeriodUsesOverviewAndExplicitModesKeepTheirLayout() {
        val options=PdfExportOptions(mode=PdfExportMode.PERSONALIZZATO,startDate=LocalDate.of(2026,1,1),endDate=LocalDate.of(2026,12,31))
        assertEquals(PdfPresentation.OVERVIEW,PdfExportPlan.presentation(options))
        assertEquals(PdfPresentation.LIST,PdfExportPlan.presentation(options.copy(mode=PdfExportMode.ELENCO)))
        assertEquals(PdfPresentation.MONTHS,PdfExportPlan.presentation(options.copy(mode=PdfExportMode.MESE)))
    }
}
