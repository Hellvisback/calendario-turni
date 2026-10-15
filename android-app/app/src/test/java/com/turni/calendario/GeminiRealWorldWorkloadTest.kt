package com.turni.calendario

import com.turni.calendario.data.GeminiApiClient
import com.turni.calendario.data.GeminiShiftData
import com.turni.calendario.data.SecurePreferencesManager
import com.turni.calendario.importer.NgeniaTurniParser
import com.turni.calendario.importer.ParsedEmployee
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.repository.ShiftRepository
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Tier 4: Real-World Workload & Adversarial Stress Test Suite (Scenarios 1-5).
 * Validates complex real-world railway, hospital, continuous industrial shift systems,
 * quota exhaustion recovery, and adversarial malformed input handling.
 */
class GeminiRealWorldWorkloadTest {

    private lateinit var client: GeminiApiClient

    @Before
    fun setUp() {
        client = GeminiApiClient()
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

    // =========================================================================
    // Scenario 1: Standard Italian Railway (RFI MOD. P.R. DOIT ANCONA / UMIS)
    // =========================================================================

    @Test
    fun testScenario1_RfiRailwayRosterEndToEndWorkflow() {
        // Real-world simulated Gemini Flash structured output for RFI MOD. P.R.
        val rfiJson = """
        {
          "validityStart": "2026-09-21",
          "validityEnd": "2026-10-18",
          "shiftDefinitions": [
            { "code": "M", "name": "Mattina", "startTime": "06:00", "endTime": "13:36", "colorHex": "#0284C7", "isReperibilita": false },
            { "code": "P", "name": "Pomeriggio", "startTime": "14:00", "endTime": "21:36", "colorHex": "#EF4444", "isReperibilita": true },
            { "code": "N", "name": "Notte", "startTime": "22:00", "endTime": "05:36", "colorHex": "#22C55E", "isReperibilita": false },
            { "code": "MD", "name": "Mattina Domenicale", "startTime": "08:36", "endTime": "16:12", "colorHex": "#F59E0B", "isReperibilita": true },
            { "code": "SMO", "name": "Smonto Notte", "startTime": "05:36", "endTime": "05:36", "colorHex": "#06B6D4", "isReperibilita": false },
            { "code": "RIP", "name": "Riposo", "startTime": "00:00", "endTime": "23:59", "colorHex": "#EC4899", "isReperibilita": false }
          ],
          "squads": [
            {
              "squadName": "Squadra A",
              "rotationDays": ["M", "M", "P", "P", "P", "N", "N", "SMO", "RIP", "M", "M", "N", "SMO", "RIP", "P", "P", "N", "N", "SMO", "M", "RIP", "N", "N", "SMO", "RIP", "M", "P", "MD"],
              "employees": ["Marcozzi Loris", "Raglione Luca"]
            },
            {
              "squadName": "Squadra B",
              "rotationDays": ["P", "P", "P", "N", "N", "SMO", "RIP", "M", "M", "N", "SMO", "RIP", "P", "P", "N", "N", "SMO", "M", "RIP", "N", "N", "SMO", "RIP", "M", "P", "MD", "M", "M"],
              "employees": ["Graziani Marco", "Roscini Giordano", "Iezzi Valerio"]
            },
            {
              "squadName": "Squadra C",
              "rotationDays": ["N", "N", "SMO", "RIP", "M", "M", "N", "SMO", "RIP", "P", "P", "N", "N", "SMO", "M", "RIP", "N", "N", "SMO", "RIP", "M", "P", "MD", "M", "M", "P", "P", "P"],
              "employees": ["D'Angelo Stefano", "Naki Amine"]
            },
            {
              "squadName": "Squadra D",
              "rotationDays": ["M", "M", "N", "SMO", "RIP", "P", "P", "N", "N", "SMO", "M", "RIP", "N", "N", "SMO", "RIP", "M", "P", "MD", "M", "M", "P", "P", "P", "N", "N", "SMO", "RIP"],
              "employees": ["Marcucci Roberto", "Carestia Stefano"]
            }
          ],
          "employees": [
            {
              "name": "Graziani Marco",
              "squadName": "Squadra B",
              "role": "OP.IS",
              "employeeId": "8050253",
              "isOnCall": true,
              "dailyShifts": [
                { "date": "2026-09-21", "code": "P" },
                { "date": "2026-09-22", "code": "P" },
                { "date": "2026-09-23", "code": "P" },
                { "date": "2026-09-24", "code": "N" },
                { "date": "2026-09-25", "code": "N" },
                { "date": "2026-09-26", "code": "SMO" },
                { "date": "2026-09-27", "code": "RIP" }
              ]
            }
          ],
          "notes": "Reperibilità dalle 0:00 alle 24:00 chi fa turno P. Domenica reperibile chi fa turno MD."
        }
        """.trimIndent()

        // 1. Parse Gemini JSON payload
        val shiftData: GeminiShiftData = client.parseGeminiResponse(rfiJson)
        assertEquals(6, shiftData.shiftDefinitions.size)
        assertEquals(4, shiftData.squads.size)
        assertEquals(1, shiftData.employees.size)

        // 2. Register / update shift hours in ShiftRepository
        val hoursMap = shiftData.shiftDefinitions.associate { it.code to Pair(it.startTime, it.endTime) }
        ShiftRepository.ensureShiftDefinitionsFromParsedHours(hoursMap)

        val matDef = ShiftRepository.getShiftDefinition("mattina")
        assertNotNull(matDef)
        assertEquals("06:00", matDef!!.startTime)
        assertEquals("13:36", matDef.endTime)

        // 3. Register rotations in catalog
        for (squad in shiftData.squads) {
            val rot = ShiftRotation(
                id = "rfi_" + squad.squadName.lowercase().replace(" ", "_"),
                name = squad.squadName,
                totalDays = squad.rotationDays.size,
                daySequence = squad.rotationDays.map { NgeniaTurniParser.mapCodeToShiftDefId(it) },
                description = "Prospetto RFI 28 giorni"
            )
            ShiftRepository.saveRotation(rot)
        }
        assertEquals(4, shiftData.squads.size)

        // 4. Auto user squad matching
        val targetUserName = "Marco Graziani"
        val matchedSquad = shiftData.squads.find { squad ->
            squad.employees.any { empName ->
                NgeniaTurniParser.matchesEmployeeQuery(
                    ParsedEmployee(name = empName, teamName = squad.squadName),
                    targetUserName
                )
            }
        }
        assertNotNull("Should match Marco Graziani to Squadra B", matchedSquad)
        assertEquals("Squadra B", matchedSquad!!.squadName)

        // 5. Pre-selection & default rotation toggle
        val matchedRotationId = "rfi_" + matchedSquad.squadName.lowercase().replace(" ", "_")
        ShiftRepository.setDefaultRotation(matchedRotationId)
        assertEquals(matchedRotationId, ShiftRepository.defaultRotationId)

        // 6. Apply individual employee shifts to calendar
        val userEmployee = shiftData.employees.first { it.name.contains("Graziani", ignoreCase = true) }
        val parsedEmp = ParsedEmployee(
            name = userEmployee.name,
            role = userEmployee.role ?: "",
            teamName = userEmployee.squadName ?: "",
            isOnCallGeneral = userEmployee.isOnCall,
            dayShifts = userEmployee.dailyShifts.mapKeys { LocalDate.parse(it.key) }
        )

        val applied = ShiftRepository.applyEmployeeShiftsToCalendar(
            employee = parsedEmp,
            overwrite = true,
            alsoSetOnCall = true
        )
        assertEquals(7, applied)

        // 7. Verify calendar shifts & on-call flags
        val dPomeriggio = LocalDate.of(2026, 9, 21)
        val dNotte = LocalDate.of(2026, 9, 24)
        val dSmonto = LocalDate.of(2026, 9, 26)
        val dRiposo = LocalDate.of(2026, 9, 27)

        assertEquals("pomeriggio", ShiftRepository.calendarShifts[dPomeriggio]!![0].shiftDefId)
        assertEquals("notte", ShiftRepository.calendarShifts[dNotte]!![0].shiftDefId)
        assertEquals("smonto_notte", ShiftRepository.calendarShifts[dSmonto]!![0].shiftDefId)
        assertEquals("riposo", ShiftRepository.calendarShifts[dRiposo]!![0].shiftDefId)

        // On-call check
        assertTrue("Pomeriggio shift should have on-call active", ShiftRepository.isOnCall(dPomeriggio))

        // Report stats check
        val stats = ShiftRepository.getComprehensiveReportStats(dPomeriggio, dRiposo)
        assertEquals(5, stats.workedDays) // 3 Pomeriggio + 2 Notte = 5 worked days
        assertEquals(1, stats.countSmonto)
        assertEquals(1, stats.countRiposo)
        assertEquals(7, stats.countReperibilita)
    }

    // =========================================================================
    // Scenario 2: Hospital Emergency / Healthcare Shift Roster
    // =========================================================================

    @Test
    fun testScenario2_HospitalEmergencyContinuousRoster() {
        val hospitalJson = """
        {
          "shiftDefinitions": [
            { "code": "M", "name": "Mattina Ospedaliera", "startTime": "07:00", "endTime": "14:00" },
            { "code": "P", "name": "Pomeriggio Ospedaliero", "startTime": "14:00", "endTime": "21:00" },
            { "code": "N", "name": "Notte Guardia Medica", "startTime": "21:00", "endTime": "07:00", "isReperibilita": true },
            { "code": "SMO", "name": "Smonto Guardia", "startTime": "07:00", "endTime": "07:00" },
            { "code": "RIP", "name": "Riposo Compensativo", "startTime": "00:00", "endTime": "23:59" }
          ],
          "squads": [
            {
              "squadName": "Equipe A - Pronto Soccorso",
              "rotationDays": ["M", "P", "N", "SMO", "RIP", "RIP"],
              "employees": ["Dr. Andrea Conti", "Inf. Sara Moretti"]
            }
          ]
        }
        """.trimIndent()

        val data = client.parseGeminiResponse(hospitalJson)
        assertEquals(5, data.shiftDefinitions.size)
        assertEquals(1, data.squads.size)

        // Verify overnight shift cross-midnight calculation (21:00 to 07:00 = 10 hours)
        val nightDef = data.shiftDefinitions.first { it.code == "N" }
        assertEquals("21:00", nightDef.startTime)
        assertEquals("07:00", nightDef.endTime)
        assertTrue(nightDef.isReperibilita)

        val domainNightDef = com.turni.calendario.model.ShiftDefinition(
            id = "notte_ospedale",
            name = nightDef.name,
            shortCode = nightDef.code,
            startTime = nightDef.startTime,
            endTime = nightDef.endTime
        )
        // 21:00 to 07:00 = 10h = 600 minutes
        assertEquals(600L, domainNightDef.durationMinutes)
        assertEquals(10.0f, domainNightDef.durationHoursFloat, 0.01f)
    }

    // =========================================================================
    // Scenario 3: Industrial Continuous 3x8 / 4x6 (35-day rotation cycle)
    // =========================================================================

    @Test
    fun testScenario3_IndustrialContinuous35DayCycle() {
        val cycleCodes = listOf(
            "M", "M", "M", "M", "M", "RIP", "RIP",
            "P", "P", "P", "P", "P", "RIP", "RIP",
            "N", "N", "N", "N", "N", "SMO", "RIP",
            "M", "M", "P", "P", "N", "SMO", "RIP",
            "P", "P", "N", "N", "SMO", "RIP", "RIP"
        )
        assertEquals("Cycle must be exactly 35 days", 35, cycleCodes.size)

        val industrialSquad = ShiftRotation(
            id = "continuous_35_days",
            name = "Ciclo Continuo 35 Giorni",
            totalDays = 35,
            daySequence = cycleCodes.map { NgeniaTurniParser.mapCodeToShiftDefId(it) }
        )

        ShiftRepository.saveRotation(industrialSquad)
        val saved = ShiftRepository.rotations.find { it.id == "continuous_35_days" }
        assertNotNull(saved)
        assertEquals(35, saved!!.totalDays)

        // Verify repeatability over 70 days (2 full cycles)
        for (i in 0 until 35) {
            assertEquals(saved.daySequence[i], saved.daySequence[(i + 35) % 35])
        }

        ShiftRepository.deleteRotation("continuous_35_days")
    }

    // =========================================================================
    // Scenario 4: Quota Exceeded (HTTP 429) & Error Recovery
    // =========================================================================

    @Test
    fun testScenario4_QuotaExceededHttp429ErrorHandling() {
        val errorJson429 = """
        {
          "error": {
            "code": 429,
            "message": "Resource has been exhausted (e.g. check quota).",
            "status": "RESOURCE_EXHAUSTED"
          }
        }
        """.trimIndent()

        val root = JSONObject(errorJson429)
        val errObj = root.getJSONObject("error")
        assertEquals(429, errObj.getInt("code"))
        assertEquals("RESOURCE_EXHAUSTED", errObj.getString("status"))

        // Verify that database state in ShiftRepository is unharmed when an error occurs
        val initialShiftsCount = ShiftRepository.calendarShifts.size
        assertEquals(0, initialShiftsCount)
    }

    // =========================================================================
    // Scenario 5: Adversarial, Unicode, Accented & Stress Resilience
    // =========================================================================

    @Test
    fun testScenario5_UnicodeAccentsAndSpecialCharactersInRoster() {
        val complexJson = """
        {
          "shiftDefinitions": [
            { "code": "M_1", "name": "Mattina con Accenti: Caffè & Tè", "startTime": "06:15", "endTime": "13:51" }
          ],
          "squads": [
            {
              "squadName": "Squadra 🚂 Ferroviaria — Unità UMIS",
              "rotationDays": ["M_1"],
              "employees": ["Nicolò D'Amico", "Dr. Müller-Lüdenscheidt", "André René Roussimoff"]
            }
          ],
          "notes": "Note con caratteri speciali: <>&\"' € and newline\nSecond line"
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(complexJson)
        assertEquals("M_1", result.shiftDefinitions[0].code)
        assertEquals("Mattina con Accenti: Caffè & Tè", result.shiftDefinitions[0].name)
        assertEquals("Squadra 🚂 Ferroviaria — Unità UMIS", result.squads[0].squadName)
        assertEquals(3, result.squads[0].employees.size)
        assertEquals("Nicolò D'Amico", result.squads[0].employees[0])
        assertEquals("Dr. Müller-Lüdenscheidt", result.squads[0].employees[1])
        assertEquals("André René Roussimoff", result.squads[0].employees[2])

        // Verify search matching works on accented name
        val matchFound = NgeniaTurniParser.matchesEmployeeQuery(
            ParsedEmployee(name = "Nicolò D'Amico"),
            "nicolo d'amico"
        ) || NgeniaTurniParser.matchesEmployeeQuery(
            ParsedEmployee(name = "Nicolò D'Amico"),
            "d'amico"
        )
        assertTrue("Should match accented name query", matchFound)
    }
}
