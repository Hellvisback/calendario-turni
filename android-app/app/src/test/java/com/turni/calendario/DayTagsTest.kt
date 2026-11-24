package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.*
import com.turni.calendario.sync.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DayTagsTest {
    private val day=LocalDate.of(2026,11,13)
    private val training=DayTag("training","Formazione","graduation-cap")
    private val health=DayTag("health","Visita medica","heart-pulse",0xFF83CDB7)
    private fun fixture(): DatabaseSnapshot {
        val extrasMap = mapOf(
            day to DayExtraInfo(day, 1.5f, true, true, true, true, "Corso sicurezza", "18:30", listOf(training.id, health.id)),
            day.plusDays(1) to DayExtraInfo(day.plusDays(1), tagIds = listOf(training.id))
        )
        val snap = DatabaseSnapshot.capture()
        return snap.copy(
            dayTags = listOf(training, health),
            extras = extrasMap,
            extrasByProfile = mapOf(snap.activeWorkProfileId to extrasMap)
        )
    }

    @Test fun labelsAndTheirDatesSurviveCompleteBackup() {
        val expected=fixture()
        assertEquals(expected,DatabaseRestore.parse(GoogleDriveBackupHelper.exportDatabaseToJson(expected)))
    }
    @Test fun oldBackupHasNoLabelsFromAnotherDatabase() {
        val json=JSONObject(GoogleDriveBackupHelper.exportDatabaseToJson(fixture()))
        json.put("version",4).remove("dayTags")
        val extras=json.getJSONArray("dayExtraInfo")
        for(i in 0 until extras.length())extras.getJSONObject(i).remove("tagIds")
        val restored=DatabaseRestore.parse(json.toString(),fixture())
        assertTrue(restored.dayTags.isEmpty())
        assertTrue(restored.extras.values.all { it.tagIds.isEmpty() })
    }
    @Test fun malformedReferencesAreRejectedBeforeChangingLiveData() {
        val before=DatabaseSnapshot.capture()
        val json=JSONObject(GoogleDriveBackupHelper.exportDatabaseToJson(fixture()))
        json.getJSONArray("dayExtraInfo").getJSONObject(0).getJSONArray("tagIds").put("missing")
        json.optJSONObject("dayExtraInfoByProfile")?.let { byProfile ->
            byProfile.keys().asSequence().firstOrNull()?.let { pId ->
                byProfile.getJSONArray(pId).getJSONObject(0).getJSONArray("tagIds").put("missing")
            }
        }
        assertThrows(IllegalArgumentException::class.java) { DatabaseRestore.parse(json.toString()) }
        assertEquals(before,DatabaseSnapshot.capture())
    }
    @Test fun eachLabelCountsDatesInclusivelyAndIndependentlyOfOtherLabels() {
        val fixture=fixture()
        val metrics=dayTagMetrics(fixture.dayTags,fixture.extras,day,day.plusDays(1))
        assertEquals(listOf(day.plusDays(1),day),metrics.first { it.tag.id==training.id }.dates)
        assertEquals(listOf(day),metrics.first { it.tag.id==health.id }.dates)
        assertEquals(1,dayTagMetrics(fixture.dayTags,fixture.extras,day,day).first { it.tag.id==training.id }.dates.size)
        assertTrue(dayTagMetrics(fixture.dayTags,fixture.extras,day.plusDays(1),day).isEmpty())
    }
    @Test fun archivingAndRenamingKeepTheOriginalHistory() {
        val fixture=fixture()
        val renamed=training.copy(name="Corso",archived=true)
        val metric=dayTagMetrics(listOf(renamed,health),fixture.extras,day,day.plusDays(1)).first { it.tag.id==training.id }
        assertEquals("Corso",metric.tag.name)
        assertTrue(metric.tag.archived)
        assertEquals(2,metric.dates.size)
    }
    @Test fun annotationSavePreservesEveryOtherDayFieldAndCanBeUndone() {
        val before=DatabaseSnapshot.capture()
        try {
            val fixture=fixture()
            DatabaseRestore.apply(fixture)
            ShiftRepository.saveDayAnnotations(day,"Nuovi dettagli",listOf(training.id,training.id))
            assertEquals(fixture.extras.getValue(day).copy(notes="Nuovi dettagli",tagIds=listOf(training.id)),ShiftRepository.getDayExtraInfo(day))
            assertEquals(fixture.shifts,ShiftRepository.calendarShifts.mapValues { it.value.toList() })
            assertTrue(ShiftRepository.undo())
            assertEquals(fixture.extras.getValue(day),ShiftRepository.getDayExtraInfo(day))
            ShiftRepository.clearAllShifts()
            assertEquals(listOf(training.id,health.id),ShiftRepository.getDayExtraInfo(day).tagIds)
            assertEquals("Corso sicurezza",ShiftRepository.getDayExtraInfo(day).notes)
        } finally { DatabaseRestore.apply(before) }
    }
    @Test fun deletingUnusedLabelCannotCreateDanglingUndoReferences() {
        val before=DatabaseSnapshot.capture()
        try {
            DatabaseRestore.apply(fixture())
            assertThrows(IllegalArgumentException::class.java) { ShiftRepository.deleteUnusedDayTag(training.id) }
            ShiftRepository.saveDayAnnotations(day,"Corso sicurezza",listOf(health.id))
            ShiftRepository.saveDayAnnotations(day.plusDays(1),"",emptyList())
            ShiftRepository.deleteUnusedDayTag(training.id)
            assertTrue(ShiftRepository.undo())
            validateDayTags(ShiftRepository.dayTags.toList(),ShiftRepository.dayExtraInfo.toMap())
            assertTrue(ShiftRepository.dayExtraInfo.values.none { training.id in it.tagIds })
        }finally { DatabaseRestore.apply(before) }
    }
    @Test fun namesAreNormalizedAndDuplicatesHaveAUsefulError() {
        assertEquals("visita medica",normalizedTagName("  VISITA   medica  "))
        assertThrows(IllegalArgumentException::class.java) {
            validateDayTags(listOf(health,health.copy(id="other",name=" VISITA   MEDICA ")),emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateDayTags(listOf(training.copy(iconKey="unknown")),emptyMap())
        }
    }
}
