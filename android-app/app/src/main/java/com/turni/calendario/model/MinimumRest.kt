package com.turni.calendario.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class MinimumRestWarning(val previousId:String,val nextId:String,val date:LocalDate,val restMinutes:Long,val requiredHours:Int) {
    val description:String get() = if(restMinutes<0) "Turni sovrapposti · riposo minimo ${requiredHours} h" else
        "Riposo ${restMinutes/60} h ${restMinutes%60} min · minimo ${requiredHours} h"
}

/** Chronological working intervals, independent of labels and insertion order. */
fun minimumRestWarnings(assignments:List<AssignedShift>,definitions:List<ShiftDefinition>,profiles:List<WorkProfile>):List<MinimumRestWarning> {
    data class Interval(val shift:AssignedShift,val definition:ShiftDefinition,val start:LocalDateTime,val end:LocalDateTime)
    val defs=definitions.associateBy{it.id};val work=profiles.associateBy{it.id}
    val intervals=assignments.mapNotNull { shift->
        val def=defs[shift.shiftDefId]?:return@mapNotNull null
        if(def.isAllDay||def.category!=ShiftCategory.LAVORO)return@mapNotNull null
        try {
            val start=shift.date.atTime(LocalTime.parse(shift.customStartTime?:def.startTime))
            val scheduled=LocalTime.parse(shift.customEndTime?:def.endTime)
            var end=shift.date.atTime(scheduled)
            if(!end.isAfter(start))end=end.plusDays(1)
            if(shift.actualEndTime!=null){
                val actual=LocalTime.parse(shift.actualEndTime)
                end=shift.date.atTime(actual)
                if(!end.isAfter(start))end=end.plusDays(1)
            }else if(shift.overtimeHours>0)end=end.plusMinutes((shift.overtimeHours*60).toLong())
            Interval(shift,def,start,end)
        }catch(_:Exception){null}
    }.sortedWith(compareBy<Interval>{it.start}.thenBy{it.shift.id})
    val warnings=mutableListOf<MinimumRestWarning>()
    var latest:Interval?=null
    intervals.forEach { current->
        latest?.let{previous->
            val required=maxOf(work[previous.definition.workProfileId]?.minRestHoursBetweenShifts?:0,work[current.definition.workProfileId]?.minRestHoursBetweenShifts?:0).coerceAtLeast(0)
            val gap=Duration.between(previous.end,current.start).toMinutes()
            if(required>0&&gap<required*60L)warnings.add(MinimumRestWarning(previous.shift.id,current.shift.id,current.shift.date,gap,required))
        }
        // A nested overlapping shift must not hide the end of a longer interval.
        if(latest==null||current.end.isAfter(latest!!.end))latest=current
    }
    return warnings
}
