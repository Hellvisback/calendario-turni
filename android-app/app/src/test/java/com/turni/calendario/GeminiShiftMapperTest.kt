package com.turni.calendario

import com.turni.calendario.data.ExtractedSetupResult
import com.turni.calendario.data.GeminiEmployeeAssignment
import com.turni.calendario.data.GeminiShiftData
import com.turni.calendario.data.GeminiShiftDefinition
import com.turni.calendario.data.GeminiShiftMapper
import com.turni.calendario.data.GeminiSquadRotation
import com.turni.calendario.data.SecurePreferencesManager
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.repository.ShiftRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [GeminiShiftMapper] (Milestone 3).
 * Verifies domain mapping to ShiftDefinition, ShiftRotation, color parsing,
 * intelligent user squad matching, and duplicate-safe repository registration.
 */
class GeminiShiftMapperTest {

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
    // 1. Shift Definition Mapping & Palette Tests
    // ==========================================

    @Test
    fun testMapToShiftDefinitions_BasicPropertiesAndUUID() {
        val extracted = GeminiShiftData(
            shiftDefinitions = listOf(
                GeminiShiftDefinition(code = "M", name = "Mattina", startTime = "06:00", endTime = "13:36", colorHex = "#38BDF8"),
                GeminiShiftDefinition(code = "P", name = "Pomeriggio", startTime = "14:00", endTime = "21:36", colorHex = "#EF4444"),
                GeminiShiftDefinition(code = "N", name = "Notte", startTime = "22:00", endTime = "05:36", colorHex = "#10B981")
            )
        )

        val defs = GeminiShiftMapper.mapToShiftDefinitions(extracted)
        assertEquals(3, defs.size)

        // UUID uniqueness
        val ids = defs.map { it.id }.toSet()
        assertEquals("Each shift definition must have a unique UUID", 3, ids.size)
        assertTrue(defs.all { it.id.isNotBlank() })

        // Codes and names
        assertEquals("M", defs[0].shortCode)
        assertEquals("Mattina", defs[0].name)
        assertEquals("06:00", defs[0].startTime)
        assertEquals("13:36", defs[0].endTime)
        assertEquals(0xFF38BDF8L, defs[0].colorHex)
        assertEquals(ShiftCategory.LAVORO, defs[0].category)
        assertFalse(defs[0].isAllDay)

        assertEquals("P", defs[1].shortCode)
        assertEquals("Pomeriggio", defs[1].name)
        assertEquals(0xFFEF4444L, defs[1].colorHex)
        assertEquals(ShiftCategory.LAVORO, defs[1].category)

        assertEquals("N", defs[2].shortCode)
        assertEquals("Notte", defs[2].name)
        assertEquals(0xFF10B981L, defs[2].colorHex)
        assertEquals(ShiftCategory.LAVORO, defs[2].category)
    }

    @Test
    fun testMapToShiftDefinitions_CategoryAndIconDetection() {
        val extracted = GeminiShiftData(
            shiftDefinitions = listOf(
                GeminiShiftDefinition(code = "RIP", name = "Riposo", startTime = "00:00", endTime = "23:59"),
                GeminiShiftDefinition(code = "SMO", name = "Smonto Notte", startTime = "05:36", endTime = "05:36"),
                GeminiShiftDefinition(code = "FER", name = "Ferie Estive", startTime = "00:00", endTime = "23:59"),
                GeminiShiftDefinition(code = "MAL", name = "Malattia", startTime = "00:00", endTime = "23:59"),
                GeminiShiftDefinition(code = "REP", name = "Reperibilità P", startTime = "14:00", endTime = "21:36", isReperibilita = true)
            )
        )

        val defs = GeminiShiftMapper.mapToShiftDefinitions(extracted)
        assertEquals(5, defs.size)

        // Riposo
        assertEquals(ShiftCategory.RIPOSO, defs[0].category)
        assertEquals("weekend", defs[0].iconKey)
        assertTrue(defs[0].isAllDay)

        // Smonto
        assertEquals(ShiftCategory.RIPOSO, defs[1].category)
        assertEquals("bedtime", defs[1].iconKey)
        assertTrue(defs[1].isAllDay)

        // Ferie
        assertEquals(ShiftCategory.ASSENZA, defs[2].category)
        assertEquals("beach_access", defs[2].iconKey)

        // Malattia
        assertEquals(ShiftCategory.ASSENZA, defs[3].category)
        assertEquals("local_hospital", defs[3].iconKey)

        // Reperibilità
        assertEquals("phone_in_talk", defs[4].iconKey)
    }

