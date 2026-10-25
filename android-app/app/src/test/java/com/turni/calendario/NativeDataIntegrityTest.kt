package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository as Repository
import com.turni.calendario.sync.DatabaseRestore
import com.turni.calendario.sync.GoogleDriveBackupHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class NativeDataIntegrityTest {
    private val day = LocalDate.of(2026, 10, 31)
    private lateinit var original: DatabaseSnapshot

    @Before fun clearCalendar() {
        original=DatabaseSnapshot.capture()
        Repository.calendarShifts.clear()
        Repository.dayExtraInfo.clear()
        Repository.reperibilitaConfig = ReperibilitaConfig(mode = ReperibilitaMode.MANUALE)
    }

    @org.junit.After fun restoreRepository() { DatabaseRestore.apply(original) }

    @Test fun repeatedReplacementsRememberRotationOriginalForAnyCategory() {
        Repository.applyRotation("turno_a", day, 1)
        Repository.replaceShift(day, "pomeriggio")
        Repository.replaceShift(day, "notte")
        assertEquals("mattina", Repository.getShiftsForDate(day).single().originalShiftDefId)
        Repository.replaceShift(day, "riposo")
        assertEquals("mattina", Repository.getShiftsForDate(day).single().originalShiftDefId)
    }

    @Test fun periodCanGoBackwardsAndRestoringOriginKeepsFutureProvenance() {
        Repository.applyRotation("turno_a", day.minusDays(1), 4)
        Repository.applyShiftRange("pomeriggio", day.plusDays(1), day.minusDays(1))
        assertEquals("pomeriggio", Repository.getShiftsForDate(day.minusDays(1)).first().shiftDefId)
        assertEquals("pomeriggio", Repository.getShiftsForDate(day.plusDays(1)).first().shiftDefId)
        Repository.applyShiftRange("mattina", day, day)
        assertTrue(Repository.getShiftsForDate(day).single().isFromRotation)
        Repository.replaceShift(day, "notte")
        assertEquals("mattina", Repository.getShiftsForDate(day).single().originalShiftDefId)
    }

    @Test fun selectiveEraseKeepsOtherShiftDetailsAndDayOnCall() {
        Repository.applyRotation("turno_a", day, 1)
        Repository.addSecondaryShift(day, "notte")
        val shifts = Repository.getShiftsForDate(day).toList()
        Repository.setShiftExtraDetails(day, shifts[0].id, "14:06", .5f, "Primary", true)
        Repository.setShiftExtraDetails(day, shifts[1].id, "06:15", .25f, "Extra", true)
        Repository.setDayOnCallManual(day, true)
        Repository.removeAssignedShift(day, shifts[0].id)
        val remaining = Repository.getShiftsForDate(day).single()
        assertEquals("Extra", remaining.notes)
        assertEquals(.25f, remaining.overtimeHours, .0001f)
        assertEquals(.25f, Repository.getDayExtraInfo(day).overtimeHours, .0001f)
        assertTrue(Repository.isOnCall(day))
        Repository.replaceShift(day, "pomeriggio")
        assertNull(Repository.getShiftsForDate(day).single().originalShiftDefId)
    }

    @Test fun erasingShiftDoesNotEraseDayNotesOrOnCallAndUndoRestoresEverything() {
        Repository.applyRotation("turno_a", day, 1)
        val assigned = Repository.getShiftsForDate(day).single()
        Repository.setShiftExtraDetails(day, assigned.id, "14:06", .5f, "Shift note", true)
        Repository.setDayOnCallManual(day, true)
        Repository.setDayNotes(day, "Day note")
        Repository.clearDayShifts(day)
        assertTrue(Repository.isOnCall(day))
        assertEquals("Day note", Repository.getDayExtraInfo(day).notes)
        assertEquals(0f, Repository.getDayExtraInfo(day).overtimeHours)
        assertTrue(Repository.undo())
        assertEquals(assigned.id, Repository.getShiftsForDate(day).single().id)
        assertEquals(.5f, Repository.getDayExtraInfo(day).overtimeHours, .0001f)
        assertFalse(Repository.isRotationCleared(day))
    }

    @Test fun erasingRangeIsOneUndoAndClearsProvenance() {
        Repository.applyRotation("turno_a", day, 3)
        Repository.applyShiftRange("eraser", day.plusDays(2), day)
        assertTrue(Repository.getShiftsForDate(day).isEmpty())
        assertTrue(Repository.undo())
        assertEquals(1, Repository.getShiftsForDate(day).size)
        assertEquals(1, Repository.getShiftsForDate(day.plusDays(2)).size)
    }

    @Test fun reportUsesAssignmentOvertimeWithoutDoubleCountingLegacyAggregate() {
        Repository.assignShift(day, "mattina")
        Repository.addSecondaryShift(day, "notte")
        val shifts = Repository.getShiftsForDate(day).toList()
        Repository.calendarShifts[day] = shifts.mapIndexed { index, shift -> shift.copy(isOvertime = true, overtimeHours = if(index == 0) .5f else .25f) }.toMutableList()
        Repository.setDayOvertime(day, 9f)
        assertEquals(.75f, Repository.getComprehensiveReportStats(day, day).totalOvertimeHours, .0001f)
    }

    @Test fun actualEndTimeCalculatesExactMinutesAcrossMidnightAndRejectsInvalidTimes() {
        assertEquals(15f / 60f, Repository.calculateOvertimeFromEndTime("23:50", "00:05"), .0001f)
        assertEquals(1f / 60f, Repository.calculateOvertimeFromEndTime("13:36", "13:37"), .0001f)
        assertEquals(0f, Repository.calculateOvertimeFromEndTime("13:36", "25:00"))
        assertEquals(0f, Repository.calculateOvertimeFromEndTime("13:36", "13:00"))
    }

    @Test fun rotationReplacementRemovesStaleOvertimeAndKeepsDayOnCall() {
        Repository.assignShift(day, "mattina")
        Repository.setShiftOvertime(day, Repository.getShiftsForDate(day).single().id, 2f)
        Repository.setDayOnCallManual(day, true)
        Repository.applyRotation("turno_a", day, 1)
        assertEquals(0f, Repository.getComprehensiveReportStats(day, day).totalOvertimeHours)
        assertTrue(Repository.isOnCall(day))
    }

    @Test fun fullBackupRoundTripPreservesEveryRelevantField() {
        val baseline = DatabaseSnapshot.capture()
        val profile = WorkProfile("custom", "Personal", iconKey = "groups", ccnlCode = "CCNL_SANITA", ccnlName = "Custom CCNL", ccnlDocumentName = "rules.pdf", ccnlDocumentUri = "content://rules")
        val def = baseline.definitions.first().copy(workProfileId = "custom", hourlyRate = 17.5, allowance = 3.5, breakStartTime = "09:00", iconKey = "moon")
        val assigned = AssignedShift("test-id", day, def.id, originalShiftDefId = "notte", isModifiedFromRotation = true,
            customStartTime = "07:00", customEndTime = "14:00", actualEndTime = "14:15", notes = "Note", tags = listOf("x"), isOvertime = true,
            overtimeHours = .25f, isTrasferta = true, isOnCallManual = false, hideExShift = true)
        val state = baseline.copy(definitions = listOf(def), profiles = listOf(profile), activeWorkProfileId = profile.id,
            shifts = mapOf(day to listOf(assigned)), extras = mapOf(day to DayExtraInfo(day, .25f, true, true, rotationCleared = true, notes = "Day", actualEndTime = "14:15")),
            defaultRotationId = "", alarms = SmartAlarmsConfig(false, listOf(ShiftAlarmRule(def.id, false, 35, "05:00")), setOf("disabled")),
            onCall = ReperibilitaConfig(ReperibilitaMode.MANUALE, listOf(def.id), manualDates = setOf(day)), holidayIds = emptySet())
        val parsed = DatabaseRestore.parse(GoogleDriveBackupHelper.exportDatabaseToJson(state), baseline)
        assertEquals(state, parsed)
    }

    @Test fun invalidBackupCannotPartiallyChangeRepository() {
        Repository.assignShift(day, "mattina")
        val before = DatabaseSnapshot.capture()
        val result = GoogleDriveBackupHelper.restoreFromJsonString("""{"version":3,"shiftDefinitions":[{"id":"new","name":"New","shortCode":"NEW"}],"calendarShifts":[{"date":"bad-date","shiftDefId":"new"}]}""")
        assertFalse(result.success)
        assertEquals(before, DatabaseSnapshot.capture())
    }

    @Test fun oldVersionTwoBackupKeepsOmittedConfigurationAndParsesExtraFields() {
        val baseline = DatabaseSnapshot.capture()
        val parsed = DatabaseRestore.parse("""{"version":2,"calendarShifts":[],"dayExtraInfo":[],"workProfiles":[{"id":"old","name":"Legacy","colorHex":4281554286}],"activeWorkProfileId":"old"}""", baseline)
        assertEquals("old", parsed.activeWorkProfileId)
        assertEquals(baseline.onCall, parsed.onCall)
        assertEquals(baseline.alarms, parsed.alarms)
        assertEquals("work", parsed.profiles.single().iconKey)
        assertTrue(parsed.shifts.isEmpty())
    }

    @Test fun rotationEmptySlotClearsAssignmentWithoutTouchingDayState() {
        val original = Repository.rotations.toList()
        try {
            Repository.saveRotation(ShiftRotation("empty-test", "Test", 2, listOf("mattina", "")))
            Repository.setDayOnCallManual(day.plusDays(1), true)
            Repository.setDayNotes(day.plusDays(1), "Keep")
            Repository.applyRotation("empty-test", day, 2)
            assertEquals("mattina", Repository.getShiftsForDate(day).single().shiftDefId)
            assertTrue(Repository.getShiftsForDate(day.plusDays(1)).isEmpty())
            assertTrue(Repository.isOnCall(day.plusDays(1)))
            assertEquals("Keep", Repository.getDayExtraInfo(day.plusDays(1)).notes)
        } finally {
            Repository.rotations.clear(); Repository.rotations.addAll(original)
        }
    }

    @Test fun legacyDayOvertimeAndTravelMigrateToPrimaryExactlyOnce() {
        val baseline = DatabaseSnapshot.capture()
        val legacy = """{"version":2,"calendarShifts":[{"id":"a","date":"2026-10-31","shiftDefId":"mattina"},{"id":"b","date":"2026-10-31","shiftDefId":"notte"}],"dayExtraInfo":[{"date":"2026-10-31","overtimeHours":1.5,"isTrasferta":true,"isOnCallManual":true}]}"""
        val migrated = DatabaseRestore.parse(legacy, baseline)
        assertEquals(1.5f, migrated.shifts.getValue(day)[0].overtimeHours, .0001f)
        assertTrue(migrated.shifts.getValue(day)[0].isTrasferta)
        assertEquals(0f, migrated.shifts.getValue(day)[1].overtimeHours)
        assertTrue(migrated.extras.getValue(day).isOnCallManual == true)
        val roundTrip = DatabaseRestore.parse(GoogleDriveBackupHelper.exportDatabaseToJson(migrated), baseline)
        assertEquals(migrated, roundTrip)
    }

    @Test fun editingOneWorkNeverErasesOtherWorkAssignmentsOrTheirDetails() {
        val definitions = Repository.shiftDefinitions.toList()
        try {
            Repository.saveShiftDefinition(ShiftDefinition("other-test", "Other", "OTH", workProfileId = "second"))
            Repository.assignShift(day, "other-test")
            val other = Repository.getShiftsForDate(day).single()
            Repository.setShiftExtraDetails(day, other.id, "14:00", .4f, "Other work", true)
            val preserved = Repository.getShiftsForDate(day).single()
            Repository.replaceShift(day, "mattina")
            Repository.replaceShift(day, "notte")
            assertEquals(preserved, Repository.getShiftsForDate(day).first { it.id == other.id })
            Repository.applyShiftRange("pomeriggio", day.plusDays(1), day)
            assertEquals(preserved, Repository.getShiftsForDate(day).first { it.id == other.id })
            Repository.clearWorkDayShifts(day, "principale")
            assertEquals(listOf(preserved), Repository.getShiftsForDate(day))
            assertEquals(.4f, Repository.getDayExtraInfo(day).overtimeHours, .0001f)
        } finally {
            Repository.shiftDefinitions.clear(); Repository.shiftDefinitions.addAll(definitions)
        }
    }

    @Test fun rotationAndEraserPreserveOtherWorkRotationLineage() {
        val definitions = Repository.shiftDefinitions.toList()
        val rotations = Repository.rotations.toList()
        try {
            Repository.saveShiftDefinition(ShiftDefinition("other-test", "Other", "OTH", workProfileId = "second"))
            Repository.saveRotation(ShiftRotation("other-cycle", "Other", 1, listOf("other-test")))
            Repository.applyRotation("turno_a", day, 2, "principale")
            val primary = Repository.getShiftsForDate(day).single()
            Repository.applyRotation("other-cycle", day, 2, "second")
            assertEquals(primary, Repository.getShiftsForDate(day).first { it.id == primary.id })
            Repository.clearWorkDayShifts(day, "second")
            Repository.replaceShift(day, "notte")
            assertEquals("mattina", Repository.getShiftsForDate(day).single().originalShiftDefId)
        } finally {
            Repository.shiftDefinitions.clear(); Repository.shiftDefinitions.addAll(definitions)
            Repository.rotations.clear(); Repository.rotations.addAll(rotations)
        }
    }

    @Test fun deletingWorkMigratesItsModelsWithoutLosingAssignedData() {
        val before = DatabaseSnapshot.capture()
        try {
            Repository.addWorkProfile(WorkProfile("delete-job", "Other", isDefault = false))
            Repository.saveShiftDefinition(ShiftDefinition("keep-model", "Keep", "KEEP", workProfileId = "delete-job"))
            Repository.assignShift(day, "keep-model", notes = "Do not lose")
            val assigned = Repository.getShiftsForDate(day).single()
            assertTrue(Repository.deleteWorkProfile("delete-job"))
            assertEquals(assigned, Repository.getShiftsForDate(day).single())
            assertEquals(Repository.activeWorkProfileId, Repository.getShiftDefinition("keep-model")!!.workProfileId)
        } finally { DatabaseRestore.apply(before) }
    }

    @Test fun deletingModelCleansReferencesAndCannotBeUndoneIntoDanglingData() {
        val before = DatabaseSnapshot.capture()
        try {
            Repository.saveShiftDefinition(ShiftDefinition("delete-model", "Delete", "DEL"))
            Repository.saveRotation(ShiftRotation("delete-cycle", "Cycle", 2, listOf("delete-model", "mattina")))
            Repository.reperibilitaConfig = ReperibilitaConfig(shiftSpecificIds = listOf("delete-model"))
            Repository.assignShift(day, "delete-model")
            Repository.addSecondaryShift(day, "mattina")
            Repository.deleteShiftDefinition("delete-model")
            assertEquals("mattina", Repository.getShiftsForDate(day).single().shiftDefId)
            assertEquals("", Repository.rotations.first { it.id == "delete-cycle" }.daySequence.first())
            assertFalse("Deleted definitions must not return via undo", Repository.canUndo())
            assertFalse(Repository.reperibilitaConfig.shiftSpecificIds.contains("delete-model"))
        } finally { DatabaseRestore.apply(before) }
    }
}
