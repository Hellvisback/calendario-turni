package com.turni.calendario

import com.turni.calendario.model.ReperibilitaConfig
import com.turni.calendario.model.ReperibilitaMode
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DatabaseRestore
import com.turni.calendario.ui.screens.calendarNeedsSelectiveEraser
import com.turni.calendario.ui.screens.calendarPeriodContains
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarExtraEraserTest {
    private val day=LocalDate.of(2026,11,3)

    @Test fun emptyAndSingleDaysEraseImmediately(){
        assertFalse(calendarNeedsSelectiveEraser(0));assertFalse(calendarNeedsSelectiveEraser(1))
    }
    @Test fun twoAssignmentsRequireChoice(){assertTrue(calendarNeedsSelectiveEraser(2))}
    @Test fun threeOrMoreAlsoRequireChoice(){assertTrue(calendarNeedsSelectiveEraser(3));assertTrue(calendarNeedsSelectiveEraser(8))}
    @Test fun selectiveEraseRemovesOnCallPreservesOtherDetailsAndCanBeUndone() {
        val original=DatabaseSnapshot.capture()
        try {
            ShiftRepository.calendarShifts.clear()
            ShiftRepository.dayExtraInfo.clear()
            ShiftRepository.reperibilitaConfig=ReperibilitaConfig(mode=ReperibilitaMode.MANUALE)
            val definitions=ShiftRepository.shiftDefinitions.filter{it.workProfileId==ShiftRepository.activeWorkProfileId}
            ShiftRepository.replaceShift(day,definitions[0].id)
            val base=ShiftRepository.getShiftsForDate(day).single()
            ShiftRepository.setShiftExtraDetails(day,base.id,"17:10",.5f,"Base note",true)
            ShiftRepository.addSecondaryShift(day,definitions[1].id)
            ShiftRepository.setDayOnCallManual(day,true)
            val before=ShiftRepository.getShiftsForDate(day).toList()
            val target=before.last().id
            ShiftRepository.removeAssignedShift(day,target,clearOnCall=true)
            val remaining=ShiftRepository.getShiftsForDate(day).single()
            assertEquals(before.first(),remaining)
            assertFalse(ShiftRepository.isOnCall(day))
            assertTrue(ShiftRepository.undo())
            assertEquals(before,ShiftRepository.getShiftsForDate(day).toList())
            assertTrue(ShiftRepository.isOnCall(day))
        }finally{DatabaseRestore.apply(original)}
    }

    @Test fun periodStartStillDoesNotProjectAssignmentsAcrossTheGrid() {
        for(offset in -42L..42L)assertFalse(calendarPeriodContains(day,null,day.plusDays(offset)))
    }
}
