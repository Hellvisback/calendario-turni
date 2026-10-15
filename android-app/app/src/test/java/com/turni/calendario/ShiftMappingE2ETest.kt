package com.turni.calendario

import com.turni.calendario.data.GeminiEmployeeAssignment
import com.turni.calendario.data.GeminiShiftData
import com.turni.calendario.data.GeminiShiftDefinition
import com.turni.calendario.data.GeminiSquadRotation
import com.turni.calendario.data.SecurePreferencesManager
import com.turni.calendario.importer.NgeniaTurniParser
import com.turni.calendario.importer.ParsedEmployee
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.repository.ShiftRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * End-to-End integration test suite for Shift Mapping, Catalog Registration,
 * User Squad Auto-Matching, and One-Click Calendar Setup (F6, F7, F8, F9).
 */
class ShiftMappingE2ETest {

    @Before
    fun setUp() {
        ShiftRepository.calendarShifts.clear()
        ShiftRepository.dayExtraInfo.clear()
        SecurePreferencesManager.clear()
    }

    @After
    fun tearDown() {
        ShiftRepository.calendarShifts.clear()
        ShiftRepository.dayExtraInfo.clear()
        SecurePreferencesManager.clear()
    }

    // ==========================================
    // Tier 1: Shift Catalog Registration (F7)
    // ==========================================

    @Test
    fun testEnsureShiftDefinitionsFromParsedHours_UpdatesExistingWithoutDuplicates() {
        val initialCount = ShiftRepository.shiftDefinitions.size

        val hoursMap = mapOf(
            "M" to Pair("06:30", "14:06"),
            "P" to Pair("14:30", "22:06"),
            "N" to Pair("22:30", "06:06"),
            "MD" to Pair("09:00", "16:36")
        )

        ShiftRepository.ensureShiftDefinitionsFromParsedHours(hoursMap)

        // Verify no duplicate shift models were added
        val morningDefs = ShiftRepository.shiftDefinitions.filter {
            it.id == "mattina" || it.shortCode.equals("MAT", ignoreCase = true)
        }
        assertEquals("Should have exactly 1 morning definition", 1, morningDefs.size)
        assertEquals("06:30", morningDefs[0].startTime)
        assertEquals("14:06", morningDefs[0].endTime)

        val afternoonDefs = ShiftRepository.shiftDefinitions.filter {
            it.id == "pomeriggio" || it.shortCode.equals("POM", ignoreCase = true)
        }
        assertEquals("Should have exactly 1 afternoon definition", 1, afternoonDefs.size)
        assertEquals("14:30", afternoonDefs[0].startTime)
        assertEquals("22:06", afternoonDefs[0].endTime)

        val nightDefs = ShiftRepository.shiftDefinitions.filter {
            it.id == "notte" || it.shortCode.equals("NOT", ignoreCase = true)
        }
        assertEquals("Should have exactly 1 night definition", 1, nightDefs.size)
        assertEquals("22:30", nightDefs[0].startTime)
        assertEquals("06:06", nightDefs[0].endTime)

        // Count should not explode
        assertEquals(initialCount, ShiftRepository.shiftDefinitions.size)
    }

    @Test
    fun testSaveRotationsIntoCatalog() {
        val initialRotationsCount = ShiftRepository.rotations.size

        val squadA = ShiftRotation(
            id = "rotation_squadra_a_test",
            name = "Squadra A (7 Giorni)",
            totalDays = 7,
            daySequence = listOf("mattina", "mattina", "pomeriggio", "pomeriggio", "notte", "smonto_notte", "riposo"),
            description = "Ciclo settimanale standard"
        )
        val squadB = ShiftRotation(
            id = "rotation_squadra_b_test",
            name = "Squadra B (7 Giorni)",
            totalDays = 7,
            daySequence = listOf("pomeriggio", "pomeriggio", "notte", "notte", "smonto_notte", "riposo", "mattina"),
            description = "Ciclo settimanale B"
        )

        ShiftRepository.saveRotation(squadA)
        ShiftRepository.saveRotation(squadB)

        val retrievedA = ShiftRepository.rotations.find { it.id == "rotation_squadra_a_test" }
        assertNotNull(retrievedA)
        assertEquals("Squadra A (7 Giorni)", retrievedA!!.name)
        assertEquals(7, retrievedA.totalDays)
        assertEquals("mattina", retrievedA.daySequence[0])

        val retrievedB = ShiftRepository.rotations.find { it.id == "rotation_squadra_b_test" }
        assertNotNull(retrievedB)
        assertEquals(7, retrievedB!!.totalDays)
        assertEquals("pomeriggio", retrievedB.daySequence[0])

        // Clean up added test rotations
        ShiftRepository.deleteRotation("rotation_squadra_a_test")
        ShiftRepository.deleteRotation("rotation_squadra_b_test")
        assertEquals(initialRotationsCount, ShiftRepository.rotations.size)
    }

