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
            val scheduledStart = shift.date.atTime(LocalTime.parse(shift.customStartTime ?: def.startTime))
            val scheduledEndTime = LocalTime.parse(shift.customEndTime ?: def.endTime)
            var scheduledEnd = shift.date.atTime(scheduledEndTime)
            if (!scheduledEnd.isAfter(scheduledStart)) scheduledEnd = scheduledEnd.plusDays(1)

            val hasSplitOvertime = shift.startOvertimeHours > 0f || shift.endOvertimeHours > 0f
            val hasSplitPermission = shift.startPermissionHours > 0f || shift.endPermissionHours > 0f
            val startOvertime = if (hasSplitOvertime) shift.startOvertimeHours else 0f
            val startPermission = if (hasSplitPermission) shift.startPermissionHours else 0f
            val endOvertime = if (hasSplitOvertime) shift.endOvertimeHours else shift.overtimeHours
            val endPermission = if (hasSplitPermission) shift.endPermissionHours else shift.permissionHours

            var start = scheduledStart
            if (shift.actualStartTime != null) {
                val actualStart = LocalTime.parse(shift.actualStartTime)
                val sameDay = shift.date.atTime(actualStart)
                start = when {
                    startOvertime.isFinite() && startOvertime > 0f -> {
                        if (sameDay.isAfter(scheduledStart)) sameDay.minusDays(1) else sameDay
                    }
                    startPermission.isFinite() && startPermission > 0f -> {
                        if (sameDay.isBefore(scheduledStart)) sameDay.plusDays(1) else sameDay
                    }
                    else -> {
                        val prevDay = sameDay.minusDays(1)
                        val nextDay = sameDay.plusDays(1)
                        listOf(prevDay, sameDay, nextDay).minByOrNull {
                            kotlin.math.abs(Duration.between(scheduledStart, it).toMinutes())
                        } ?: sameDay
                    }
                }
            } else if (startOvertime.isFinite() && startOvertime > 0f) {
                start = scheduledStart.minusMinutes((startOvertime * 60).toLong())
            } else if (startPermission.isFinite() && startPermission > 0f) {
                val permMinutes = (startPermission * 60).toLong().coerceAtLeast(0L)
                val maxMinutes = Duration.between(scheduledStart, scheduledEnd).toMinutes().coerceAtLeast(0L)
                val candidate = scheduledStart.plusMinutes(minOf(permMinutes, maxMinutes))
                start = if (candidate.isBefore(scheduledEnd)) candidate else scheduledEnd
            }

            var end = scheduledEnd
            if (shift.actualEndTime != null) {
                val actual = LocalTime.parse(shift.actualEndTime)
                end = shift.date.atTime(actual)
                if (!end.isAfter(start)) end = end.plusDays(1)
            } else if (endOvertime.isFinite() && endOvertime > 0f) {
                end = scheduledEnd.plusMinutes((endOvertime * 60).toLong())
            }
            if (shift.actualEndTime == null && endPermission.isFinite() && endPermission > 0f) {
                val permMinutes = (endPermission * 60).toLong().coerceAtLeast(0L)
                val maxMinutes = Duration.between(start, end).toMinutes().coerceAtLeast(0L)
                val candidate = end.minusMinutes(minOf(permMinutes, maxMinutes))
                end = if (candidate.isAfter(start)) candidate else start
            }
            Interval(shift, def, start, end)
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

data class ConsecutiveShiftWarning(
    val date: LocalDate,
    val consecutiveDays: Int,
    val maxAllowed: Int,
    val workProfileId: String,
) {
    val description: String
        get() = "$consecutiveDays turni lavorativi di fila · limite $maxAllowed"
}

/** Counts consecutive working days per profile, resetting on non-working days or calendar gaps. */
fun consecutiveShiftWarnings(
    assignments: List<AssignedShift>,
    definitions: List<ShiftDefinition>,
    profiles: List<WorkProfile>,
): List<ConsecutiveShiftWarning> {
    val defs = definitions.associateBy { it.id }
    val byProfile = mutableMapOf<String, MutableMap<LocalDate, MutableList<ShiftDefinition>>>()
    for (shift in assignments) {
        val def = defs[shift.shiftDefId] ?: continue
        byProfile
            .getOrPut(def.workProfileId) { java.util.TreeMap() }
            .getOrPut(shift.date) { mutableListOf() }
            .add(def)
    }
    val warnings = mutableListOf<ConsecutiveShiftWarning>()
    for (profile in profiles) {
        if (profile.maxConsecutiveWorkDays <= 0) continue
        val byDate = byProfile[profile.id] ?: continue
        var streak = 0
        var prevDate: LocalDate? = null
        for ((date, dayDefs) in byDate) {
            if (prevDate != null && prevDate.plusDays(1) != date) {
                streak = 0
            }
            if (dayDefs.any { it.category == ShiftCategory.LAVORO }) {
                streak += 1
                if (streak > profile.maxConsecutiveWorkDays) {
                    warnings.add(
                        ConsecutiveShiftWarning(
                            date = date,
                            consecutiveDays = streak,
                            maxAllowed = profile.maxConsecutiveWorkDays,
                            workProfileId = profile.id,
                        )
                    )
                }
            } else {
                streak = 0
            }
            prevDate = date
        }
    }
    return warnings
}
