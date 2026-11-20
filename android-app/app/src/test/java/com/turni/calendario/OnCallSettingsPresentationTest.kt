package com.turni.calendario

import com.turni.calendario.model.ReperibilitaConfig
import com.turni.calendario.model.ReperibilitaMode
import com.turni.calendario.ui.screens.annualDayInMonth
import com.turni.calendario.ui.screens.manualOnCallDates
import com.turni.calendario.ui.screens.onCallStatusSummary
import com.turni.calendario.ui.screens.visibleOnCallMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class OnCallSettingsPresentationTest {
    @Test fun annualMonthChangeKeepsDatesValidWithoutLosingLeapDay() {
        assertEquals(30, annualDayInMonth(11, 31))
        assertEquals(29, annualDayInMonth(2, 31))
        assertEquals(29, annualDayInMonth(2, 29))
        assertEquals(15, annualDayInMonth(4, 15))
        assertEquals(1, annualDayInMonth(1, 0))
    }

    @Test fun manualSummaryIncludesNewOverridesAndHonorsExplicitRemoval() {
        val day = LocalDate.of(2026, 11, 8)
        val removed = day.plusDays(1)
        val added = day.plusDays(2)
        val unchanged = day.plusDays(3)
        assertEquals(setOf(day, added, unchanged), manualOnCallDates(
            setOf(day, removed, unchanged),
            mapOf(day to true, removed to false, added to true, unchanged to null)
        ))
    }

    @Test fun visibleModeMapsLegacyManualToShiftSpecificAndKeepsAutomaticModes() {
        assertEquals(ReperibilitaMode.TURNO_SPECIFICO, visibleOnCallMode(ReperibilitaMode.MANUALE))
        assertEquals(ReperibilitaMode.TURNO_SPECIFICO, visibleOnCallMode(ReperibilitaMode.TURNO_SPECIFICO))
        assertEquals(ReperibilitaMode.ALTERNATA_STAGIONALE, visibleOnCallMode(ReperibilitaMode.ALTERNATA_STAGIONALE))
    }

    @Test fun statusSummaryReflectsDisabledAndActiveModes() {
        assertEquals(
            "Nessuna regola automatica",
            onCallStatusSummary(ReperibilitaConfig(mode = ReperibilitaMode.MANUALE, shiftSpecificIds = listOf("pomeriggio"))),
        )
        assertEquals(
            "Attiva · 0 turni",
            onCallStatusSummary(ReperibilitaConfig(mode = ReperibilitaMode.TURNO_SPECIFICO, shiftSpecificIds = emptyList())),
        )
        assertEquals(
            "Attiva · 1 turno",
            onCallStatusSummary(ReperibilitaConfig(mode = ReperibilitaMode.TURNO_SPECIFICO, shiftSpecificIds = listOf("pomeriggio"))),
        )
        assertEquals(
            "Attiva · 2 turni",
            onCallStatusSummary(ReperibilitaConfig(mode = ReperibilitaMode.TURNO_SPECIFICO, shiftSpecificIds = listOf("mattina", "pomeriggio"))),
        )
        assertEquals(
            "Attiva · Stagionale",
            onCallStatusSummary(ReperibilitaConfig(mode = ReperibilitaMode.ALTERNATA_STAGIONALE)),
        )
    }
}
