package com.turni.calendario

import com.turni.calendario.importer.NgeniaTurniParser
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.screens.RFI_EXAMPLE_TABELLONE
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class NgeniaTurniParserTest {

    @Before
    fun setUp() {
        // Reset stato repository
        ShiftRepository.defaultRotationId = "turno_a"
    }

    @Test
    fun testParseValidityDates() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)
        assertNotNull(result.validityStart)
        assertNotNull(result.validityEnd)
        assertEquals(LocalDate.of(2026, 9, 21), result.validityStart)
        assertEquals(LocalDate.of(2026, 10, 18), result.validityEnd)
    }

    @Test
    fun testParseHoursSchedule() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)
        assertTrue(result.detectedHours.containsKey("M"))
        assertTrue(result.detectedHours.containsKey("P"))
        assertTrue(result.detectedHours.containsKey("N"))
        assertTrue(result.detectedHours.containsKey("MD"))

        assertEquals(Pair("06:00", "13:36"), result.detectedHours["M"])
        assertEquals(Pair("14:00", "21:36"), result.detectedHours["P"])
        assertEquals(Pair("22:00", "05:36"), result.detectedHours["N"])
        assertEquals(Pair("08:36", "16:12"), result.detectedHours["MD"])
    }

    @Test
    fun testParseAllTeams() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)
        assertEquals(5, result.teams.size)

        val teamNames = result.teams.map { it.teamName }
        assertTrue(teamNames.contains("Squadra A"))
        assertTrue(teamNames.contains("Squadra B"))
        assertTrue(teamNames.contains("Squadra C"))
        assertTrue(teamNames.contains("Squadra D"))
        assertTrue(teamNames.contains("Squadra B 1"))

        for (team in result.teams) {
            assertEquals(28, team.totalDays)
            assertEquals(28, team.dayShiftDefIds.size)
        }
    }

    @Test
    fun testParseEmployees() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)
        assertTrue(result.employees.isNotEmpty())

        val marcozzi = result.employees.find { it.name.contains("Marcozzi", ignoreCase = true) }
        assertNotNull(marcozzi)
        assertEquals("Squadra B", marcozzi?.teamName)
        assertEquals("8024724", marcozzi?.phone)

        val raglione = result.employees.find { it.name.contains("Raglione", ignoreCase = true) }
        assertNotNull(raglione)
    }

    @Test
    fun testImportWithoutDuplicates() {
        val initialDefinitionsCount = ShiftRepository.shiftDefinitions.size
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)

        ShiftRepository.ensureShiftDefinitionsFromParsedHours(result.detectedHours)

        // I turni esistenti M, P, N, MD devono essere stati aggiornati, non duplicati
        val mattinaDefs = ShiftRepository.shiftDefinitions.filter { it.id == "mattina" }
        assertEquals(1, mattinaDefs.size)
        assertEquals("06:00", mattinaDefs.first().startTime)
        assertEquals("13:36", mattinaDefs.first().endTime)

        val pomeriggioDefs = ShiftRepository.shiftDefinitions.filter { it.id == "pomeriggio" }
        assertEquals(1, pomeriggioDefs.size)
        assertEquals("14:00", pomeriggioDefs.first().startTime)
        assertEquals("21:36", pomeriggioDefs.first().endTime)

        // Il conteggio complessivo non deve essere esploso con duplicati
        assertTrue(ShiftRepository.shiftDefinitions.size <= initialDefinitionsCount + 2)
    }

    @Test
    fun testDefaultRotationSortsFirst() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)

        // Importiamo e impostiamo Squadra C come predefinita
        ShiftRepository.importNgeniaTurniResult(
            result = result,
            selectedTeamName = "Squadra C",
            makeDefault = true,
            applyToCalendar = true
        )

        assertEquals("rotazione_squadra_c", ShiftRepository.defaultRotationId)

        val sorted = ShiftRepository.getRotationsSortedByDefault()
        assertFalse(sorted.isEmpty())
        assertEquals("rotazione_squadra_c", sorted.first().id)
        assertEquals("Squadra C", sorted.first().name)
    }
}
