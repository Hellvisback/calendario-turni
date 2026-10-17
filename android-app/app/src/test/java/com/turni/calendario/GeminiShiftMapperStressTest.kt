package com.turni.calendario

import com.turni.calendario.data.GeminiEmployeeAssignment
import com.turni.calendario.data.GeminiShiftData
import com.turni.calendario.data.GeminiShiftDefinition
import com.turni.calendario.data.GeminiShiftMapper
import com.turni.calendario.data.GeminiSquadRotation
import com.turni.calendario.data.SecurePreferencesManager
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.repository.ShiftRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Adversarial Stress Test Suite for [GeminiShiftMapper] & [ShiftRepository].
 * Executed by Challenger 2 for Milestone 3.
 *
 * Verifies:
 * 1. Unusual rotation cycle lengths (1-day, 3-day, 21-day, 35-day, 42-day cycles, empty cycles).
 * 2. Unmapped / unknown shift codes in rotation sequences & graceful fallback.
 * 3. Repetitive / idempotent registrations and drift prevention.
 * 4. Multi-threaded concurrency during registration and planned shift lookup.
 */
class GeminiShiftMapperStressTest {

    private val originalDefaultRotation = ShiftRepository.defaultRotationId
    private val originalDefinitionsSnapshot = ShiftRepository.shiftDefinitions.toList()
    private val originalRotationsSnapshot = ShiftRepository.rotations.toList()

    @Before
    fun setUp() {
        ShiftRepository.calendarShifts.clear()
        ShiftRepository.dayExtraInfo.clear()
        SecurePreferencesManager.clear()
        ShiftRepository.shiftDefinitions.clear()
        ShiftRepository.shiftDefinitions.addAll(originalDefinitionsSnapshot)
        ShiftRepository.rotations.clear()
        ShiftRepository.rotations.addAll(originalRotationsSnapshot)
        ShiftRepository.setDefaultRotation(originalDefaultRotation)
    }

    @After
    fun tearDown() {
        ShiftRepository.calendarShifts.clear()
        ShiftRepository.dayExtraInfo.clear()
        SecurePreferencesManager.clear()

        // Restore initial repository state
        ShiftRepository.shiftDefinitions.clear()
        ShiftRepository.shiftDefinitions.addAll(originalDefinitionsSnapshot)

        ShiftRepository.rotations.clear()
        ShiftRepository.rotations.addAll(originalRotationsSnapshot)

        ShiftRepository.setDefaultRotation(originalDefaultRotation)
    }

    // =========================================================================
    // 1. UNUSUAL ROTATION CYCLE LENGTHS (1, 3, 21, 35, 42 days, empty)
    // =========================================================================

