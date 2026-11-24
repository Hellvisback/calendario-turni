package com.turni.calendario

import com.turni.calendario.export.PdfGenerator
import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DatabaseRestore
import com.turni.calendario.sync.GoogleDriveBackupHelper
import com.turni.calendario.ui.screens.reportMetrics
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ConsecutiveShiftAndPermissionTest {
    private val baseDate = LocalDate.of(2026, 11, 2)
    private val profile = WorkProfile(id = "job", name = "Principale", minRestHoursBetweenShifts = 11, maxConsecutiveWorkDays = 6)

    private fun workDef(
        id: String = "work",
        profileId: String = profile.id,
        start: String = "06:00",
        end: String = "14:00",
        isAllDay: Boolean = false,
    ) = ShiftDefinition(
        id = id,
        name = id,
        shortCode = id.take(3).uppercase(),
        startTime = start,
        endTime = end,
        isAllDay = isAllDay,
        category = ShiftCategory.LAVORO,
        workProfileId = profileId,
    )

    private fun nonWorkDef(id: String, category: ShiftCategory, profileId: String = profile.id) = ShiftDefinition(
        id = id,
        name = id,
        shortCode = id.take(3).uppercase(),
        isAllDay = true,
        category = category,
        workProfileId = profileId,
    )

    private fun assign(dayOffset: Long, defId: String = "work", id: String = "s-$dayOffset-$defId", permissionHours: Float = 0f) =
        AssignedShift(id = id, date = baseDate.plusDays(dayOffset), shiftDefId = defId, permissionHours = permissionHours)

    @Test
    fun consecutiveWorkStreakEmitsWarningsOnlyAboveLimit() {
        val defs = listOf(workDef())
        val sixDays = (0L..5L).map { assign(it) }
        assertTrue(consecutiveShiftWarnings(sixDays, defs, listOf(profile)).isEmpty())

        val eightDaysReversed = (7L downTo 0L).map { assign(it) }
        val warnings = consecutiveShiftWarnings(eightDaysReversed, defs, listOf(profile))
        assertEquals(2, warnings.size)
        assertEquals(ConsecutiveShiftWarning(baseDate.plusDays(6), 7, 6, profile.id), warnings[0])
        assertEquals("7 turni lavorativi di fila · limite 6", warnings[0].description)
        assertEquals(ConsecutiveShiftWarning(baseDate.plusDays(7), 8, 6, profile.id), warnings[1])
    }

    @Test
    fun streakResetsOnCalendarGapRiposoAssenzaAndAltro() {
        val defs = listOf(
            workDef(),
            nonWorkDef("smonto_notte", ShiftCategory.RIPOSO),
            nonWorkDef("ferie", ShiftCategory.ASSENZA),
            nonWorkDef("corso_altro", ShiftCategory.ALTRO),
        )

        // Gap on day 4 breaks 4 + 4 streak
        val withGap = ((0L..3L) + (5L..8L)).map { assign(it) }
        assertTrue(consecutiveShiftWarnings(withGap, defs, listOf(profile)).isEmpty())

        // RIPOSO (smonto) on day 4 resets streak
        val withSmonto = (0L..3L).map { assign(it) } + assign(4L, "smonto_notte") + (5L..8L).map { assign(it) }
        assertTrue(consecutiveShiftWarnings(withSmonto, defs, listOf(profile)).isEmpty())

        // ASSENZA (ferie) on day 4 resets streak
        val withFerie = (0L..3L).map { assign(it) } + assign(4L, "ferie") + (5L..8L).map { assign(it) }
        assertTrue(consecutiveShiftWarnings(withFerie, defs, listOf(profile)).isEmpty())

        // ALTRO on day 4 resets streak
        val withAltro = (0L..3L).map { assign(it) } + assign(4L, "corso_altro") + (5L..8L).map { assign(it) }
        assertTrue(consecutiveShiftWarnings(withAltro, defs, listOf(profile)).isEmpty())
    }

    @Test
    fun multiShiftSameDayCountsOnceAndProfilesStayIndependent() {
        val otherProfile = profile.copy(id = "other", maxConsecutiveWorkDays = 3)
        val defs = listOf(
            workDef("w1", profileId = profile.id),
            workDef("w2", profileId = profile.id, start = "14:00", end = "22:00"),
            nonWorkDef("smonto", ShiftCategory.RIPOSO, profileId = profile.id),
            workDef("other_w", profileId = otherProfile.id),
        )
        // 6 calendar days with 2 shifts per day on job + mixed smonto on day 3 -> still 6 days, no warning on job
        val jobShifts = (0L..5L).flatMap { d ->
            listOf(assign(d, "w1", "a-$d"), assign(d, if (d == 3L) "smonto" else "w2", "b-$d"))
        }
        // 4 consecutive days on otherProfile (limit 3) -> 1 warning only on otherProfile
        val otherShifts = (0L..3L).map { assign(it, "other_w", "o-$it") }

        val warnings = consecutiveShiftWarnings(jobShifts + otherShifts, defs, listOf(profile, otherProfile))
        assertEquals(1, warnings.size)
        assertEquals(otherProfile.id, warnings.single().workProfileId)
        assertEquals(4, warnings.single().consecutiveDays)
        assertEquals(baseDate.plusDays(3), warnings.single().date)
    }

    @Test
    fun zeroMaxConsecutiveWorkDaysDisablesWarnings() {
        val defs = listOf(workDef())
        val disabledProfile = profile.copy(maxConsecutiveWorkDays = 0)
        val tenDays = (0L..9L).map { assign(it) }
        assertTrue(consecutiveShiftWarnings(tenDays, defs, listOf(disabledProfile)).isEmpty())
    }

    @Test
    fun minimumRestAdjustsEndWithPermissionHoursAndClampsToStart() {
        val defs = listOf(
            workDef("day1", start = "06:00", end = "20:00"),
            workDef("day2", start = "06:00", end = "14:00"),
        )
        // Without permission: 20:00 -> 06:00 next day = 10h rest < 11h required
        val unadjusted = minimumRestWarnings(
            listOf(assign(0, "day1"), assign(1, "day2")),
            defs,
            listOf(profile),
        )
        assertEquals(600L, unadjusted.single().restMinutes)

        // With 1.5h permission and no actualEndTime: end moves to 18:30 -> 11.5h rest >= 11h
        val withPermission = minimumRestWarnings(
            listOf(assign(0, "day1", permissionHours = 1.5f), assign(1, "day2")),
            defs,
            listOf(profile),
        )
        assertTrue(withPermission.isEmpty())

        // With actualEndTime explicitly set, permissionHours does not alter actualEndTime
        val withActualEnd = minimumRestWarnings(
            listOf(assign(0, "day1", permissionHours = 4f).copy(actualEndTime = "20:00"), assign(1, "day2")),
            defs,
            listOf(profile),
        )
        assertEquals(600L, withActualEnd.single().restMinutes)

        // Excessive permission exceeding shift length clamps candidate end to shift start (06:00) -> 24h rest before next 06:00
        val sameDayNext = workDef("same_day", start = "14:00", end = "22:00")
        val clampedToStart = minimumRestWarnings(
            listOf(assign(0, "day1", permissionHours = 30f), assign(0, "same_day")),
            defs + sameDayNext,
            listOf(profile),
        )
        assertEquals(480L, clampedToStart.single().restMinutes)
    }

    @Test
    fun reportMetricsDeductsClampedPermissionAndAddsOvertime() {
        val work = workDef("m", start = "06:00", end = "14:00")
        val allDayWork = workDef("all", isAllDay = true)
        val metrics = reportMetrics(
            assignments = listOf(
                AssignedShift("1", baseDate, work.id, permissionHours = 2f, overtimeHours = 0.5f),
                AssignedShift("2", baseDate.plusDays(1), work.id, permissionHours = 12f),
                AssignedShift("3", baseDate.plusDays(2), allDayWork.id, permissionHours = 3f),
            ),
            definitions = listOf(work, allDayWork),
        )
        val workMetric = metrics.first { it.definition.id == work.id }
        val allDayMetric = metrics.first { it.definition.id == allDayWork.id }
        assertEquals(6.5f, workMetric.hours, 0.001f)
        assertEquals(0f, allDayMetric.hours, 0.001f)
    }

    @Test
    fun repositoryStatsExtraDetailsAndConsecutiveAlertsWorkEndToEnd() {
        val before = DatabaseSnapshot.capture()
        try {
            seedLegacyDemoRepository()
            ShiftRepository.calendarShifts.clear()
            ShiftRepository.dayExtraInfo.clear()
            ShiftRepository.statisticsRules.clear()
            val customProfile = WorkProfile("principale", "Test", maxConsecutiveWorkDays = 3)
            ShiftRepository.workProfiles.clear()
            ShiftRepository.workProfiles.add(customProfile)

            // 4 consecutive morning shifts (06:00-13:36 = 7.6h each)
            for (d in 0L..3L) {
                ShiftRepository.assignShift(baseDate.plusDays(d), "mattina")
            }
            assertEquals(1, ShiftRepository.consecutiveShiftAlerts.size)
            assertEquals(4, ShiftRepository.consecutiveShiftAlerts.single().consecutiveDays)

            val firstShift = ShiftRepository.getShiftsForDate(baseDate).single()
            ShiftRepository.setShiftExtraDetails(
                date = baseDate,
                assignedShiftId = firstShift.id,
                actualEndTime = null,
                overtimeHours = 1.5f,
                notes = "Permesso parziale",
                isTrasferta = false,
                permissionHours = 2f,
            )
            // Calling setShiftExtraDetails with permissionHours = null and setShiftOvertime preserves permissionHours
            ShiftRepository.setShiftExtraDetails(
                date = baseDate,
                assignedShiftId = firstShift.id,
                actualEndTime = null,
                overtimeHours = 1.5f,
                notes = "Nota aggiornata",
                permissionHours = null,
            )
            ShiftRepository.setShiftOvertime(baseDate, firstShift.id, 1f)
            assertEquals(2f, ShiftRepository.getShiftsForDate(baseDate).single().permissionHours, 0.001f)

            ShiftRepository.setShiftActualEndTime(baseDate, firstShift.id, "14:06", 0.5f)
            assertEquals(2f, ShiftRepository.getShiftsForDate(baseDate).single().permissionHours, 0.001f)

            // Excessive permission on day 1 clamps to baseHours (7.6f)
            val secondShift = ShiftRepository.getShiftsForDate(baseDate.plusDays(1)).single()
            ShiftRepository.setShiftExtraDetails(
                date = baseDate.plusDays(1),
                assignedShiftId = secondShift.id,
                actualEndTime = null,
                overtimeHours = 0f,
                notes = "",
                permissionHours = 20f,
            )

            // All-day LAVORO shift ignores permissionHours in stats
            val allDayWork = workDef("all_day_work", profileId = "principale", isAllDay = true)
            ShiftRepository.saveShiftDefinition(allDayWork)
            ShiftRepository.assignShift(baseDate.plusDays(4), allDayWork.id)
            val allDayAssigned = ShiftRepository.getShiftsForDate(baseDate.plusDays(4)).single()
            ShiftRepository.setShiftExtraDetails(
                date = baseDate.plusDays(4),
                assignedShiftId = allDayAssigned.id,
                actualEndTime = null,
                overtimeHours = 0f,
                notes = "",
                permissionHours = 4f,
            )

            val stats = ShiftRepository.getComprehensiveReportStats(baseDate, baseDate.plusDays(4))
            // Day 0: (7.6 - 2.0) + 0.5 overtime = 6.1; Day 1: (7.6 - 7.6) = 0; Day 2: 7.6; Day 3: 7.6; Day 4: 0
            assertEquals(6.1f + 7.6f + 7.6f, stats.totalHours, 0.01f)
            assertEquals(2f + 7.6f, stats.totalPermissionHours, 0.01f)

            // Verify PDF export includes permission line when > 0f
            val pdfDay0 = PdfGenerator.capture(PdfExportOptions(startDate = baseDate, endDate = baseDate)).days.single()
            assertTrue(pdfDay0.shifts.single().details.any { it.contains("Permesso:") && it.contains("2 h 0 min") })
        } finally {
            DatabaseRestore.apply(before)
        }
    }

    @Test
    fun backupRoundTripAndLegacyDefaultsPreservePermissionAndMaxConsecutiveDays() {
        val prev = DatabaseSnapshot.capture()
        try {
            seedLegacyDemoRepository()
            val baseline = DatabaseSnapshot.capture()
            val work = WorkProfile("job-perm", "Lavoro", maxConsecutiveWorkDays = 5)
            val def = baseline.definitions.first().copy(workProfileId = work.id)
            val models = baseline.definitions.map { if (it.id == def.id) def else it.copy(workProfileId = work.id) }
            val assigned = AssignedShift("perm-shift", baseDate, def.id, permissionHours = 1.75f)
            val expected = baseline.copy(
                definitions = models,
                rotations = emptyList(),
                profiles = listOf(work),
                activeWorkProfileId = work.id,
                activeRotationId = "",
                defaultRotationId = "",
                shifts = mapOf(baseDate to listOf(assigned)),
                extras = emptyMap(),
                extrasByProfile = emptyMap(),
                onCallByProfile = emptyMap(),
                alarmsByProfile = emptyMap(),
                activeRotationIdByProfile = emptyMap(),
                defaultRotationIdByProfile = emptyMap(),
            )

            val json = GoogleDriveBackupHelper.exportDatabaseToJson(expected)
            val parsed = DatabaseRestore.parse(json, baseline)
            assertEquals(expected, parsed)
            assertEquals(1.75f, parsed.shifts.getValue(baseDate).single().permissionHours, 0.001f)
            assertEquals(5, parsed.profiles.single().maxConsecutiveWorkDays)

            // Legacy backup without permissionHours or maxConsecutiveWorkDays defaults seamlessly
            val legacyRoot = JSONObject(json)
            legacyRoot.getJSONArray("calendarShifts").getJSONObject(0).remove("permissionHours")
            legacyRoot.getJSONArray("workProfiles").getJSONObject(0).remove("maxConsecutiveWorkDays")
            val legacyParsed = DatabaseRestore.parse(legacyRoot.toString(), baseline)
            assertEquals(0f, legacyParsed.shifts.getValue(baseDate).single().permissionHours, 0.001f)
            assertEquals(6, legacyParsed.profiles.single().maxConsecutiveWorkDays)

            // Out-of-range maxConsecutiveWorkDays is clamped to 0..31; negative permissionHours is rejected
            val clampedRoot = JSONObject(json)
            clampedRoot.getJSONArray("workProfiles").getJSONObject(0).put("maxConsecutiveWorkDays", 99)
            assertEquals(31, DatabaseRestore.parse(clampedRoot.toString(), baseline).profiles.single().maxConsecutiveWorkDays)

            val invalidRoot = JSONObject(json)
            invalidRoot.getJSONArray("calendarShifts").getJSONObject(0).put("permissionHours", -1.0)
            assertThrows(IllegalArgumentException::class.java) {
                DatabaseRestore.parse(invalidRoot.toString(), baseline)
            }
        } finally {
            DatabaseRestore.apply(prev)
        }
    }
}