    @Test
    fun testMapToShiftDefinitions_PaletteAllocationWhenColorMissingOrInvalid() {
        val extracted = GeminiShiftData(
            shiftDefinitions = listOf(
                GeminiShiftDefinition(code = "S1", name = "Shift 1", startTime = "08:00", endTime = "16:00", colorHex = null),
                GeminiShiftDefinition(code = "S2", name = "Shift 2", startTime = "08:00", endTime = "16:00", colorHex = ""),
                GeminiShiftDefinition(code = "S3", name = "Shift 3", startTime = "08:00", endTime = "16:00", colorHex = "invalid_hex"),
                GeminiShiftDefinition(code = "S4", name = "Shift 4", startTime = "08:00", endTime = "16:00", colorHex = "0xFF6366F1")
            )
        )

        val defs = GeminiShiftMapper.mapToShiftDefinitions(extracted)
        assertEquals(4, defs.size)

        // S1 -> first default palette color (#38BDF8)
        assertEquals(GeminiShiftMapper.DEFAULT_PALETTE[0], defs[0].colorHex)
        // S2 -> second default palette color (#F59E0B)
        assertEquals(GeminiShiftMapper.DEFAULT_PALETTE[1], defs[1].colorHex)
        // S3 -> third default palette color (#10B981)
        assertEquals(GeminiShiftMapper.DEFAULT_PALETTE[2], defs[2].colorHex)
        // S4 -> explicitly parsed 0xFF6366F1
        assertEquals(0xFF6366F1L, defs[3].colorHex)
    }

    // ==========================================
    // 2. Shift Rotation Mapping Tests
    // ==========================================

