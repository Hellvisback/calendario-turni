package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DatabaseRestore
import com.turni.calendario.sync.GoogleDriveBackupHelper
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CompleteBackupRoundTripTest {
    private val day=LocalDate.of(2026,12,25)
    private fun complete():DatabaseSnapshot {
        val work=WorkProfile("job-a","Lavoro A","Reparto", "CUSTOM","Contratto",32,12,31,51,66,26,41,
            0xFF84C9B5,"team",true,true,"Documento.pdf","content://example/document")
        val other=work.copy(id="job-b",name="Lavoro B",isDefault=false,iconKey="work",ccnlDocumentName=null,ccnlDocumentUri=null)
        val night=ShiftDefinition("night","Turno originale","NOT","22:00","06:00",false,20,"02:00",0xFFB8A3F2,"moon",ShiftCategory.LAVORO,work.id,12.5,3.75)
        val changed=night.copy(id="changed",name="Turno cambiato",shortCode="CAM",startTime="14:00",endTime="22:00",colorHex=0xFFEF9DA0)
        val rest=night.copy(id="rest",name="Riposo personalizzato",shortCode="RIP",isAllDay=true,breakMinutes=0,breakStartTime=null,category=ShiftCategory.RIPOSO)
        val second=night.copy(id="second-job-model",workProfileId=other.id)
        val assignment=AssignedShift("shift-1",day,changed.id,night.id,true,true,"15:00","23:00","00:30",
            "Nota del turno è leggibile\nAltra riga",listOf("tag","secondo"),true,1.5f,true,true,false)
        return DatabaseSnapshot.capture().copy(
            definitions=listOf(night,changed,rest,second),profiles=listOf(work,other),
            rotations=listOf(ShiftRotation("cycle-a","Ciclo A",3,listOf(night.id,rest.id,""),"Descrizione A"),
                ShiftRotation("cycle-b","Ciclo B",2,listOf(changed.id,night.id),"Descrizione B")),
            shifts=mapOf(day to listOf(assignment,AssignedShift("shift-2",day,rest.id,isOnCallManual=false,hideExShift=true)),
                day.plusDays(1) to listOf(AssignedShift("shift-3",day.plusDays(1),second.id,isOnCallManual=null))),
            extras=mapOf(day to DayExtraInfo(day,1.5f,true,false,true,true,"Nota del giorno","00:30")),
            activeWorkProfileId=other.id,activeRotationId="cycle-b",defaultRotationId="cycle-a",
            preferences=AppPreferences(false,false,false,false,false,true,setOf(changed.id)),
            onCall=ReperibilitaConfig(ReperibilitaMode.ALTERNATA_STAGIONALE,listOf(changed.id),
                SeasonalReperibilitaConfig(11,2,4,29,listOf(night.id),4,2,day),setOf(day,day.plusDays(7))),
            alarms=SmartAlarmsConfig(false,listOf(ShiftAlarmRule(night.id,false,135,"20:15")),setOf("disabled-alarm")),
            holidayIds=setOf("natale","capodanno"),
            statisticsRules=listOf(StatisticsRule("rule",changed.id,setOf(night.id,rest.id),false))
        )
    }

    @Test fun everyBusinessFieldSurvivesBackupAndRestoreTogether() {
        val expected=complete()
        val restored=DatabaseRestore.parse(GoogleDriveBackupHelper.exportDatabaseToJson(expected),DatabaseSnapshot.capture())
        assertEquals(expected,restored)
        assertEquals("night",restored.shifts.getValue(day).first().originalShiftDefId)
        assertTrue(restored.shifts.getValue(day).first().isFromRotation)
        assertTrue(restored.shifts.getValue(day).first().isModifiedFromRotation)
        assertEquals(setOf(true,false,null),restored.shifts.values.flatten().map{it.isOnCallManual}.toSet())
    }

    @Test fun restoredRotationOriginStillDrivesNormalReplaceAndEraserBehavior() {
        val before=DatabaseSnapshot.capture()
        try{
            val expected=complete().copy(activeWorkProfileId="job-a")
            DatabaseRestore.apply(DatabaseRestore.parse(GoogleDriveBackupHelper.exportDatabaseToJson(expected),DatabaseSnapshot.capture()))
            ShiftRepository.replaceShift(day,"rest")
            assertEquals("night",ShiftRepository.calendarShifts.getValue(day).first().originalShiftDefId)
            ShiftRepository.clearWorkDayShifts(day)
            ShiftRepository.replaceShift(day,"changed")
            assertNull(ShiftRepository.calendarShifts.getValue(day).first().originalShiftDefId)
        }finally{DatabaseRestore.apply(before)}
    }
}
