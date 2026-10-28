package com.turni.calendario

import com.turni.calendario.model.ReperibilitaConfig
import com.turni.calendario.model.ReperibilitaMode
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DatabaseRestore
import com.turni.calendario.ui.screens.calendarNeedsSelectiveEraser
import com.turni.calendario.ui.screens.calendarPeriodContains
import com.turni.calendario.ui.screens.calendarPaintDates
import com.turni.calendario.ui.screens.CalendarPaintRequest
import com.turni.calendario.ui.screens.rotationCycleNumber
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarEditInteractionTest {
    private val start=LocalDate.of(2026,10,28)

    @Test fun firstPeriodTapDoesNotPreviewOtherDays() {
        for(offset in -42L..42L)assertFalse(calendarPeriodContains(start,null,start.plusDays(offset)))
    }

    @Test fun completedPeriodIncludesBothEndsInEitherDirection() {
        val end=start.plusDays(5)
        for(offset in -2L..8L){
            val expected=offset in 0L..5L
            assertEquals(expected,calendarPeriodContains(start,end,start.plusDays(offset)))
            assertEquals(expected,calendarPeriodContains(end,start,start.plusDays(offset)))
        }
    }

    @Test fun rotationCycleMarkersContinueAcrossMonthBoundaries() {
        assertEquals(1,rotationCycleNumber(start,start,7))
        assertNull(rotationCycleNumber(start,start.minusDays(7),7))
        assertNull(rotationCycleNumber(start,start.plusDays(6),7))
        assertEquals(2,rotationCycleNumber(start,start.plusDays(7),7))
        assertEquals(9,rotationCycleNumber(start,start.plusDays(56),7))
        assertNull(rotationCycleNumber(start,start,0))
        assertNull(rotationCycleNumber(null,start,7))
    }

    @Test fun onlyMultipleAssignmentsNeedTheSelectiveEraser() {
        assertFalse(calendarNeedsSelectiveEraser(0))
        assertFalse(calendarNeedsSelectiveEraser(1))
        assertTrue(calendarNeedsSelectiveEraser(2))
        assertTrue(calendarNeedsSelectiveEraser(5))
    }

    @Test fun singleShiftAndOnCallEraseCanBeUndoneTogether() {
        val original=DatabaseSnapshot.capture()
        try {
            ShiftRepository.calendarShifts.clear()
            ShiftRepository.dayExtraInfo.clear()
            ShiftRepository.reperibilitaConfig=ReperibilitaConfig(mode=ReperibilitaMode.MANUALE)
            val definition=ShiftRepository.shiftDefinitions.first{it.workProfileId==ShiftRepository.activeWorkProfileId}
            ShiftRepository.replaceShift(start,definition.id)
            ShiftRepository.setDayOnCallManual(start,true)
            ShiftRepository.clearWorkDayShifts(start,clearOnCall=true)
            assertTrue(ShiftRepository.getShiftsForDate(start).isEmpty())
            assertFalse(ShiftRepository.isOnCall(start))
            assertTrue(ShiftRepository.undo())
            assertEquals(definition.id,ShiftRepository.getShiftsForDate(start).single().shiftDefId)
            assertTrue(ShiftRepository.isOnCall(start))
        }finally{DatabaseRestore.apply(original)}
    }

    @Test fun rangePaintIncludesEveryInteriorDayAcrossMonthInBothDirections() {
        val end=start.plusDays(6)
        val visible=List(42){start.minusDays(8).plusDays(it.toLong())}
        val expected=(0L..6L).map{start.plusDays(it)}.toSet()
        assertEquals(expected,calendarPaintDates(CalendarPaintRequest(start,"test",1,end),visible))
        assertEquals(expected,calendarPaintDates(CalendarPaintRequest(end,"test",2,start),visible))
        assertEquals(expected.take(2).toSet(),calendarPaintDates(CalendarPaintRequest(start,"test",3,end),expected.take(2)))
        assertTrue(calendarPaintDates(CalendarPaintRequest(start,"test",4,end),listOf(start.minusDays(1))).isEmpty())
    }
    @Test fun appliedRangeUpdatesAllDaysAndOneUndoRestoresTheWholeOperation() {
        val original=DatabaseSnapshot.capture()
        try {
            ShiftRepository.calendarShifts.clear()
            ShiftRepository.dayExtraInfo.clear()
            val model=ShiftRepository.shiftDefinitions.first{it.workProfileId==ShiftRepository.activeWorkProfileId}
            ShiftRepository.applyShiftRange(model.id,start.plusDays(6),start)
            for(offset in 0L..6L)assertEquals(model.id,ShiftRepository.getShiftsForDate(start.plusDays(offset)).single().shiftDefId)
            assertTrue(ShiftRepository.undo())
            assertTrue(ShiftRepository.calendarShifts.isEmpty())
        } finally {DatabaseRestore.apply(original)}
    }
}