    // ==========================================
    // Tier 1: Auto User Squad Matching (F8)
    // ==========================================

    @Test
    fun testAutoUserSquadMatching_ExactName() {
        val extractedData = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra A", listOf("M", "P"), listOf("Rossi Mario", "Verdi Carlo")),
                GeminiSquadRotation("Squadra B", listOf("P", "N"), listOf("Graziani Marco", "Bianchi Luca")),
                GeminiSquadRotation("Squadra C", listOf("N", "M"), listOf("Ferrari Luigi"))
            )
        )

        val userConfiguredName = "Graziani Marco"

        // Search for user's squad
        var matchedSquad: GeminiSquadRotation? = null
        for (squad in extractedData.squads) {
            val found = squad.employees.any { empName ->
                NgeniaTurniParser.matchesEmployeeQuery(
                    ParsedEmployee(name = empName, teamName = squad.squadName),
                    userConfiguredName
                )
            }
            if (found) {
                matchedSquad = squad
                break
            }
        }

        assertNotNull("Should find matching squad for 'Graziani Marco'", matchedSquad)
        assertEquals("Squadra B", matchedSquad!!.squadName)
    }

    @Test
    fun testAutoUserSquadMatching_ReversedCaseInsensitiveAndDelimiters() {
        val extractedData = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra A", listOf("M", "P"), listOf("Mario Rossi")),
                GeminiSquadRotation("Squadra B", listOf("P", "N"), listOf("Marco Graziani"))
            )
        )

        // Query with reversed First/Last Name and all lowercase: "graziani marco"
        val query = "graziani marco"

        val matchingSquad = extractedData.squads.find { squad ->
            squad.employees.any { emp ->
                NgeniaTurniParser.matchesEmployeeQuery(
                    ParsedEmployee(name = emp, teamName = squad.squadName),
                    query
                )
            }
        }

        assertNotNull(matchingSquad)
        assertEquals("Squadra B", matchingSquad!!.squadName)
    }

    @Test
    fun testAutoUserSquadMatching_FallbackWhenUserNotFound() {
        val extractedData = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra A", listOf("M", "P"), listOf("Mario Rossi")),
                GeminiSquadRotation("Squadra B", listOf("P", "N"), listOf("Luca Bianchi"))
            )
        )

        val unknownUser = "Alessandro Del Piero"

        val matchingSquad = extractedData.squads.find { squad ->
            squad.employees.any { emp ->
                NgeniaTurniParser.matchesEmployeeQuery(
                    ParsedEmployee(name = emp, teamName = squad.squadName),
                    unknownUser
                )
            }
        } ?: extractedData.squads.firstOrNull()

        assertNotNull("Should fallback to first squad when user is not found", matchingSquad)
        assertEquals("Squadra A", matchingSquad!!.squadName)
    }

    // ==========================================
    // Tier 1: Star Toggle & Sort Logic (F8, F11)
    // ==========================================

    @Test
    fun testToggleDefaultRotation_TwoStateToggleAndSort() {
        val originalDefault = ShiftRepository.defaultRotationId

        try {
            val testRotId = "test_toggle_rot"
            val testRot = ShiftRotation(
                id = testRotId,
                name = "Squadra Speciale",
                totalDays = 7,
                daySequence = listOf("mattina", "pomeriggio", "notte", "smonto_notte", "riposo", "riposo", "riposo")
            )
            ShiftRepository.saveRotation(testRot)

            // 1. Toggle ON: activates test rotation
            val isNowDefault = ShiftRepository.toggleDefaultRotation(testRotId)
            assertTrue(isNowDefault)
            assertEquals(testRotId, ShiftRepository.defaultRotationId)

            val sortedList = ShiftRepository.getRotationsSortedByDefault()
            assertEquals("Default rotation must appear at index 0", testRotId, sortedList[0].id)

            // 2. Toggle OFF: revokes default, resets to empty string
            val isStillDefault = ShiftRepository.toggleDefaultRotation(testRotId)
            assertFalse(isStillDefault)
            assertEquals("", ShiftRepository.defaultRotationId)

            // Cleanup
            ShiftRepository.deleteRotation(testRotId)
        } finally {
            ShiftRepository.setDefaultRotation(originalDefault)
        }
    }

    // ==========================================
    // Tier 1: One-Click Calendar Setup (F9)
    // ==========================================

    @Test
    fun testApplyEmployeeShiftsToCalendar_SingleTurnistaAssignment() {
        val d1 = LocalDate.of(2026, 11, 2)
        val d2 = LocalDate.of(2026, 11, 3)
        val d3 = LocalDate.of(2026, 11, 4)

        val employee = ParsedEmployee(
            name = "Graziani Marco",
            role = "Operatore Specialista",
            teamName = "Squadra B",
            isOnCallGeneral = true,
            dayShifts = mapOf(
                d1 to "M",
                d2 to "P",
                d3 to "N"
            )
        )

        val applied = ShiftRepository.applyEmployeeShiftsToCalendar(
            employee = employee,
            overwrite = true,
            alsoSetOnCall = true
        )

        assertEquals("Should apply 3 shifts", 3, applied)

        // Verify d1 is morning
        val shiftsD1 = ShiftRepository.calendarShifts[d1]
        assertNotNull(shiftsD1)
        assertEquals(1, shiftsD1!!.size)
        assertEquals("mattina", shiftsD1[0].shiftDefId)
        assertTrue(ShiftRepository.isOnCall(d1))

        // Verify d2 is afternoon
        val shiftsD2 = ShiftRepository.calendarShifts[d2]
        assertNotNull(shiftsD2)
        assertEquals("pomeriggio", shiftsD2!![0].shiftDefId)
        assertTrue(ShiftRepository.isOnCall(d2))

        // Verify d3 is night
        val shiftsD3 = ShiftRepository.calendarShifts[d3]
        assertNotNull(shiftsD3)
        assertEquals("notte", shiftsD3!![0].shiftDefId)
        assertTrue(ShiftRepository.isOnCall(d3))
    }

    @Test
    fun testApplyEmployeeShiftsToCalendar_NoOverwritePreservesExistingShifts() {
        val d1 = LocalDate.of(2026, 11, 10)
        // Set pre-existing vacation shift
        ShiftRepository.assignShift(d1, "ferie")
        assertEquals("ferie", ShiftRepository.calendarShifts[d1]!![0].shiftDefId)

        val employee = ParsedEmployee(
            name = "Graziani Marco",
            teamName = "Squadra B",
            dayShifts = mapOf(d1 to "M")
        )

        // Apply without overwrite
        val applied = ShiftRepository.applyEmployeeShiftsToCalendar(
            employee = employee,
            overwrite = false,
            alsoSetOnCall = false
        )

        assertEquals(0, applied)
        assertEquals("ferie", ShiftRepository.calendarShifts[d1]!![0].shiftDefId)
    }

    // ==========================================
    // Tier 2: Modular Cycle Arithmetic & Edge Cases
    // ==========================================

    @Test
    fun testPlannedRotationShift_ModularCycle28Days() {
        val refDate = LocalDate.of(2026, 9, 28) // Monday
        val cycle28 = ShiftRotation(
            id = "test_cycle_28",
            name = "Ciclo 28 Giorni",
            totalDays = 28,
            daySequence = (1..28).map { "shift_$it" }
        )

        // Exactly on refDate -> day 0 (shift_1)
        val day0 = Math.floorMod(java.time.temporal.ChronoUnit.DAYS.between(refDate, refDate), 28)
        assertEquals(0, day0)
        assertEquals("shift_1", cycle28.daySequence[day0.toInt()])

        // Exactly 28 days later -> day 0 (shift_1)
        val datePlus28 = refDate.plusDays(28)
        val day28 = Math.floorMod(java.time.temporal.ChronoUnit.DAYS.between(refDate, datePlus28), 28)
        assertEquals(0, day28)
        assertEquals("shift_1", cycle28.daySequence[day28.toInt()])

        // Past date (7 days before refDate) -> index 21 (shift_22)
        val pastDate = refDate.minusDays(7)
        val dayPast = Math.floorMod(java.time.temporal.ChronoUnit.DAYS.between(refDate, pastDate), 28)
        assertEquals(21, dayPast)
        assertEquals("shift_22", cycle28.daySequence[dayPast.toInt()])
    }

    @Test
    fun testPlannedRotationShift_ModularCycle35Days() {
        val refDate = LocalDate.of(2026, 9, 28)
        val cycle35 = (1..35).map { "code_$it" }

        // 35 days cycle
        for (offset in 0 until 70) {
            val targetDate = refDate.plusDays(offset.toLong())
            val diff = java.time.temporal.ChronoUnit.DAYS.between(refDate, targetDate)
            val idx = Math.floorMod(diff, 35).toInt()
            assertEquals("code_${(offset % 35) + 1}", cycle35[idx])
        }
    }
}
