package com.turni.calendario

import androidx.compose.runtime.mutableStateListOf
import com.turni.calendario.export.PdfGenerator
import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DatabaseRestore
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PdfCaptureTest {
    private val date=LocalDate.of(2026,10,7)
    private val options=PdfExportOptions(startDate=date,endDate=date)
    private fun isolated(block:()->Unit){
        val definitions=ShiftRepository.shiftDefinitions.toList()
        val profiles=ShiftRepository.workProfiles.toList()
        val shifts=ShiftRepository.calendarShifts.mapValues{it.value.toList()}
        val extras=ShiftRepository.dayExtraInfo.toMap()
        try{ShiftRepository.calendarShifts.clear();ShiftRepository.dayExtraInfo.clear();block()}
        finally{
            ShiftRepository.shiftDefinitions.clear();ShiftRepository.shiftDefinitions.addAll(definitions)
            ShiftRepository.workProfiles.clear();ShiftRepository.workProfiles.addAll(profiles)
            ShiftRepository.calendarShifts.clear();shifts.forEach{(date,list)->ShiftRepository.calendarShifts[date]=mutableStateListOf<AssignedShift>().apply{addAll(list)}}
            ShiftRepository.dayExtraInfo.clear();ShiftRepository.dayExtraInfo.putAll(extras)
        }
    }
    @Test fun capturesAllAssignmentsOfTheSelectedWorkOnly()=isolated {
        val active=ShiftRepository.activeWorkProfile
        val defs=ShiftRepository.shiftDefinitions.filter{it.workProfileId==active.id}
        val other=active.copy(id="pdf-test-other",name="Other work")
        ShiftRepository.workProfiles.add(other)
        val foreign=defs.first().copy(id="pdf-test-foreign",workProfileId=other.id)
        ShiftRepository.shiftDefinitions.add(foreign)
        ShiftRepository.calendarShifts[date]=mutableStateListOf(AssignedShift("foreign",date,foreign.id),AssignedShift("base",date,defs[0].id),AssignedShift("extra",date,defs[1].id))
        val exported=PdfGenerator.capture(options)
        assertEquals(active.name,exported.work)
        assertEquals(listOf(defs[0].shortCode,defs[1].shortCode),exported.days.single().shifts.map{it.code})
    }
    @Test fun customNightHoursAndPerAssignmentDetailsAreExported()=isolated {
        val def=ShiftRepository.shiftDefinitions.first{!it.isAllDay}.copy(id="pdf-test-custom",breakMinutes=30)
        ShiftRepository.shiftDefinitions.add(def)
        ShiftRepository.calendarShifts[date]=mutableStateListOf(AssignedShift("custom",date,def.id,customStartTime="21:00",customEndTime="06:00",actualEndTime="06:30",overtimeHours=.5f,isTrasferta=true,notes="Assignment note"))
        val row=PdfGenerator.capture(options.copy(includeDuration=true,includeBreak=true)).days.single().shifts.single()
        assertEquals("21:00",row.time)
        assertTrue(row.details.contains("21:00 - 06:00"))
        assertTrue(row.details.contains("Durata prevista: 8 h 30 min"))
        assertTrue(row.details.contains("Straordinario: 0 h 30 min"))
        assertTrue(row.details.contains("Trasferta"))
        assertTrue(row.details.contains("Assignment note"))
    }
    @Test fun disabledContentDoesNotLeakIntoExportedDetails()=isolated {
        val def=ShiftRepository.shiftDefinitions.first{!it.isAllDay}
        ShiftRepository.calendarShifts[date]=mutableStateListOf(AssignedShift("hidden",date,def.id,notes="Private note",tags=listOf("Private tag"),isTrasferta=true))
        val row=PdfGenerator.capture(options.copy(includeTitle=false,includeTime=false,includeNotes=false,includeLocation=false)).days.single().shifts.single()
        assertEquals("",row.name)
        assertNull(row.time)
        assertTrue(row.details.isEmpty())
    }
}
