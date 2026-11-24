package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DatabaseRestore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class WorkProfileIsolationTest {
    private lateinit var initialSnapshot: DatabaseSnapshot

    @Before
    fun setup() {
        initialSnapshot = DatabaseSnapshot.capture()
        DatabaseRestore.apply(
            DatabaseSnapshot(
                definitions = emptyList(),
                rotations = emptyList(),
                shifts = emptyMap(),
                extras = emptyMap(),
                profiles = listOf(
                    WorkProfile("principale", "Il mio lavoro"),
                    WorkProfile("secondario", "Secondo Lavoro", isDefault = false)
                ),
                activeWorkProfileId = "principale",
                activeRotationId = "",
                defaultRotationId = "",
                preferences = AppPreferences(autoBackupDaily = false),
                onCall = ReperibilitaConfig(),
                alarms = SmartAlarmsConfig(),
                holidayIds = emptySet()
            )
        )
    }

    @After
    fun tearDown() {
        DatabaseRestore.apply(initialSnapshot)
    }

    @Test
    fun onCallAlarmsTagsExtrasAndRotationsAreIsolatedPerProfile() {
        val date = LocalDate.of(2026, 11, 10)
        ShiftRepository.setActiveWorkProfile("principale")
        ShiftRepository.reperibilitaConfig = ReperibilitaConfig(
            mode = ReperibilitaMode.MANUALE,
            manualDates = setOf(date)
        )
        ShiftRepository.smartAlarmsConfig = SmartAlarmsConfig(
            isGlobalEnabled = true,
            rules = listOf(ShiftAlarmRule("m1", isEnabled = true, minutesBefore = 45))
        )
        ShiftRepository.saveDayTag(DayTag("t1", "Corso", workProfileId = "principale"))
        ShiftRepository.saveDayAnnotations(date, "Nota principale", listOf("t1"), profileId = "principale")
        ShiftRepository.saveRotation(ShiftRotation("r1", "Rot 1", 1, listOf(""), workProfileId = "principale"))
        ShiftRepository.setDefaultRotation("r1", "principale")

        // Switch to secondary profile and verify isolation
        ShiftRepository.setActiveWorkProfile("secondario")
        assertFalse(ShiftRepository.isOnCall(date))
        assertTrue(ShiftRepository.isOnCall(date, "principale"))
        assertFalse(ShiftRepository.smartAlarmsConfig.isGlobalEnabled)
        assertTrue(ShiftRepository.smartAlarmsConfig.rules.isEmpty())
        assertTrue(ShiftRepository.activeDayTags.isEmpty())
        assertEquals("", ShiftRepository.getDayExtraInfo(date).notes)
        assertTrue(ShiftRepository.getRotationsSortedByDefault().isEmpty())
        assertEquals("", ShiftRepository.defaultRotationId)

        // Same tag name allowed in different profile
        ShiftRepository.saveDayTag(DayTag("t2", "Corso", workProfileId = "secondario"))
        assertEquals(1, ShiftRepository.dayTagsFor("secondario").size)
        assertEquals(1, ShiftRepository.dayTagsFor("principale").size)
    }

    @Test
    fun duplicateShiftToProfileAssignsNewUuidAndSkipsExactDuplicates() {
        ShiftRepository.saveShiftDefinition(
            ShiftDefinition("m1", "Mattina", "MAT", "06:00", "14:00", workProfileId = "principale")
        )
        ShiftRepository.saveShiftDefinition(
            ShiftDefinition("p1", "Pomeriggio", "POM", "14:00", "22:00", workProfileId = "principale")
        )

        val duplicated = ShiftRepository.duplicateShiftToProfile("m1", "secondario")
        assertNotNull(duplicated)
        assertNotEquals("m1", duplicated!!.id)
        assertEquals("secondario", duplicated.workProfileId)

        // Duplicate again -> skipped
        assertNull(ShiftRepository.duplicateShiftToProfile("m1", "secondario"))

        // Duplicate all -> only Pomeriggio is copied (1 new shift)
        val count = ShiftRepository.duplicateAllShiftsToProfile("principale", "secondario")
        assertEquals(1, count)
        assertEquals(2, ShiftRepository.shiftDefinitions.count { it.workProfileId == "secondario" })
    }

    @Test
    fun chronologicalSortingPlacesTimedBeforeAllDay() {
        val late = ShiftDefinition("late", "Notte", "NOT", "22:00", "06:00")
        val early = ShiftDefinition("early", "Mattina", "MAT", "06:00", "14:00")
        val allDay = ShiftDefinition("rest", "Riposo", "RIP", isAllDay = true, category = ShiftCategory.RIPOSO)

        val sorted = listOf(allDay, late, early).sortedChronologically()
        assertEquals(listOf("early", "late", "rest"), sorted.map { it.id })
    }

    @Test
    fun switchingProfileAllowsReadOnlySnapshotReadsWithoutMutationCrash() {
        val date = LocalDate.of(2026, 11, 15)
        val extraProfile = WorkProfile("terzo", "Terzo Lavoro", isDefault = false)
        ShiftRepository.addWorkProfile(extraProfile)
        ShiftRepository.saveShiftDefinition(
            ShiftDefinition("p3", "Pomeriggio", "POM", "14:00", "22:00", workProfileId = extraProfile.id)
        )
        ShiftRepository.setActiveWorkProfile(extraProfile.id)
        ShiftRepository.replaceShift(date, "p3")

        // Switch to another profile whose extras map has never been written to
        val fourthProfile = WorkProfile("quarto", "Quarto Lavoro", isDefault = false)
        ShiftRepository.addWorkProfile(fourthProfile)
        ShiftRepository.setActiveWorkProfile(fourthProfile.id)

        val snapshot = androidx.compose.runtime.snapshots.Snapshot.takeSnapshot()
        try {
            val stats = snapshot.enter {
                ShiftRepository.isRotationCleared(date)
                ShiftRepository.hasTrasfertaForDate(date)
                ShiftRepository.getDayExtraInfo(date)
                ShiftRepository.getComprehensiveReportStats(date.withDayOfMonth(1), date.withDayOfMonth(28))
            }
            val captured = snapshot.enter { DatabaseSnapshot.capture() }
            assertEquals(1, stats.workedDays)
            assertEquals(fourthProfile.id, captured.activeWorkProfileId)
        } finally {
            snapshot.dispose()
        }

        // Also verify read-only snapshot reads when dayExtraInfoByProfile has no entry for activeWorkProfileId
        val fifthProfile = WorkProfile("quinto", "Quinto Lavoro", isDefault = false)
        DatabaseRestore.apply(
            DatabaseSnapshot.capture().copy(
                profiles = ShiftRepository.workProfiles.toList() + fifthProfile,
                activeWorkProfileId = fifthProfile.id,
                extras = emptyMap(),
                extrasByProfile = emptyMap()
            )
        )
        val restoredSnapshot = androidx.compose.runtime.snapshots.Snapshot.takeSnapshot()
        try {
            val stats = restoredSnapshot.enter {
                ShiftRepository.isRotationCleared(date)
                ShiftRepository.hasTrasfertaForDate(date)
                ShiftRepository.getDayExtraInfo(date)
                ShiftRepository.getComprehensiveReportStats(date.withDayOfMonth(1), date.withDayOfMonth(28))
            }
            val captured = restoredSnapshot.enter { DatabaseSnapshot.capture() }
            assertEquals(1, stats.workedDays)
            assertEquals(fifthProfile.id, captured.activeWorkProfileId)
        } finally {
            restoredSnapshot.dispose()
        }
    }

    @Test
    fun enablingTurnoSpecificoAfterClearingDayAppliesImmediately() {
        val date = LocalDate.of(2026, 11, 18)
        ShiftRepository.setActiveWorkProfile("principale")
        ShiftRepository.saveShiftDefinition(
            ShiftDefinition("pomeriggio", "Pomeriggio", "POM", "14:00", "22:00", workProfileId = "principale")
        )
        ShiftRepository.replaceShift(date, "pomeriggio")
        ShiftRepository.clearWorkDayShifts(date, clearOnCall = true)
        assertFalse(ShiftRepository.isOnCall(date))

        ShiftRepository.replaceShift(date, "pomeriggio")
        ShiftRepository.updateReperibilitaConfig {
            it.copy(
                mode = ReperibilitaMode.TURNO_SPECIFICO,
                shiftSpecificIds = listOf("pomeriggio")
            )
        }
        assertTrue(ShiftRepository.isOnCall(date))
    }

    @Test
    fun updateReperibilitaConfigClearsStaleManualOffOnMatchingAssignedShifts() {
        val date = LocalDate.of(2026, 11, 19)
        ShiftRepository.setActiveWorkProfile("principale")
        ShiftRepository.saveShiftDefinition(
            ShiftDefinition("pomeriggio", "Pomeriggio", "POM", "14:00", "22:00", workProfileId = "principale")
        )
        ShiftRepository.replaceShift(date, "pomeriggio")
        ShiftRepository.setDayOnCallManual(date, false)
        assertFalse(ShiftRepository.isOnCall(date))

        ShiftRepository.updateReperibilitaConfig {
            it.copy(
                mode = ReperibilitaMode.TURNO_SPECIFICO,
                shiftSpecificIds = listOf("pomeriggio")
            )
        }
        assertTrue(ShiftRepository.isOnCall(date))
    }
}
