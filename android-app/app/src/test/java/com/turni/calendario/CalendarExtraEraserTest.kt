package com.turni.calendario

import com.turni.calendario.model.ReperibilitaConfig
import com.turni.calendario.model.ReperibilitaMode
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DatabaseRestore
import com.turni.calendario.ui.screens.calendarExtraEraserTargets
import com.turni.calendario.ui.screens.calendarPeriodContains
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarExtraEraserTest {
    private val day=LocalDate.of(2026,11,3)

    @Test fun emptyAndBaseOnlyDaysOfferNothingToTheExtraEraser() {
        assertTrue(calendarExtraEraserTargets(emptyList()).isEmpty())
        assertTrue(calendarExtraEraserTargets(listOf("base")).isEmpty())
    }

    @Test fun twoAssignmentsIdentifyTheOnlyExtraForImmediateRemoval() {
        assertEquals(listOf("extra"),calendarExtraEraserTargets(listOf("base","extra")))
    }

    @Test fun threeOrMoreAssignmentsOfferOnlyExtrasInTheirOriginalOrder() {
        assertEquals(listOf("second","third","fourth"),
            calendarExtraEraserTargets(listOf("base","second","third","fourth")))
    }

    @Test fun removingTheOnlyExtraPreservesBaseDetailsAndDayOnCallAndCanBeUndone() {
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
            val target=calendarExtraEraserTargets(before.map{it.id}).single()
            ShiftRepository.removeAssignedShift(day,target)
            val remaining=ShiftRepository.getShiftsForDate(day).single()
            assertEquals(before.first(),remaining)
            assertTrue(ShiftRepository.isOnCall(day))
            assertTrue(ShiftRepository.undo())
            assertEquals(before,ShiftRepository.getShiftsForDate(day).toList())
            assertTrue(ShiftRepository.isOnCall(day))
        }finally{DatabaseRestore.apply(original)}
    }

    @Test fun periodStartStillDoesNotProjectAssignmentsAcrossTheGrid() {
        for(offset in -42L..42L)assertFalse(calendarPeriodContains(day,null,day.plusDays(offset)))
    }
}
