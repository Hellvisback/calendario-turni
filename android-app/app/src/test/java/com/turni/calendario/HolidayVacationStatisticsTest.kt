package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.*
import com.turni.calendario.sync.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HolidayVacationStatisticsTest {
    private val day=LocalDate.of(2026,12,25)
    private val leave=ShiftDefinition("custom-vacation","VAC","VAC",isAllDay=true,category=ShiftCategory.ASSENZA)
    private val rest=ShiftDefinition("custom-rest","REC","REC",isAllDay=true,category=ShiftCategory.RIPOSO)
    private val work=ShiftDefinition("custom-work","Day","DAY",startTime="08:00",endTime="16:00")
    private val models=listOf(leave,rest,work)
    private val map=models.associateBy{it.id}
    private val shift=AssignedShift("leave",day,leave.id)

    @Test fun exclusionNeedsBothSwitchAndConfiguredHolidayAndSelectedModel() {
        assertNull(countedStatisticsAssignment(shift,map,emptyList(),true,true,setOf(leave.id)))
        assertNotNull(countedStatisticsAssignment(shift,map,emptyList(),false,true,setOf(leave.id)))
        assertNotNull(countedStatisticsAssignment(shift,map,emptyList(),true,false,setOf(leave.id)))
        assertNotNull(countedStatisticsAssignment(shift,map,emptyList(),true,true,setOf(work.id)))
        assertNotNull(countedStatisticsAssignment(shift,map,emptyList(),true,true,emptySet()))
    }
    @Test fun originalRestRuleIsStillCountedAndCalendarIsUnchanged() {
        val visible=shift.copy(originalShiftDefId=rest.id)
        val rule=StatisticsRule("restore-rest",leave.id,setOf(rest.id))
        assertEquals(rest.id,countedStatisticsAssignment(visible,map,listOf(rule),true,true,setOf(leave.id))!!.shiftDefId)
        assertEquals(leave.id,visible.shiftDefId)
        assertEquals(rest.id,visible.originalShiftDefId)
    }
    @Test fun customNamesDoNotAffectSelectedModelIds() {
        val renamed=models.map{it.copy(name="Nuovo nome",shortCode="X")}.associateBy{it.id}
        assertNull(countedStatisticsAssignment(shift,renamed,emptyList(),true,true,setOf(leave.id)))
    }
    @Test fun totalsAgreeAndHolidayVisibilityDoesNotDisableTheRule() {
        val original=DatabaseSnapshot.capture()
        try {
            val holiday=HolidaysCatalog.allHolidays.first{it.getDateForYear(day.year)==day}
            val next=shift.copy(id="next",date=day.plusDays(1))
            val second=AssignedShift("second",day,work.id)
            DatabaseRestore.apply(original.copy(definitions=models,rotations=emptyList(),statisticsRules=emptyList(),
                shifts=mapOf(day to listOf(shift,second),day.plusDays(1) to listOf(next)),extras=emptyMap(),
                holidayIds=setOf(holiday.id),preferences=AppPreferences(showHolidays=false,excludeVacationOnHolidays=true,holidayExcludedShiftIds=setOf(leave.id))))
            assertNull(ShiftRepository.isHoliday(day))
            val totals=ShiftRepository.getComprehensiveReportStats(day,day.plusDays(1))
            assertEquals(mapOf(work.id to 1,leave.id to 1),totals.shiftCounts)
            assertEquals(totals.shiftCounts,ShiftRepository.getReportStats(day,day.plusDays(1)))
            assertEquals(8f,totals.totalHours,.001f)
            assertEquals(1,totals.workedDays)
            assertEquals(leave.id,ShiftRepository.getShiftsForDate(day).first().shiftDefId)
            ShiftRepository.enabledHolidayIds=emptySet()
            assertEquals(2,ShiftRepository.getReportStats(day,day.plusDays(1))[leave.id])
        } finally {DatabaseRestore.apply(original)}
    }
    @Test fun backupRoundTripPreservesRuleAndOldBackupsDefaultToOff() {
        val original=DatabaseSnapshot.capture()
        val configured=original.copy(preferences=original.preferences.copy(excludeVacationOnHolidays=true,holidayExcludedShiftIds=setOf(original.definitions.first().id)))
        val json=JSONObject(GoogleDriveBackupHelper.exportDatabaseToJson(configured))
        assertEquals(configured.preferences,DatabaseRestore.parse(json.toString(),configured).preferences)
        json.getJSONObject("appPreferences").remove("excludeVacationOnHolidays")
        json.getJSONObject("appPreferences").remove("holidayExcludedShiftIds")
        val legacy=DatabaseRestore.parse(json.toString(),configured)
        assertFalse(legacy.preferences.excludeVacationOnHolidays)
        assertTrue(legacy.preferences.holidayExcludedShiftIds.isEmpty())
    }

    @Test fun turningOffNeverAddsBackAnUnselectedVacationModel() {
        val seeded=models+leave.copy(id="ferie")
        assertTrue(holidayExclusionSelectionForToggle(emptySet(),seeded,false).isEmpty())
        assertEquals(setOf("ferie"),holidayExclusionSelectionForToggle(emptySet(),seeded,true))
        assertEquals(setOf(leave.id),holidayExclusionSelectionForToggle(setOf(leave.id),seeded,false))
        assertEquals(setOf(leave.id),holidayExclusionSelectionForToggle(setOf(leave.id),seeded,true))
        assertTrue(holidayExclusionSelectionForToggle(emptySet(),models,true).isEmpty())
    }
}
