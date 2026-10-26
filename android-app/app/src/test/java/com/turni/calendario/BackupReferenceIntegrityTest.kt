package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DatabaseRestore
import com.turni.calendario.sync.GoogleDriveBackupHelper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class BackupReferenceIntegrityTest {
    private val date = LocalDate.of(2026, 10, 25)
    private lateinit var original: DatabaseSnapshot
    private lateinit var base: DatabaseSnapshot

    @Before fun captureRepository() {
        original = DatabaseSnapshot.capture()
        val model = ShiftDefinition("backup-model", "Turno", "TUR", workProfileId = "backup-work")
        base = original.copy(
            definitions = listOf(model),
            profiles = listOf(WorkProfile(id = "backup-work", name = "Lavoro")),
            rotations = listOf(ShiftRotation("backup-cycle", "Ciclo", 2, listOf(model.id, ""))),
            shifts = mapOf(date to listOf(AssignedShift("backup-assignment", date, model.id))),
            extras = emptyMap(), activeWorkProfileId = "backup-work", activeRotationId = "backup-cycle",
            defaultRotationId = "backup-cycle", onCall = ReperibilitaConfig(), alarms = SmartAlarmsConfig()
        )
    }

    @After fun restoreRepository() { DatabaseRestore.apply(original) }

    private fun document() = JSONObject(GoogleDriveBackupHelper.exportDatabaseToJson(base))

    private fun assertRejectedWithoutMutation(json: JSONObject) {
        val before = DatabaseSnapshot.capture()
        try {
            DatabaseRestore.parse(json.toString(), base)
            fail("Invalid references must be rejected before a restore can be offered")
        } catch (_: IllegalArgumentException) {
            assertEquals(before, DatabaseSnapshot.capture())
        }
    }

    @Test fun missingAssignedModelIsRejectedWithoutChangingLiveState() {
        val json = document()
        json.getJSONArray("calendarShifts").getJSONObject(0).put("shiftDefId", "missing")
        assertRejectedWithoutMutation(json)
    }

    @Test fun missingWorkProfileIsRejectedWithoutChangingLiveState() {
        val json = document()
        json.getJSONArray("shiftDefinitions").getJSONObject(0).put("workProfileId", "missing")
        assertRejectedWithoutMutation(json)
    }

    @Test fun blankAndDuplicateWorkIdsAreRejected() {
        val blank = document()
        blank.getJSONArray("workProfiles").getJSONObject(0).put("id", "")
        assertRejectedWithoutMutation(blank)
        val duplicate = document()
        duplicate.getJSONArray("workProfiles").put(JSONObject(duplicate.getJSONArray("workProfiles").getJSONObject(0).toString()))
        assertRejectedWithoutMutation(duplicate)
    }

    @Test fun missingOriginalModelIsRejected() {
        val json = document()
        json.getJSONArray("calendarShifts").getJSONObject(0).put("originalShiftDefId", "missing")
        assertRejectedWithoutMutation(json)
    }

    @Test fun missingRotationModelIsRejectedButEmptySlotsAreValid() {
        val json = document()
        json.getJSONArray("rotations").getJSONObject(0).getJSONArray("daySequence").put(0, "missing")
        assertRejectedWithoutMutation(json)
        val valid = DatabaseRestore.parse(document().toString(), base)
        assertEquals(listOf("backup-model", ""), valid.rotations.single().daySequence)
    }

    @Test fun incompleteLegacyBackupCannotEraseModelsStillUsedByFallbackAssignments() {
        assertRejectedWithoutMutation(JSONObject().put("version", 1).put("shiftDefinitions", JSONArray()))
    }

    @Test fun compatibleLegacyPartialBackupRetainsDefinedModelsAndWorkProfiles() {
        val partial = JSONObject().put("version", 1).put("calendarShifts", document().getJSONArray("calendarShifts"))
        val parsed = DatabaseRestore.parse(partial.toString(), base)
        assertEquals(base.definitions, parsed.definitions)
        assertEquals(base.profiles, parsed.profiles)
        assertEquals(base.shifts, parsed.shifts)
    }

    @Test fun directApplyRejectsOrphansBeforeAnyMutationOrUndoClear() {
        ShiftRepository.saveSnapshot()
        val before = DatabaseSnapshot.capture()
        try {
            DatabaseRestore.apply(base.copy(definitions = emptyList()))
            fail("Direct restore must also validate references")
        } catch (_: IllegalArgumentException) {
            assertEquals(before, DatabaseSnapshot.capture())
            assertTrue(ShiftRepository.canUndo())
        }
    }

    @Test fun fullRestoreClearsCalendarOnlyUndoHistory() {
        DatabaseRestore.apply(base)
        ShiftRepository.saveSnapshot()
        assertTrue(ShiftRepository.canUndo())
        val newModel = base.definitions.single().copy(id = "restored-model")
        val restored = base.copy(definitions = listOf(newModel),
            rotations = listOf(base.rotations.single().copy(daySequence = listOf(newModel.id, ""))),
            shifts = mapOf(date to listOf(AssignedShift("restored-assignment", date, newModel.id))))
        DatabaseRestore.apply(restored)
        assertFalse(ShiftRepository.canUndo())
        assertFalse(ShiftRepository.undo())
        assertEquals(restored, DatabaseSnapshot.capture())
    }

    @Test fun restoreHelperDoesNotRetainAnUndoEntryWithOldModelIds() {
        DatabaseRestore.apply(base)
        ShiftRepository.saveSnapshot()
        val result = DatabaseRestore.restore(document().toString())
        assertTrue(result.success)
        assertFalse(ShiftRepository.canUndo())
        assertFalse(ShiftRepository.undo())
    }
}