    @Test
    fun testUnusualCycleLengths_1DayCycle() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra Monotono",
                    rotationDays = listOf("M"),
                    cycleDays = 1
                )
            )
        )

        val result = GeminiShiftMapper.registerExtractedData(ShiftRepository, data)
        assertEquals(1, result.registeredRotations.size)

        val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_monotono" }
        assertNotNull("1-day rotation must be saved in repository", rot)
        assertEquals(1, rot!!.daySequence.size)
        assertEquals(1, rot.totalDays)
        assertEquals("mattina", rot.daySequence[0])

        // Verify planned shift calculation over negative past, reference date, and distant future
        val pastDate = ShiftRepository.cycleReferenceDate.minusDays(365)
        val refDate = ShiftRepository.cycleReferenceDate
        val futureDate = ShiftRepository.cycleReferenceDate.plusDays(1000)

        val pastShift = ShiftRepository.getPlannedRotationShift(pastDate, rot.id)
        val refShift = ShiftRepository.getPlannedRotationShift(refDate, rot.id)
        val futureShift = ShiftRepository.getPlannedRotationShift(futureDate, rot.id)

        assertNotNull(pastShift)
        assertEquals("mattina", pastShift!!.id)
        assertNotNull(refShift)
        assertEquals("mattina", refShift!!.id)
        assertNotNull(futureShift)
        assertEquals("mattina", futureShift!!.id)
    }

    @Test
    fun testUnusualCycleLengths_3DayCycle() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra Triade",
                    rotationDays = listOf("M", "P", "RIP"),
                    cycleDays = 3
                )
            )
        )

        GeminiShiftMapper.registerExtractedData(ShiftRepository, data)
        val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_triade" }
        assertNotNull(rot)
        assertEquals(3, rot!!.totalDays)
        assertEquals(listOf("mattina", "pomeriggio", "riposo"), rot.daySequence)

        val ref = ShiftRepository.cycleReferenceDate
        assertEquals("mattina", ShiftRepository.getPlannedRotationShift(ref, rot.id)?.id)
        assertEquals("pomeriggio", ShiftRepository.getPlannedRotationShift(ref.plusDays(1), rot.id)?.id)
        assertEquals("riposo", ShiftRepository.getPlannedRotationShift(ref.plusDays(2), rot.id)?.id)
        assertEquals("mattina", ShiftRepository.getPlannedRotationShift(ref.plusDays(3), rot.id)?.id)

        // Negative days indexing check
        assertEquals("riposo", ShiftRepository.getPlannedRotationShift(ref.minusDays(1), rot.id)?.id)
        assertEquals("pomeriggio", ShiftRepository.getPlannedRotationShift(ref.minusDays(2), rot.id)?.id)
        assertEquals("mattina", ShiftRepository.getPlannedRotationShift(ref.minusDays(3), rot.id)?.id)
    }

    @Test
    fun testUnusualCycleLengths_21Days_3Weeks() {
        // 21 days: 3 weeks of standard patterns
        val seq21 = (1..3).flatMap { listOf("M", "P", "N", "SMO", "RIP", "RIP", "M") }
        assertEquals(21, seq21.size)

        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra 3 Settimane",
                    rotationDays = seq21,
                    cycleDays = 21
                )
            )
        )

        GeminiShiftMapper.registerExtractedData(ShiftRepository, data)
        val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_3_settimane" }
        assertNotNull(rot)
        assertEquals(21, rot!!.totalDays)
        assertEquals(21, rot.daySequence.size)

        // Day 0 -> M, Day 3 -> SMO, Day 4 -> RIP, Day 21 (wrap) -> Day 0 -> M
        val ref = ShiftRepository.cycleReferenceDate
        assertEquals("mattina", ShiftRepository.getPlannedRotationShift(ref, rot.id)?.id)
        assertEquals("smonto_notte", ShiftRepository.getPlannedRotationShift(ref.plusDays(3), rot.id)?.id)
        assertEquals("riposo", ShiftRepository.getPlannedRotationShift(ref.plusDays(4), rot.id)?.id)
        assertEquals("mattina", ShiftRepository.getPlannedRotationShift(ref.plusDays(21), rot.id)?.id)
    }

    @Test
    fun testUnusualCycleLengths_35Days_5Weeks() {
        // 35 days: 5 weeks cycle
        val seq35 = (1..5).flatMap { listOf("M", "M", "P", "P", "N", "SMO", "RIP") }
        assertEquals(35, seq35.size)

        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra 5 Settimane",
                    rotationDays = seq35,
                    cycleDays = 35
                )
            )
        )

        GeminiShiftMapper.registerExtractedData(ShiftRepository, data)
        val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_5_settimane" }
        assertNotNull(rot)
        assertEquals(35, rot!!.totalDays)
        assertEquals(35, rot.daySequence.size)

        val ref = ShiftRepository.cycleReferenceDate
        // Check day 34 (last day of cycle) and day 35 (wrap to day 0)
        assertEquals("riposo", ShiftRepository.getPlannedRotationShift(ref.plusDays(34), rot.id)?.id)
        assertEquals("mattina", ShiftRepository.getPlannedRotationShift(ref.plusDays(35), rot.id)?.id)
        assertEquals("mattina", ShiftRepository.getPlannedRotationShift(ref.plusDays(70), rot.id)?.id)
    }

    @Test
    fun testUnusualCycleLengths_42Days_6Weeks() {
        // 42 days: 6 weeks cycle
        val seq42 = (1..6).flatMap { listOf("M", "M", "P", "P", "N", "SMO", "RIP") }
        assertEquals(42, seq42.size)

        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra 6 Settimane",
                    rotationDays = seq42,
                    cycleDays = 42
                )
            )
        )

        GeminiShiftMapper.registerExtractedData(ShiftRepository, data)
        val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_6_settimane" }
        assertNotNull(rot)
        assertEquals(42, rot!!.totalDays)
        assertEquals(42, rot.daySequence.size)

        val ref = ShiftRepository.cycleReferenceDate
        assertEquals("mattina", ShiftRepository.getPlannedRotationShift(ref.plusDays(42 * 10), rot.id)?.id)
    }

    @Test
    fun testUnusualCycleLengths_EmptyCycleDoesNotCrash() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra Vuota",
                    rotationDays = emptyList(),
                    cycleDays = 0
                )
            )
        )

        GeminiShiftMapper.registerExtractedData(ShiftRepository, data)
        val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_vuota" }
        assertNotNull(rot)
        assertEquals(0, rot!!.totalDays)
        assertTrue(rot.daySequence.isEmpty())

        // Ensure getPlannedRotationShift safely returns null without divide by zero or index crash
        val planned = ShiftRepository.getPlannedRotationShift(LocalDate.now(), rot.id)
        assertNull("Empty rotation must safely return null planned shift", planned)
    }

    // =========================================================================
    // 2. UNKNOWN / UNMAPPED SHIFT CODES & GRACEFUL FALLBACK
    // =========================================================================

    @Test
    fun testUnmappedShiftCodes_CompletelyUnknownCodesFallbackGracefullyToRiposo() {
        val unknownCodes = listOf(
            "UNKNOWN_CODE_99",
            "FOOBAR",
            "XXX",
            "K99",
            "Z_VAL",
            "@#$%",
            "   ",
            ""
        )

        val data = GeminiShiftData(
            shiftDefinitions = emptyList(), // No definitions provided
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra Mistero",
                    rotationDays = unknownCodes,
                    cycleDays = unknownCodes.size
                )
            )
        )

        GeminiShiftMapper.registerExtractedData(ShiftRepository, data)
        val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_mistero" }
        assertNotNull(rot)
        assertEquals(unknownCodes.size, rot!!.daySequence.size)

        // Every unknown code should have resolved to a valid shift id in ShiftRepository (specifically "riposo")
        rot.daySequence.forEachIndexed { idx, defId ->
            assertEquals(
                "Unknown code at index $idx ('${unknownCodes[idx]}') must safely fallback to 'riposo'",
                "riposo",
                defId
            )
            assertNotNull(
                "Resolved ID must exist in ShiftRepository definitions",
                ShiftRepository.getShiftDefinition(defId)
            )
        }
    }

    @Test
    fun testUnmappedShiftCodes_CustomShiftInDefinitionsIsPreservedOverFallback() {
        // When Gemini outputs a custom definition (e.g. "K99"), it must resolve to the created custom definition UUID
        val data = GeminiShiftData(
            shiftDefinitions = listOf(
                GeminiShiftDefinition(
                    code = "K99",
                    name = "Turno Robotica K99",
                    startTime = "11:00",
                    endTime = "19:00",
                    colorHex = "#8B5CF6"
                )
            ),
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra Speciale",
                    rotationDays = listOf("M", "K99", "RIP")
                )
            )
        )

        GeminiShiftMapper.registerExtractedData(ShiftRepository, data)

        val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_speciale" }
        assertNotNull(rot)
        assertEquals(3, rot!!.daySequence.size)

        assertEquals("mattina", rot.daySequence[0])

        // The second day must resolve to the registered K99 shift definition ID
        val customShift = ShiftRepository.shiftDefinitions.find { it.shortCode == "K99" }
        assertNotNull("Custom shift K99 must be registered in repository", customShift)
        assertEquals(customShift!!.id, rot.daySequence[1])

        assertEquals("riposo", rot.daySequence[2])

        // Verify planned shift lookup returns the custom shift
        val plannedCustom = ShiftRepository.getPlannedRotationShift(
            ShiftRepository.cycleReferenceDate.plusDays(1),
            rot.id
        )
        assertNotNull(plannedCustom)
        assertEquals("Turno Robotica K99", plannedCustom!!.name)
        assertEquals("11:00", plannedCustom.startTime)
    }

    // =========================================================================
    // 3. REPETITIVE / IDEMPOTENT REGISTRATIONS & DRIFT PREVENTION
    // =========================================================================

    @Test
    fun testRepetitiveRegistrations_StrictIdempotencyAndNoDuplicateBloat() {
        val initialDefCount = ShiftRepository.shiftDefinitions.size
        val initialRotCount = ShiftRepository.rotations.size

        val data = GeminiShiftData(
            shiftDefinitions = listOf(
                GeminiShiftDefinition(code = "M", name = "Mattina", startTime = "06:15", endTime = "13:51"),
                GeminiShiftDefinition(code = "CUSTOM_A", name = "Turno Extra A", startTime = "15:00", endTime = "23:00")
            ),
            squads = listOf(
                GeminiSquadRotation(
                    squadName = "Squadra Idempotente",
                    rotationDays = listOf("M", "CUSTOM_A", "RIP"),
                    employees = listOf("Marco Graziani")
                )
            )
        )

        // Execute registration 25 times consecutively
        for (iteration in 1..25) {
            val result = GeminiShiftMapper.registerExtractedData(
                repository = ShiftRepository,
                data = data,
                targetUserName = "Marco Graziani"
            )

            assertEquals(2, result.registeredShifts.size)
            assertEquals(1, result.registeredRotations.size)
            assertEquals("Squadra Idempotente", result.matchedSquadName)
            assertEquals("rotazione_squadra_idempotente", result.defaultRotationId)

            // Definitions count should increase by exactly 1 ("CUSTOM_A") on the first iteration and never increase again
            assertEquals(
                "ShiftDefinitions size must remain strictly stable on iteration $iteration",
                initialDefCount + 1,
                ShiftRepository.shiftDefinitions.size
            )

            // Rotations count should increase by exactly 1 on first iteration and never increase again
            assertEquals(
                "Rotations size must remain strictly stable on iteration $iteration",
                initialRotCount + 1,
                ShiftRepository.rotations.size
            )
        }

        // Verify that custom shift ID did not change across repeated registrations
        val customDefs = ShiftRepository.shiftDefinitions.filter { it.shortCode == "CUSTOM_A" }
        assertEquals("Exactly one CUSTOM_A shift must exist", 1, customDefs.size)

        val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_idempotente" }
        assertNotNull(rot)
        assertEquals(customDefs.first().id, rot!!.daySequence[1])
    }

    // =========================================================================
    // 4. MULTI-THREADED CONCURRENCY STRESS
    // =========================================================================

    @Test
    fun testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt() {
        val numThreads = 10
        val operationsPerThread = 15
        val executor = Executors.newFixedThreadPool(numThreads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(numThreads)
        val failureCount = AtomicInteger(0)

        val testDatasets = (1..numThreads).map { threadIdx ->
            GeminiShiftData(
                shiftDefinitions = listOf(
                    GeminiShiftDefinition(
                        code = "TH_$threadIdx",
                        name = "Shift Thread $threadIdx",
                        startTime = "08:00",
                        endTime = "16:00"
                    )
                ),
                squads = listOf(
                    GeminiSquadRotation(
                        squadName = "Squadra Concurrency $threadIdx",
                        rotationDays = listOf("M", "TH_$threadIdx", "RIP"),
                        employees = listOf("Worker $threadIdx")
                    )
                )
            )
        }

        for (t in 0 until numThreads) {
            val data = testDatasets[t]
            val workerName = "Worker $t"
            executor.submit {
                try {
                    startLatch.await(5, TimeUnit.SECONDS)
                    for (op in 0 until operationsPerThread) {
                        // 1. Register data
                        val res = GeminiShiftMapper.registerExtractedData(
                            repository = ShiftRepository,
                            data = data,
                            targetUserName = workerName
                        )
                        assertNotNull(res)

                        // 2. Perform concurrent reads on repository safely
                        synchronized(ShiftRepository) {
                            val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_concurrency_$t" }
                            if (rot != null) {
                                val planned = ShiftRepository.getPlannedRotationShift(
                                    ShiftRepository.cycleReferenceDate.plusDays(op.toLong()),
                                    rot.id
                                )
                                assertNotNull(planned)
                                // read shift definitions without throwing
                                ShiftRepository.shiftDefinitions.toList()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    failureCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        // Release all threads simultaneously
        startLatch.countDown()
        val completed = doneLatch.await(20, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All concurrent threads must complete within timeout", completed)
        assertEquals("No exceptions or race conditions allowed during concurrent operations", 0, failureCount.get())

        // Verify that all 10 squads were successfully created without corruption
        for (t in 1..numThreads) {
            val rot = ShiftRepository.rotations.find { it.id == "rotazione_squadra_concurrency_$t" }
            assertNotNull("Rotation for thread $t must be present in repository", rot)
            val customDef = ShiftRepository.shiftDefinitions.find { it.shortCode == "TH_$t" }
            assertNotNull("Shift definition for thread $t must exist", customDef)
        }
    }

    // =========================================================================
    // 5. EMPLOYEE MATCHING EDGE CASES
    // =========================================================================

    @Test
    fun testNameMatchScoring_EdgeCasesAndPunctuation() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra A", listOf("M"), listOf("Marco Graziani")),
                GeminiSquadRotation("Squadra B", listOf("P"), listOf("Maria Grazia Rossi")),
                GeminiSquadRotation("Squadra C", listOf("N"), listOf("De Rossi, Mario-Antonio"))
            )
        )

        // Exact match
        assertEquals("Squadra A", GeminiShiftMapper.matchUserSquad(data, "Marco Graziani"))
        // Case insensitive and inverted
        assertEquals("Squadra A", GeminiShiftMapper.matchUserSquad(data, "graziani marco"))
        // Delimiters
        assertEquals("Squadra C", GeminiShiftMapper.matchUserSquad(data, "Mario Antonio De Rossi"))
        assertEquals("Squadra C", GeminiShiftMapper.matchUserSquad(data, "de rossi mario"))

        // Distinction between "Marco Graziani" and "Maria Grazia Rossi"
        assertEquals("Squadra B", GeminiShiftMapper.matchUserSquad(data, "Maria Grazia Rossi"))
        assertEquals("Squadra B", GeminiShiftMapper.matchUserSquad(data, "Rossi Maria Grazia"))

        // Query with null, empty, whitespace
        assertNull(GeminiShiftMapper.matchUserSquad(data, null))
        assertNull(GeminiShiftMapper.matchUserSquad(data, ""))
        assertNull(GeminiShiftMapper.matchUserSquad(data, "     "))

        // Non-existent worker
        assertNull(GeminiShiftMapper.matchUserSquad(data, "Giuseppe Verdi"))
    }
}
