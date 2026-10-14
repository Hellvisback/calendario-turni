package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ShiftRepositoryTest {

    @Before
    fun setup() {
        ShiftRepository.dayExtraInfo.clear()
        ShiftRepository.calendarShifts.clear()
        ShiftRepository.reperibilitaConfig = ReperibilitaConfig()
    }

    @Test
    fun testReperibilitaManualMode() {
        ShiftRepository.reperibilitaConfig = ReperibilitaConfig(
            mode = ReperibilitaMode.MANUALE,
            manualDates = setOf(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 20))
        )

        assertTrue(ShiftRepository.isOnCall(LocalDate.of(2026, 10, 15)))
        assertTrue(ShiftRepository.isOnCall(LocalDate.of(2026, 10, 20)))
        assertFalse(ShiftRepository.isOnCall(LocalDate.of(2026, 10, 16)))

        // Toggle date
        ShiftRepository.toggleManualDateOnCall(LocalDate.of(2026, 10, 16))
        assertTrue(ShiftRepository.isOnCall(LocalDate.of(2026, 10, 16)))

        ShiftRepository.toggleManualDateOnCall(LocalDate.of(2026, 10, 15))
        assertFalse(ShiftRepository.isOnCall(LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun testReperibilitaShiftSpecificMode() {
        ShiftRepository.reperibilitaConfig = ReperibilitaConfig(
            mode = ReperibilitaMode.TURNO_SPECIFICO,
            shiftSpecificIds = listOf("pomeriggio")
        )

        val date1 = LocalDate.of(2026, 10, 10)
        val date2 = LocalDate.of(2026, 10, 11)

        ShiftRepository.assignShift(date1, "pomeriggio")
        ShiftRepository.assignShift(date2, "mattina")

        assertTrue("Date with pomeriggio should be on-call", ShiftRepository.isOnCall(date1))
        assertFalse("Date with mattina should NOT be on-call", ShiftRepository.isOnCall(date2))
    }

    @Test
    fun testReperibilitaSeasonalWinterAndSummer() {
        val seasonal = SeasonalReperibilitaConfig(
            winterStartMonth = 10, winterStartDay = 1,
            winterEndMonth = 5, winterEndDay = 31,
            winterShiftIds = listOf("pomeriggio"),
            summerCycleReferenceDate = LocalDate.of(2026, 6, 1), // Lunedì
            summerCycleWorkWeeks = 2, // 2 settimane lavoro
            summerCycleOnCallWeeks = 1 // 1 settimana reperibilità (ciclo 3 settimane)
        )
        ShiftRepository.reperibilitaConfig = ReperibilitaConfig(
            mode = ReperibilitaMode.ALTERNATA_STAGIONALE,
            seasonal = seasonal
        )

        // Test Inverno (15 Ottobre 2026): attivo solo su turni winterShiftIds ("pomeriggio")
        val winterDate = LocalDate.of(2026, 10, 15)
        ShiftRepository.assignShift(winterDate, "mattina")
        assertFalse(ShiftRepository.isOnCall(winterDate))

        ShiftRepository.assignShift(winterDate, "pomeriggio")
        assertTrue(ShiftRepository.isOnCall(winterDate))

        // Test Estate (Giugno 2026):
        // Settimana 0 (1-7 Giugno): Reperibilità (posInCycle 0 < onCallWeeks 1) -> TRUE
        val summerWeek0Date = LocalDate.of(2026, 6, 3)
        assertTrue("Week 0 of summer rotation should be on-call", ShiftRepository.isOnCall(summerWeek0Date))

        // Settimana 1 (8-14 Giugno): Lavoro (posInCycle 1 >= onCallWeeks 1) -> FALSE
        val summerWeek1Date = LocalDate.of(2026, 6, 10)
        assertFalse("Week 1 of summer rotation should NOT be on-call", ShiftRepository.isOnCall(summerWeek1Date))

        // Settimana 2 (15-21 Giugno): Lavoro (posInCycle 2 >= onCallWeeks 1) -> FALSE
        val summerWeek2Date = LocalDate.of(2026, 6, 17)
        assertFalse("Week 2 of summer rotation should NOT be on-call", ShiftRepository.isOnCall(summerWeek2Date))

        // Settimana 3 (22-28 Giugno): Nuovo ciclo, Settimana 0 -> Reperibilità -> TRUE
        val summerWeek3Date = LocalDate.of(2026, 6, 24)
        assertTrue("Week 3 of summer rotation starts new cycle (on-call)", ShiftRepository.isOnCall(summerWeek3Date))
    }

    @Test
    fun testReperibilitaDayOverridePrecedence() {
        ShiftRepository.reperibilitaConfig = ReperibilitaConfig(
            mode = ReperibilitaMode.MANUALE,
            manualDates = emptySet()
        )
        val testDate = LocalDate.of(2026, 10, 25)
        assertFalse(ShiftRepository.isOnCall(testDate))

        // Override manuale positivo per il giorno
        ShiftRepository.setDayOnCallManual(testDate, true)
        assertTrue(ShiftRepository.isOnCall(testDate))

        // Override manuale negativo per il giorno
        ShiftRepository.setDayOnCallManual(testDate, false)
        assertFalse(ShiftRepository.isOnCall(testDate))
    }

    @Test
    fun testCcnlFerieOverSmontoAndRiposoRule() {
        // Regola fondamentale CCNL:
        // Se si inseriscono Ferie sopra uno Smonto Notte o Riposo,
        // nel report NON devono scalare le ferie usate, ma contare come Smonto / Riposo!
        val d1 = LocalDate.of(2026, 10, 1) // Ferie su Smonto Notte originario
        val d2 = LocalDate.of(2026, 10, 2) // Ferie su Riposo originario
        val d3 = LocalDate.of(2026, 10, 3) // Ferie su Turno Lavorativo (Mattina originario)

        // Assegniamo d1 come ferie con originalShiftDefId = "smonto_notte"
        ShiftRepository.assignShift(d1, "ferie", isModified = true)
        ShiftRepository.calendarShifts[d1]?.firstOrNull()?.let {
            val idx = ShiftRepository.calendarShifts[d1]!!.indexOf(it)
            ShiftRepository.calendarShifts[d1]!![idx] = it.copy(originalShiftDefId = "smonto_notte")
        }

        // Assegniamo d2 come ferie con originalShiftDefId = "riposo"
        ShiftRepository.assignShift(d2, "ferie", isModified = true)
        ShiftRepository.calendarShifts[d2]?.firstOrNull()?.let {
            val idx = ShiftRepository.calendarShifts[d2]!!.indexOf(it)
            ShiftRepository.calendarShifts[d2]!![idx] = it.copy(originalShiftDefId = "riposo")
        }

        // Assegniamo d3 come ferie con originalShiftDefId = "mattina" (lavoro effettivo sostituito da ferie)
        ShiftRepository.assignShift(d3, "ferie", isModified = true)
        ShiftRepository.calendarShifts[d3]?.firstOrNull()?.let {
            val idx = ShiftRepository.calendarShifts[d3]!!.indexOf(it)
            ShiftRepository.calendarShifts[d3]!![idx] = it.copy(originalShiftDefId = "mattina")
        }

        val stats = ShiftRepository.getComprehensiveReportStats(d1, d3)

        assertEquals("Solo 1 giorno effettivo di ferie deve essere scalato", 1, stats.countFerie)
        assertEquals("Smonto notte deve essere conteggiato come smonto", 1, stats.countSmonto)
        assertEquals("Riposo deve essere conteggiato come riposo", 1, stats.countRiposo)
    }

    @Test
    fun testOvertimeAndAllowancesAccumulation() {
        val testDate = LocalDate.of(2026, 10, 5)
        ShiftRepository.assignShift(testDate, "mattina") // Mattina: 06:00 - 13:36 = 7.6 ore

        ShiftRepository.setDayOvertime(testDate, 2.5f)
        ShiftRepository.setDayTrasferta(testDate, true)
        ShiftRepository.setDayOnCallManual(testDate, true)

        val stats = ShiftRepository.getComprehensiveReportStats(testDate, testDate)

        assertEquals(1, stats.workedDays)
        assertEquals(2.5f, stats.totalOvertimeHours, 0.01f)
        assertEquals(7.6f + 2.5f, stats.totalHours, 0.01f)
        assertEquals(1, stats.countTrasferte)
        assertEquals(1, stats.countReperibilita)
    }

    @Test
    fun testOvertimeOnRestDayCountedInTotalHours() {
        val testDate = LocalDate.of(2026, 10, 6)
        ShiftRepository.assignShift(testDate, "riposo") // Giorno di riposo
        ShiftRepository.setDayOvertime(testDate, 3.0f)   // Straordinario / chiamata in servizio

        val stats = ShiftRepository.getComprehensiveReportStats(testDate, testDate)

        assertEquals("Giorno con straordinario deve contare come giorno lavorato", 1, stats.workedDays)
        assertEquals("3 ore di straordinario", 3.0f, stats.totalOvertimeHours, 0.01f)
        assertEquals("Totale ore deve includere lo straordinario anche se era riposo", 3.0f, stats.totalHours, 0.01f)
    }

    @Test
    fun testCcnlCatalogPresetsAndLookup() {
        assertTrue("CCNL catalog must have at least 7 presets", CcnlCatalog.presets.size >= 7)

        val rfi = CcnlCatalog.findByCode("CCNL_MOBILITA_AF")
        assertEquals(38, rfi.standardWeeklyHours)
        assertEquals(14, rfi.minRestHoursBetweenShifts)
        assertEquals(35, rfi.nightSurchargePercent)
        assertEquals(50, rfi.holidaySurchargePercent)
        assertEquals(65, rfi.nightHolidaySurchargePercent)

        val sanita = CcnlCatalog.findByCode("CCNL_SANITA")
        assertEquals(36, sanita.standardWeeklyHours)
        assertEquals(11, sanita.minRestHoursBetweenShifts)
        assertEquals(25, sanita.nightSurchargePercent)

        val metal = CcnlCatalog.findByCode("CCNL_METALMECCANICI")
        assertEquals(40, metal.standardWeeklyHours)
        assertEquals(11, metal.minRestHoursBetweenShifts)
    }

    @Test
    fun testWorkProfileCrudAndActiveSwitch() {
        // Profilo iniziale predefinito
        val initialActive = ShiftRepository.activeWorkProfile
        assertEquals("principale", initialActive.id)
        assertEquals(11, initialActive.minRestHoursBetweenShifts)
        assertEquals(38, initialActive.weeklyHours)

        // Aggiunta secondo lavoro (es. Sanità Ospedale)
        val sanitaProfile = WorkProfile(
            id = "sanita_job",
            name = "Ospedale Riuniti",
            companyOrDepartment = "Pronto Soccorso",
            ccnlCode = "CCNL_SANITA",
            ccnlName = "CCNL Sanità Pubblica & Privata",
            weeklyHours = 36,
            minRestHoursBetweenShifts = 11,
            nightSurchargePercent = 25,
            holidaySurchargePercent = 30,
            nightHolidaySurchargePercent = 50,
            isDefault = false
        )
        ShiftRepository.addWorkProfile(sanitaProfile)
        assertEquals(2, ShiftRepository.workProfiles.size)

        // Switch lavoro attivo
        ShiftRepository.setActiveWorkProfile("sanita_job")
        assertEquals("sanita_job", ShiftRepository.activeWorkProfile.id)
        assertEquals(11, ShiftRepository.activeWorkProfile.minRestHoursBetweenShifts)
        assertEquals(36, ShiftRepository.activeWorkProfile.weeklyHours)

        // Aggiornamento profilo
        val updatedSanita = ShiftRepository.activeWorkProfile.copy(weeklyHours = 35)
        ShiftRepository.updateWorkProfile(updatedSanita)
        assertEquals(35, ShiftRepository.activeWorkProfile.weeklyHours)

        // Ripristino profilo principale come attivo
        ShiftRepository.setActiveWorkProfile("principale")
        assertEquals("principale", ShiftRepository.activeWorkProfile.id)
        assertEquals(11, ShiftRepository.activeWorkProfile.minRestHoursBetweenShifts)

        // Eliminazione secondo lavoro
        val deleted = ShiftRepository.deleteWorkProfile("sanita_job")
        assertTrue("Eliminazione secondo lavoro deve avere successo", deleted)
        assertEquals(1, ShiftRepository.workProfiles.size)
    }
}
