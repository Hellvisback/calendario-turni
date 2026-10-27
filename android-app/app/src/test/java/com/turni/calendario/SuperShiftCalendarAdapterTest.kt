package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SuperShiftCalendarAdapterTest {
    private val date=LocalDate.of(2026,10,27)
    private val zone=ZoneId.of("Europe/Rome")
    private fun ics(content:String)="BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\n$content\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
    private fun event(uid:String,date:LocalDate=this.date,start:String?="22:00",end:String?="06:00")=ImportShiftEvent(uid,date,"Custom shift",start,end,"Memo")
    private fun rejected(text:String) {try{SuperShiftCalendarAdapter.readCalendar(text,zone);fail("Must not partially import an unsupported file")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
    @Test fun overnightTimesFoldedNotesAndEscapedTextArePreserved() {
        val text=ics("UID:night-1\r\nSUMMARY:Night\r\nDTSTART;TZID=Europe/Rome:20261027T220000\r\nDTEND;TZID=Europe/Rome:20261028T060000\r\nDESCRIPTION:First line\\nSecond\r\n  line\r\nLOCATION:Office")
        val result=SuperShiftCalendarAdapter.readCalendar(text,zone).single()
        assertEquals("22:00",result.start);assertEquals("06:00",result.end)
        assertEquals("First line\nSecond line\nLuogo: Office",result.notes)
    }
    @Test fun utcAndExplicitDateTimeParametersAreNotMistakenForAllDay() {
        val result=SuperShiftCalendarAdapter.readCalendar(ics("UID:utc\r\nSUMMARY:Early\r\nDTSTART;VALUE=DATE-TIME:20261027T050000Z\r\nDTEND:20261027T130000Z"),zone).single()
        assertEquals("06:00",result.start);assertEquals("14:00",result.end)
    }
    @Test fun allDayEndIsExclusiveAndMultiDayEventsBecomeSeparateEditableDays() {
        val events=SuperShiftCalendarAdapter.readCalendar(ics("UID:leave\r\nSUMMARY:Leave\r\nDTSTART;VALUE=DATE:20261027\r\nDTEND;VALUE=DATE:20261030"),zone)
        assertEquals(listOf(date,date.plusDays(1),date.plusDays(2)),events.map { it.date });assertTrue(events.all { it.allDay })
    }
    @Test fun unsupportedRecurrenceJsonMissingIdsAndTruncationAreRejected() {
        rejected("{\"shifts\":[]}")
        rejected(ics("UID:r\r\nSUMMARY:R\r\nDTSTART;VALUE=DATE:20261027\r\nRRULE:FREQ=DAILY;COUNT=10"))
        rejected(ics("SUMMARY:No UID\r\nDTSTART;VALUE=DATE:20261027"))
        rejected("BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:a")
    }
    @Test fun zeroDurationAndMultiDayTimedEventsAreRejected() {
        rejected(ics("UID:bad\r\nSUMMARY:Bad\r\nDTSTART:20261027T220000\r\nDTEND:20261027T220000"))
        rejected(ics("UID:long\r\nSUMMARY:Long\r\nDTSTART:20261027T220000\r\nDTEND:20261029T060000"))
    }
    @Test fun invalidDstGapIsRejectedWithoutInventingAnHour() {
        rejected(ics("UID:dst\r\nSUMMARY:DST\r\nDTSTART;TZID=Europe/Rome:20260329T023000\r\nDTEND;TZID=Europe/Rome:20260329T040000"))
    }
    @Test fun importIsAdditiveIdempotentAndKeepsManualEdits() {
        val base=DatabaseSnapshot.capture()
        val plan=SuperShiftCalendarAdapter.plan(listOf(event("one"),event("two",date.plusDays(1),"23:00","07:00")),base)
        assertEquals(1,plan.definitions.size);assertEquals(2,plan.assignments.size)
        assertTrue(plan.assignments.any { it.customStartTime!=null })
        val result=SuperShiftCalendarAdapter.merge(plan,emptyMap(),base)
        assertEquals(2,result.importedCount);assertEquals(base.shifts.values.sumOf { it.size }+2,result.snapshot.shifts.values.sumOf { it.size })
        val edited=result.snapshot.copy(shifts=result.snapshot.shifts.mapValues { (_,shifts)->shifts.map { if(it.id==plan.assignments[0].id)it.copy(notes="My edit")else it } })
        val again=SuperShiftCalendarAdapter.plan(listOf(event("one"),event("two",date.plusDays(1),"23:00","07:00")),edited)
        assertEquals(2,again.duplicateCount);assertTrue(again.assignments.isEmpty())
        assertEquals(edited,SuperShiftCalendarAdapter.merge(again,emptyMap(),edited).snapshot)
        assertEquals("My edit",edited.shifts.values.flatten().first { it.id==plan.assignments[0].id }.notes)
    }
    @Test fun modelCategoriesAreExplicitAndExistingDataNeverChanged() {
        val base=DatabaseSnapshot.capture()
        val plan=SuperShiftCalendarAdapter.plan(listOf(event("all-day",start=null,end=null)),base)
        assertEquals(ShiftCategory.ALTRO,plan.definitions.single().category)
        val result=SuperShiftCalendarAdapter.merge(plan,mapOf(plan.definitions.single().id to ShiftCategory.RIPOSO),base)
        assertEquals(ShiftCategory.RIPOSO,result.snapshot.definitions.last().category)
        assertEquals(base.statisticsRules,result.snapshot.statisticsRules)
        assertEquals(base.rotations,result.snapshot.rotations)
        assertEquals(base.alarms,result.snapshot.alarms)
    }
    @Test fun conflictingUidIsRejectedButExactDuplicatesAreCollapsed() {
        val base=DatabaseSnapshot.capture()
        assertEquals(1,SuperShiftCalendarAdapter.plan(listOf(event("same"),event("same")),base).assignments.size)
        try{SuperShiftCalendarAdapter.plan(listOf(event("same"),event("same",start="20:00")),base);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun googleExpandedRecurringInstancesAndNotesMapToNativeEvents() {
        fun source(start:String)=JSONObject().put("id",start).put("iCalUID","recurring-uid").put("summary","Custom shift").put("description","Memo").put("location","Office")
            .put("originalStartTime",JSONObject().put("dateTime",start))
            .put("start",JSONObject().put("dateTime",start)).put("end",JSONObject().put("dateTime",start.replace("22:00","23:00")))
        val calendar=ImportCalendar("calendar","Work","Europe/Rome","#aabbcc")
        val events=listOf("2026-10-27T22:00:00+01:00","2026-10-28T22:00:00+01:00").flatMap { SuperShiftCalendarAdapter.googleEvent(source(it),calendar) }
        assertEquals(2,events.size);assertEquals(0xFFAABBCC,events.first().color)
        assertTrue(events.first().notes.contains("Office"))
        assertEquals(2,SuperShiftCalendarAdapter.plan(events,DatabaseSnapshot.capture()).assignments.size)
    }
    @Test fun movedEventRetainsIdentityAndDoesNotCreateASecondCopy() {
        val base=DatabaseSnapshot.capture()
        val plan=SuperShiftCalendarAdapter.plan(listOf(event("stable")),base)
        val imported=SuperShiftCalendarAdapter.merge(plan,emptyMap(),base).snapshot
        val moved=SuperShiftCalendarAdapter.plan(listOf(event("stable",date.plusDays(2))),imported)
        assertTrue(moved.assignments.isEmpty());assertEquals(1,moved.duplicateCount)
    }
    @Test fun googleAndIcsUseTheSameOccurrenceIdentity() {
        val source=JSONObject().put("id","instance").put("iCalUID","uid").put("summary","Shift")
            .put("originalStartTime",JSONObject().put("dateTime","2026-10-27T22:00:00+01:00"))
            .put("start",JSONObject().put("dateTime","2026-10-27T22:00:00+01:00"))
            .put("end",JSONObject().put("dateTime","2026-10-28T06:00:00+01:00"))
        val google=SuperShiftCalendarAdapter.googleEvent(source,ImportCalendar("calendar","C","Europe/Rome"))
        val file=SuperShiftCalendarAdapter.readCalendar(ics("UID:uid\r\nSUMMARY:Shift\r\nRECURRENCE-ID:20261027T210000Z\r\nDTSTART:20261027T210000Z\r\nDTEND:20261028T050000Z"),zone)
        val base=DatabaseSnapshot.capture()
        val imported=SuperShiftCalendarAdapter.merge(SuperShiftCalendarAdapter.plan(google,base),emptyMap(),base).snapshot
        assertEquals(1,SuperShiftCalendarAdapter.plan(file,imported).duplicateCount)
    }

    @Test fun importedAssignmentsUseTheNormalNativeEditAndEraseOperations() {
        val base=DatabaseSnapshot.capture()
        try {
            val imported=SuperShiftCalendarAdapter.merge(SuperShiftCalendarAdapter.plan(listOf(event("editable")),base),emptyMap(),base)
            DatabaseRestore.apply(imported.snapshot)
            val assigned=ShiftRepository.calendarShifts[date]!!.first { it.id.startsWith("ss-event-") }
            ShiftRepository.setShiftActualEndTime(date,assigned.id,"07:00",1f)
            val edited=ShiftRepository.calendarShifts[date]!!.first { it.id==assigned.id }
            assertEquals(1f,edited.overtimeHours);assertEquals("07:00",edited.actualEndTime)
            ShiftRepository.removeAssignedShift(date,assigned.id)
            assertTrue(ShiftRepository.calendarShifts[date].orEmpty().none { it.id==assigned.id })
            assertEquals(base.shifts[date].orEmpty(),ShiftRepository.calendarShifts[date].orEmpty())
        } finally {DatabaseRestore.apply(base)}
    }

}
