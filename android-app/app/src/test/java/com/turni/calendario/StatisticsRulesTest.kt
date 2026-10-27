package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.*
import com.turni.calendario.sync.*
import com.turni.calendario.ui.screens.reportMetrics
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatisticsRulesTest {
    private val date=LocalDate.of(2026,10,27)
    private val cover=ShiftDefinition("cover-id","VAC","V",category=ShiftCategory.ASSENZA,isAllDay=true)
    private val rest=ShiftDefinition("rest-id","Recupero","REC",category=ShiftCategory.RIPOSO,isAllDay=true)
    private val work=ShiftDefinition("work-id","Day","DAY",startTime="08:00",endTime="16:00")
    private val definitions=listOf(cover,rest,work)
    private val rule=StatisticsRule("rule",cover.id,setOf(rest.id,work.id))
    private val shift=AssignedShift("one",date,cover.id,originalShiftDefId=rest.id,isModifiedFromRotation=true)

    @Test fun renamedModelsUseIdsWithoutChangingCalendar() {
        val resolved=statisticsAssignment(shift,definitions.associateBy { it.id },listOf(rule))
        assertEquals(rest.id,resolved.shiftDefId)
        assertEquals(cover.id,shift.shiftDefId)
        val renamed=definitions.map { it.copy(name="Renamed",shortCode="R") }
        assertEquals(rest.id,statisticsAssignment(shift,renamed.associateBy { it.id },listOf(rule)).shiftDefId)
        assertEquals(rest.id,reportMetrics(listOf(resolved),definitions).single().definition.id)
    }
    @Test fun disabledRulesNoOriginalAndUnmatchedModelsCountVisibleShift() {
        listOf(shift.copy(originalShiftDefId=null),shift.copy(originalShiftDefId="missing"),shift.copy(originalShiftDefId=cover.id)).forEach {
            assertEquals(cover.id,statisticsAssignment(it,definitions.associateBy { it.id },listOf(rule)).shiftDefId)
        }
        assertEquals(cover.id,statisticsAssignment(shift,definitions.associateBy { it.id },listOf(rule.copy(enabled=false))).shiftDefId)
    }
    @Test fun rulesNeverChainOrCrossWorkProfiles() {
        val rules=listOf(rule,StatisticsRule("two",rest.id,setOf(work.id)))
        assertEquals(rest.id,statisticsAssignment(shift,definitions.associateBy { it.id },rules).shiftDefId)
        val foreign=definitions.map { if(it.id==rest.id)it.copy(workProfileId="other")else it }
        assertEquals(cover.id,statisticsAssignment(shift,foreign.associateBy { it.id },rules).shiftDefId)
    }
    @Test fun originalTemplateHoursReplaceOnlyVisibleTemplateOverrides() {
        val source=shift.copy(originalShiftDefId=work.id,customStartTime="01:00",customEndTime="03:00",overtimeHours=.5f,notes="Memo")
        val resolved=statisticsAssignment(source,definitions.associateBy { it.id },listOf(rule))
        assertEquals(8.5f,reportMetrics(listOf(resolved),definitions).single().hours,.001f)
        assertEquals("Memo",resolved.notes)
    }
    @Test fun allReportTotalsAgreeAndBackupRoundTrips() {
        val original=DatabaseSnapshot.capture()
        try {
            val before=original.copy(definitions=definitions,rotations=emptyList(),shifts=mapOf(date to listOf(shift)),extras=emptyMap(),statisticsRules=listOf(rule),onCall=ReperibilitaConfig())
            DatabaseRestore.apply(before)
            val stats=ShiftRepository.getComprehensiveReportStats(date,date)
            assertEquals(mapOf(rest.id to 1),stats.shiftCounts)
            assertEquals(stats.shiftCounts,ShiftRepository.getReportStats(date,date))
            assertEquals(0f,stats.totalHours)
            val parsed=DatabaseRestore.parse(GoogleDriveBackupHelper.exportDatabaseToJson(before),before)
            assertEquals(before.statisticsRules,parsed.statisticsRules)
            ShiftRepository.saveStatisticsRule(rule.copy(enabled=false))
            assertEquals(mapOf(cover.id to 1),ShiftRepository.getComprehensiveReportStats(date,date).shiftCounts)
        }finally {DatabaseRestore.apply(original)}
    }
    @Test fun deletingModelCleansRulesAndCannotLeaveDanglingReferences() {
        val original=DatabaseSnapshot.capture()
        try {
            DatabaseRestore.apply(original.copy(definitions=definitions,rotations=emptyList(),shifts=mapOf(date to listOf(shift)),statisticsRules=listOf(rule)))
            ShiftRepository.deleteShiftDefinition(rest.id)
            assertEquals(setOf(work.id),ShiftRepository.statisticsRules.single().originalShiftIds)
            assertNull(ShiftRepository.calendarShifts[date]!!.single().originalShiftDefId)
            ShiftRepository.deleteShiftDefinition(cover.id)
            assertTrue(ShiftRepository.statisticsRules.isEmpty())
        }finally {DatabaseRestore.apply(original)}
    }
    @Test fun invalidRulesAreRejectedAndEmptyListDoesNotReenableLegacyRule() {
        try {validateStatisticsRules(listOf(rule.copy(originalShiftIds=setOf(cover.id))),definitions);fail()}catch(_:IllegalArgumentException){}
        try {validateStatisticsRules(listOf(rule,rule.copy(id="two")),definitions);fail()}catch(_:IllegalArgumentException){}
        val base=DatabaseSnapshot.capture()
        val json=JSONObject(GoogleDriveBackupHelper.exportDatabaseToJson(base.copy(statisticsRules=emptyList())))
        assertTrue(DatabaseRestore.parse(json.toString(),base).statisticsRules.isEmpty())
        json.put("version",3).remove("statisticsRules")
        assertEquals(legacyStatisticsRules(base.definitions),DatabaseRestore.parse(json.toString(),base).statisticsRules)
    }
}