    @Test
    fun testMapToRotations_CycleLengthAndSequenceResolution() {
        val shiftDefs = listOf(
            ShiftDefinition(id = "mattina", name = "Mattina", shortCode = "M"),
            ShiftDefinition(id = "pomeriggio", name = "Pomeriggio", shortCode = "P"),
            ShiftDefinition(id = "notte", name = "Notte", shortCode = "N"),
            ShiftDefinition(id = "riposo", name = "Riposo", shortCode = "RIP")
        )

        val extracted = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra Alpha",
                    rotationDays = listOf("M", "M", "P", "P", "N", "RIP", "RIP"),
                    cycleDays = 7
                ),
                GeminiSquadRotation(
                    squadName = "Squadra Beta",
                    rotationDays = listOf("P", "N", "RIP", "M"),
                    cycleDays = 4
                )
            )
        )

        val rotations = GeminiShiftMapper.mapToRotations(extracted, shiftDefs)
        assertEquals(2, rotations.size)

        val rotAlpha = rotations[0]
        assertEquals("rotazione_squadra_alpha", rotAlpha.id)
        assertEquals("Squadra Alpha", rotAlpha.name)
        assertEquals(7, rotAlpha.totalDays)
        assertEquals(listOf("mattina", "mattina", "pomeriggio", "pomeriggio", "notte", "riposo", "riposo"), rotAlpha.daySequence)

        val rotBeta = rotations[1]
        assertEquals("rotazione_squadra_beta", rotBeta.id)
        assertEquals(4, rotBeta.totalDays)
        assertEquals(listOf("pomeriggio", "notte", "riposo", "mattina"), rotBeta.daySequence)
    }

    @Test
    fun testMapToRotations_FallbackToStandardShiftIdsWhenDefinitionsEmpty() {
        val extracted = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra Standard",
                    rotationDays = listOf("M", "P", "N", "SMO", "RIP")
                )
            )
        )

        val rotations = GeminiShiftMapper.mapToRotations(extracted, emptyList())
        assertEquals(1, rotations.size)
        val rot = rotations[0]
        assertEquals(5, rot.totalDays)
        assertEquals(listOf("mattina", "pomeriggio", "notte", "smonto_notte", "riposo"), rot.daySequence)
    }

    // ==========================================
    // 3. Auto User Squad Matching Tests
    // ==========================================

    @Test
    fun testMatchUserSquad_ExactAndCaseInsensitive() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra A", listOf("M", "P"), listOf("Luigi Verdi", "Carlo Bianchi")),
                GeminiSquadRotation("Squadra B", listOf("P", "N"), listOf("Marco Graziani", "Paolo Neri"))
            )
        )

        assertEquals("Squadra B", GeminiShiftMapper.matchUserSquad(data, "Marco Graziani"))
        assertEquals("Squadra B", GeminiShiftMapper.matchUserSquad(data, "marco graziani"))
        assertEquals("Squadra B", GeminiShiftMapper.matchUserSquad(data, "MARCO GRAZIANI"))
        assertEquals("Squadra A", GeminiShiftMapper.matchUserSquad(data, "luigi verdi"))
    }

    @Test
    fun testMatchUserSquad_InvertedNameOrderAndWhitespace() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra 1", listOf("M"), listOf("Mario Rossi")),
                GeminiSquadRotation("Squadra 2", listOf("P"), listOf("Marco Graziani"))
            )
        )

        // Inverted: "Graziani Marco"
        assertEquals("Squadra 2", GeminiShiftMapper.matchUserSquad(data, "Graziani Marco"))
        // Excess whitespace and inverted
        assertEquals("Squadra 2", GeminiShiftMapper.matchUserSquad(data, "   graziani     marco   "))
        // Delimiters
        assertEquals("Squadra 2", GeminiShiftMapper.matchUserSquad(data, "Graziani, Marco"))
        assertEquals("Squadra 2", GeminiShiftMapper.matchUserSquad(data, "graziani_marco"))
    }

    @Test
    fun testMatchUserSquad_SingleTokenSurnameOrForename() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra Nord", listOf("M"), listOf("Giuseppe Garibaldi")),
                GeminiSquadRotation("Squadra Sud", listOf("P"), listOf("Marco Graziani"))
            )
        )

        assertEquals("Squadra Sud", GeminiShiftMapper.matchUserSquad(data, "Graziani"))
        assertEquals("Squadra Nord", GeminiShiftMapper.matchUserSquad(data, "Garibaldi"))
    }

    @Test
    fun testMatchUserSquad_FromEmployeeAssignmentsList() {
        val data = GeminiShiftData(
            squads = emptyList(),
            employees = listOf(
                GeminiEmployeeAssignment(name = "Alessandro Del Piero", squadName = "Squadra Juventus"),
                GeminiEmployeeAssignment(name = "Francesco Totti", squadName = "Squadra Roma")
            )
        )

        assertEquals("Squadra Juventus", GeminiShiftMapper.matchUserSquad(data, "Del Piero Alessandro"))
        assertEquals("Squadra Roma", GeminiShiftMapper.matchUserSquad(data, "Totti"))
    }

    @Test
    fun testMatchUserSquad_AmbiguousTokensPrefersBestMatch() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra A", listOf("M"), listOf("Marco Rossi")),
                GeminiSquadRotation("Squadra B", listOf("P"), listOf("Marco Graziani"))
            )
        )

        // Both contain "Marco", but Squadra B matches BOTH "Marco" and "Graziani"
        assertEquals("Squadra B", GeminiShiftMapper.matchUserSquad(data, "Marco Graziani"))
        assertEquals("Squadra A", GeminiShiftMapper.matchUserSquad(data, "Marco Rossi"))
    }

    @Test
    fun testMatchUserSquad_NullOrNotFoundReturnsNull() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra A", listOf("M"), listOf("Mario Rossi"))
            )
        )

        assertNull(GeminiShiftMapper.matchUserSquad(data, "Unknown Worker"))
        assertNull(GeminiShiftMapper.matchUserSquad(data, ""))
        assertNull(GeminiShiftMapper.matchUserSquad(data, "   "))
        assertNull(GeminiShiftMapper.matchUserSquad(data, null))
    }

    // ==========================================
    // 4. Repository Integration Tests
    // ==========================================

    @Test
    fun testRegisterExtractedData_UpdatesExistingShiftsWithoutDuplicateIds() {
        val initialCount = ShiftRepository.shiftDefinitions.size

        // Extracted data specifies updated times for Mattina (06:30-14:06) and a brand new shift ("SPEC")
        val data = GeminiShiftData(
            shiftDefinitions = listOf(
                GeminiShiftDefinition(code = "M", name = "Mattina", startTime = "06:30", endTime = "14:06"),
                GeminiShiftDefinition(code = "SPEC", name = "Turno Speciale", startTime = "10:00", endTime = "18:00")
            ),
            squads = listOf(
                GeminiSquadRotation("Squadra Test", listOf("M", "SPEC"))
            )
        )

        val result = GeminiShiftMapper.registerExtractedData(
            repository = ShiftRepository,
            data = data,
            targetUserName = null
        )
        assertEquals(2, result.registeredShifts.size)
        assertEquals(1, result.registeredRotations.size)

        // Existing Mattina should be updated, not duplicated
        val morningDefs = ShiftRepository.shiftDefinitions.filter {
            it.id == "mattina" || it.shortCode.equals("M", true) || it.shortCode.equals("MAT", true)
        }
        assertEquals("Should have exactly 1 morning definition", 1, morningDefs.size)
        assertEquals("06:30", morningDefs[0].startTime)
        assertEquals("14:06", morningDefs[0].endTime)

        // Brand new shift "SPEC" was added
        val specDef = ShiftRepository.shiftDefinitions.find { it.shortCode == "SPEC" }
        assertNotNull(specDef)
        assertEquals("10:00", specDef!!.startTime)

        // Size increased by exactly 1 (the brand new shift)
        assertEquals(initialCount + 1, ShiftRepository.shiftDefinitions.size)

        // Cleanup
        ShiftRepository.deleteShiftDefinition(specDef.id)
    }

    @Test
    fun testRegisterExtractedData_RegistersRotationsAndSetsDefault() {
        val originalDefault = ShiftRepository.defaultRotationId

        try {
            val data = GeminiShiftData(
                shiftDefinitions = listOf(
                    GeminiShiftDefinition(code = "M", name = "Mattina", startTime = "06:00", endTime = "13:36"),
                    GeminiShiftDefinition(code = "P", name = "Pomeriggio", startTime = "14:00", endTime = "21:36")
                ),
                squads = listOf(
                    GeminiSquadRotation("Squadra A", listOf("M", "P"), listOf("Mario Rossi")),
                    GeminiSquadRotation("Squadra B", listOf("P", "M"), listOf("Marco Graziani"))
                )
            )

            val result = GeminiShiftMapper.registerExtractedData(
                repository = ShiftRepository,
                data = data,
                targetUserName = "Graziani Marco"
            )

            assertEquals("Squadra B", result.matchedSquadName)
            assertEquals("rotazione_squadra_b", result.defaultRotationId)
            assertEquals("rotazione_squadra_b", ShiftRepository.defaultRotationId)

            val savedRotB = ShiftRepository.rotations.find { it.id == "rotazione_squadra_b" }
            assertNotNull(savedRotB)
            assertEquals("Squadra B", savedRotB!!.name)

            // Cleanup added rotations
            ShiftRepository.deleteRotation("rotazione_squadra_a")
            ShiftRepository.deleteRotation("rotazione_squadra_b")
        } finally {
            ShiftRepository.setDefaultRotation(originalDefault)
        }
    }

    @Test
    fun testRegisterExtractedData_FallbackToSecurePreferencesWhenTargetUserNull() {
        val originalDefault = ShiftRepository.defaultRotationId
        SecurePreferencesManager.saveUserName(name = "Marco Graziani")

        try {
            val data = GeminiShiftData(
                squads = listOf(
                    GeminiSquadRotation("Squadra Alpha", listOf("M"), listOf("Mario Rossi")),
                    GeminiSquadRotation("Squadra Beta", listOf("P"), listOf("Marco Graziani"))
                )
            )

            val result = GeminiShiftMapper.registerExtractedData(
                repository = ShiftRepository,
                data = data,
                targetUserName = null // Should read from SecurePreferencesManager
            )

            assertEquals("Squadra Beta", result.matchedSquadName)
            assertEquals("rotazione_squadra_beta", result.defaultRotationId)
            assertEquals("rotazione_squadra_beta", ShiftRepository.defaultRotationId)

            ShiftRepository.deleteRotation("rotazione_squadra_alpha")
            ShiftRepository.deleteRotation("rotazione_squadra_beta")
        } finally {
            ShiftRepository.setDefaultRotation(originalDefault)
        }
    }

    // ==========================================
    // 5. Enhanced Normalization, Anti-Homonymy & Fallback Tests
    // ==========================================

    @Test
    fun testNormalizeName_AccentsApostrophesPunctuation() {
        // Accents / Diacritics
        assertEquals("niccolo", GeminiShiftMapper.normalizeName("Niccolò"))
        assertEquals("rene dupont", GeminiShiftMapper.normalizeName("René Dupont"))
        assertEquals("jose garcia", GeminiShiftMapper.normalizeName("José Garcia"))

        // Typographical and Unicode Apostrophes
        assertEquals("marco d angelo", GeminiShiftMapper.normalizeName("Marco D’Angelo")) // U+2019
        assertEquals("marco d angelo", GeminiShiftMapper.normalizeName("Marco D‘Angelo")) // U+2018
        assertEquals("marco d angelo", GeminiShiftMapper.normalizeName("Marco D'Angelo"))  // U+0027
        assertEquals("dell amico", GeminiShiftMapper.normalizeName("Dell′Amico"))        // U+2032

        // Delimiters and Noise tokenization
        assertEquals(listOf("marco", "graziani", "capoturno"), GeminiShiftMapper.normalizeName("Marco Graziani (Capoturno)").split(Regex("\\s+")).filter { it.isNotBlank() })
        assertEquals(listOf("marco", "graziani"), GeminiShiftMapper.normalizeName("Marco / Graziani").split(Regex("\\s+")).filter { it.isNotBlank() })
        assertEquals(listOf("marco", "graziani"), GeminiShiftMapper.normalizeName("   Marco \t\t  Graziani  \n").split(Regex("\\s+")).filter { it.isNotBlank() })
    }

    @Test
    fun testMatchUserSquad_DiacriticsAndTypographicalApostrophes() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra A", listOf("M"), listOf("Niccolò Paganini")),
                GeminiSquadRotation("Squadra B", listOf("P"), listOf("Marco D'Angelo")),
                GeminiSquadRotation("Squadra C", listOf("N"), listOf("René Dupont"))
            )
        )

        // Query without accent matches candidate with accent
        assertEquals("Squadra A", GeminiShiftMapper.matchUserSquad(data, "Niccolo Paganini"))
        assertEquals("Squadra A", GeminiShiftMapper.matchUserSquad(data, "Niccolo"))
        assertEquals("Squadra C", GeminiShiftMapper.matchUserSquad(data, "Rene"))

        // Curly typographical apostrophe (U+2019) matches ASCII apostrophe candidate
        assertEquals("Squadra B", GeminiShiftMapper.matchUserSquad(data, "Marco D’Angelo"))
        assertEquals("Squadra B", GeminiShiftMapper.matchUserSquad(data, "D’Angelo"))
    }

    @Test
    fun testMatchUserSquad_CompoundNamesWithoutSpaces() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra Alfa", listOf("M"), listOf("Gianmarco Rossi")),
                GeminiSquadRotation("Squadra Beta", listOf("P"), listOf("Piergiorgio Bianchi"))
            )
        )

        // Split query vs fused candidate
        assertEquals("Squadra Alfa", GeminiShiftMapper.matchUserSquad(data, "Gian Marco Rossi"))
        val scoreSplit = GeminiShiftMapper.calculateNameMatchScore("Gian Marco Rossi", "Gianmarco Rossi")
        assertEquals(90, scoreSplit)

        // Fused query vs split candidate
        val dataSplit = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra Gamma", listOf("M"), listOf("Gian Marco Rossi"))
            )
        )
        assertEquals("Squadra Gamma", GeminiShiftMapper.matchUserSquad(dataSplit, "Gianmarco Rossi"))
        val scoreFused = GeminiShiftMapper.calculateNameMatchScore("Gianmarco Rossi", "Gian Marco Rossi")
        assertEquals(90, scoreFused)
    }

    @Test
    fun testMatchUserSquad_AntiHomonymySafeguard() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra 1", listOf("M"), listOf("Andrea Bianchi")),
                GeminiSquadRotation("Squadra 2", listOf("P"), listOf("Luigi Verdi"))
            )
        )

        // "Andrea Romano" shares 1 token with "Andrea Bianchi", but differs on surname
        val score = GeminiShiftMapper.calculateNameMatchScore("Andrea Romano", "Andrea Bianchi")
        assertEquals(35, score)
        assertNull("Partial homonymy must NOT match a different person", GeminiShiftMapper.matchUserSquad(data, "Andrea Romano"))
    }

    @Test
    fun testMatchUserSquad_ShortTokenNoSubstringCollision() {
        // Query "Dan" must not match inside "Giordano Bruno"
        val scoreDan = GeminiShiftMapper.calculateNameMatchScore("Dan", "Giordano Bruno")
        assertEquals(0, scoreDan)

        // Query "Leo" must not match inside "Pantaleo De Rosa"
        val scoreLeo = GeminiShiftMapper.calculateNameMatchScore("Leo", "Pantaleo De Rosa")
        assertEquals(0, scoreLeo)
    }

    @Test
    fun testResolveShiftDefId_SanitizationAndFallbacks() {
        val shiftDefs = listOf(
            ShiftDefinition(id = "custom_1", name = "Speciale", shortCode = "SPEC")
        )

        // Noise characters stripped
        assertEquals("mattina", GeminiShiftMapper.resolveShiftDefId("*M#", shiftDefs))
        assertEquals("pomeriggio", GeminiShiftMapper.resolveShiftDefId("(P)", shiftDefs))
        assertEquals("custom_1", GeminiShiftMapper.resolveShiftDefId("[SPEC]", shiftDefs))

        // Unknown codes fallback to riposo
        assertEquals("riposo", GeminiShiftMapper.resolveShiftDefId("UNKNOWN_99", shiftDefs))
        assertEquals("riposo", GeminiShiftMapper.resolveShiftDefId("", shiftDefs))
        assertEquals("riposo", GeminiShiftMapper.resolveShiftDefId("   ", shiftDefs))
    }
}
