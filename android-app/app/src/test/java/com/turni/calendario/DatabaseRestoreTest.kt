package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.sync.DatabaseRestore
import com.turni.calendario.sync.GoogleDriveBackupHelper
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DatabaseRestoreTest {
    @Test
    fun v6BackupRoundTripsPerProfileStateAndSplitOvertimePermission() {
        val date = LocalDate.of(2026, 11, 15)
        val p1 = WorkProfile("principale", "Lavoro 1", weeklyHours = 38, minRestHoursBetweenShifts = 11)
        val p2 = WorkProfile("secondario", "Lavoro 2", weeklyHours = 20, minRestHoursBetweenShifts = 12, isDefault = false)
        val d1 = ShiftDefinition("m1", "Mattina", "MAT", "06:00", "14:00", workProfileId = "principale")
        val d2 = ShiftDefinition("m2", "Sera", "SER", "18:00", "22:00", workProfileId = "secondario")
        val shift = AssignedShift(
            id = "s1",
            date = date,
            shiftDefId = "m1",
            actualStartTime = "05:00",
            actualEndTime = "15:30",
            overtimeHours = 2.5f,
            permissionHours = 0.5f,
            startOvertimeHours = 1.0f,
            startPermissionHours = 0f,
            endOvertimeHours = 1.5f,
            endPermissionHours = 0.5f
        )
        val snapshot = DatabaseSnapshot(
            definitions = listOf(d1, d2),
            rotations = listOf(ShiftRotation("r2", "Rot 2", 1, listOf("m2"), workProfileId = "secondario")),
            shifts = mapOf(date to listOf(shift)),
            extras = mapOf(date to DayExtraInfo(date, notes = "Nota P1")),
            profiles = listOf(p1, p2),
            activeWorkProfileId = "principale",
            activeRotationId = "",
            defaultRotationId = "",
            preferences = AppPreferences(autoBackupDaily = false),
            onCall = ReperibilitaConfig(mode = ReperibilitaMode.MANUALE, manualDates = setOf(date)),
            alarms = SmartAlarmsConfig(isGlobalEnabled = false),
            holidayIds = setOf("natale"),
            statisticsRules = emptyList(),
            dayTags = listOf(DayTag("tag2", "Formazione", workProfileId = "secondario")),
            extrasByProfile = mapOf(
                "principale" to mapOf(date to DayExtraInfo(date, notes = "Nota P1")),
                "secondario" to mapOf(date to DayExtraInfo(date, notes = "Nota P2", tagIds = listOf("tag2")))
            ),
            onCallByProfile = mapOf(
                "principale" to ReperibilitaConfig(mode = ReperibilitaMode.MANUALE, manualDates = setOf(date)),
                "secondario" to ReperibilitaConfig(mode = ReperibilitaMode.TURNO_SPECIFICO, shiftSpecificIds = listOf("m2"))
            ),
            alarmsByProfile = mapOf(
                "principale" to SmartAlarmsConfig(isGlobalEnabled = false),
                "secondario" to SmartAlarmsConfig(isGlobalEnabled = true, rules = listOf(ShiftAlarmRule("m2", true, 30)))
            ),
            activeRotationIdByProfile = mapOf("secondario" to "r2"),
            defaultRotationIdByProfile = mapOf("secondario" to "r2")
        )

        val json = GoogleDriveBackupHelper.exportDatabaseToJson(snapshot)
        assertEquals(6, JSONObject(json).getInt("version"))

        val parsed = DatabaseRestore.parse(json, snapshot)
        assertEquals(snapshot, parsed)
    }

    @Test
    fun legacyV5BackupMigratesGlobalDataToActiveProfile() {
        val date = LocalDate.of(2026, 11, 20)
        val base = DatabaseSnapshot(
            definitions = listOf(ShiftDefinition("m1", "Mattina", "MAT", "06:00", "14:00", workProfileId = "principale")),
            rotations = listOf(ShiftRotation("r1", "Rot 1", 1, listOf("m1"))),
            shifts = mapOf(date to listOf(AssignedShift("s1", date, "m1"))),
            extras = mapOf(date to DayExtraInfo(date, notes = "Legacy")),
            profiles = listOf(WorkProfile("principale", "Il mio lavoro")),
            activeWorkProfileId = "principale",
            activeRotationId = "r1",
            defaultRotationId = "r1",
            preferences = AppPreferences(autoBackupDaily = false),
            onCall = ReperibilitaConfig(mode = ReperibilitaMode.MANUALE, manualDates = setOf(date)),
            alarms = SmartAlarmsConfig(isGlobalEnabled = true, rules = listOf(ShiftAlarmRule("m1", true, 60))),
            holidayIds = setOf("natale")
        )
        val jsonObj = JSONObject(GoogleDriveBackupHelper.exportDatabaseToJson(base))
        jsonObj.put("version", 5)
        jsonObj.remove("dayExtraInfoByProfile")
        jsonObj.remove("reperibilitaByProfile")
        jsonObj.remove("smartAlarmsByProfile")
        jsonObj.remove("activeRotationIdByProfile")
        jsonObj.remove("defaultRotationIdByProfile")

        val restored = DatabaseRestore.parse(jsonObj.toString(), base)
        assertEquals("Legacy", restored.extrasByProfile["principale"]?.get(date)?.notes)
        assertTrue(date in restored.onCallByProfile["principale"]?.manualDates.orEmpty())
        assertTrue(restored.alarmsByProfile["principale"]?.isGlobalEnabled == true)
        assertEquals("r1", restored.defaultRotationIdByProfile["principale"])
    }
}
