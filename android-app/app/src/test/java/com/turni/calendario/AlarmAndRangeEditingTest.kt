package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository as Repository
import com.turni.calendario.sync.DatabaseRestore
import com.turni.calendario.alarm.ShiftAlarmScheduler
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AlarmAndRangeEditingTest {
    @Test fun editingTimeDoesNotReenableDisabledRuleOrOverwriteOtherRules() {
        val original=DatabaseSnapshot.capture()
        try {
            Repository.smartAlarmsConfig=SmartAlarmsConfig(isGlobalEnabled=false,rules=listOf(
                ShiftAlarmRule("reperibilita",true,customTime="20:00"),ShiftAlarmRule("office",true)))
            Repository.updateSmartAlarmRule("office"){it.copy(isEnabled=false)}
            Repository.updateSmartAlarmRule("reperibilita"){it.copy(customTime="18:45")}
            Repository.updateSmartAlarmRule("reperibilita"){it.copy(isEnabled=false)}
            Repository.updateSmartAlarmRule("reperibilita"){it.copy(customTime="19:15")}
            val config=Repository.smartAlarmsConfig
            assertFalse(config.isGlobalEnabled)
            assertFalse(config.rules.first{it.shiftDefId=="office"}.isEnabled)
            val rule=config.rules.first{it.shiftDefId=="reperibilita"}
            assertFalse(rule.isEnabled)
            assertEquals("19:15",rule.customTime)
            assertEquals(2,config.rules.size)
        } finally { DatabaseRestore.apply(original) }
    }

    @Test fun onCallTimeChangeReschedulesCalculatedReminderAndRoundTrips() {
        val original=DatabaseSnapshot.capture()
        try {
            val date=LocalDate.of(2030,3,5)
            Repository.calendarShifts.clear();Repository.dayExtraInfo.clear()
            Repository.reperibilitaConfig=ReperibilitaConfig(mode=ReperibilitaMode.MANUALE)
            Repository.setDayOnCallManual(date.plusDays(1),true)
            Repository.smartAlarmsConfig=SmartAlarmsConfig(rules=listOf(ShiftAlarmRule("reperibilita",true,customTime="20:00")))
            Repository.updateSmartAlarmRule("reperibilita"){it.copy(customTime="18:45")}
            val snapshot=DatabaseSnapshot.capture()
            DatabaseRestore.apply(snapshot)
            val reminder=ShiftAlarmScheduler.calculateUpcomingAlarms(date.atStartOfDay(),1).single()
            assertEquals(date.atTime(18,45),reminder.triggerDateTime)
            assertTrue(reminder.isEnabled)
        } finally { DatabaseRestore.apply(original) }
    }

    @Test fun intervalEraseIsInclusiveBidirectionalAndOneUndoPreservesNotesAndOtherWork() {
        val original=DatabaseSnapshot.capture()
        try {
            Repository.calendarShifts.clear();Repository.dayExtraInfo.clear()
            Repository.reperibilitaConfig=ReperibilitaConfig(mode=ReperibilitaMode.MANUALE)
            val definition=Repository.shiftDefinitions.first{it.workProfileId==Repository.activeWorkProfileId}
            val other=definition.copy(id="other-work-test",workProfileId="other-work-test")
            Repository.shiftDefinitions.add(other)
            val start=LocalDate.of(2030,1,30)
            for(i in -1L..4L){
                val date=start.plusDays(i)
                Repository.replaceShift(date,definition.id)
                Repository.calendarShifts[date]= (Repository.getShiftsForDate(date)+AssignedShift("other-$i",date,other.id)).toMutableList()
                Repository.setDayOnCallManual(date,true)
                Repository.dayExtraInfo[date]=Repository.getDayExtraInfo(date).copy(notes="Keep my note")
            }
            val before=DatabaseSnapshot.capture()
            Repository.clearWorkShiftRange(start.plusDays(3),start)
            for(i in 0L..3L){
                val date=start.plusDays(i)
                assertEquals(listOf(other.id),Repository.getShiftsForDate(date).map{it.shiftDefId})
                assertFalse(Repository.isOnCall(date))
                assertEquals("Keep my note",Repository.getDayExtraInfo(date).notes)
            }
            assertEquals(2,Repository.getShiftsForDate(start.minusDays(1)).size)
            assertEquals(2,Repository.getShiftsForDate(start.plusDays(4)).size)
            assertTrue(Repository.undo())
            assertEquals(before.shifts,DatabaseSnapshot.capture().shifts)
            assertTrue(Repository.isOnCall(start.plusDays(1)))
        } finally { DatabaseRestore.apply(original) }
    }
    @Test fun addingRangePreservesEveryBaseShiftAndUsesOneUndoAcrossMonths() {
        val original=DatabaseSnapshot.capture()
        try {
            Repository.calendarShifts.clear();Repository.dayExtraInfo.clear()
            val models=Repository.shiftDefinitions.filter{it.workProfileId==Repository.activeWorkProfileId}
            val base=models.first();val extra=models.first{it.id!=base.id}
            val start=LocalDate.of(2030,1,30)
            for(offset in -1L..4L)Repository.replaceShift(start.plusDays(offset),base.id)
            val before=DatabaseSnapshot.capture()
            Repository.applyShiftRange(extra.id,start.plusDays(3),start,addSecondary=true)
            for(offset in 0L..3L){
                val shifts=Repository.getShiftsForDate(start.plusDays(offset))
                assertEquals(listOf(base.id,extra.id),shifts.map{it.shiftDefId})
                assertEquals(before.shifts[start.plusDays(offset)]!!.first(),shifts.first())
            }
            assertEquals(1,Repository.getShiftsForDate(start.minusDays(1)).size)
            assertEquals(1,Repository.getShiftsForDate(start.plusDays(4)).size)
            assertTrue(Repository.undo())
            assertEquals(before.shifts,DatabaseSnapshot.capture().shifts)
        }finally{DatabaseRestore.apply(original)}
    }

}

